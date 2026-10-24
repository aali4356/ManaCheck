#!/usr/bin/env python3
"""Run the fixed offline verifier sequentially and compare Surefire outcomes.

Only --count is configurable. Importable detector functions never execute Maven.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
COMMAND = ["./scripts/verify.sh"]
EVIDENCE = ("surefire-reports", "cucumber", "browser", "site/jacoco")
ENVIRONMENT = {"ANTHROPIC_API_KEY": "", "DEBUG": "false"}
TOTAL_SECONDS = 24 * 60
RUN_SECONDS = 180
# Reviewed full browser-profile inventory at this base; update on intentional discovery changes.
EXPECTED_TEST_COUNT = 98


def read_reports(folder):
    """Return unique (classname, name) outcomes plus explicit report defects."""
    outcomes = {}
    issues = []
    files = sorted(Path(folder).glob("TEST-*.xml"))
    if not files:
        issues.append("missing Surefire XML reports")
    for path in files:
        try:
            root = ET.parse(path).getroot()
            if root.tag not in ("testsuite", "testsuites"):
                raise ValueError("expected testsuite or testsuites")
            suites = list(root.iter("testsuite"))
            if not suites:
                raise ValueError("no test suites")
            for suite in suites:
                # Surefire emits flat suites; reject nested/ambiguous documents.
                if suite.findall("testsuite"):
                    raise ValueError("nested test suites are unsupported")
                cases = suite.findall("testcase")
                counts = Counter()
                for case in cases:
                    key = (case.get("classname", ""), case.get("name", ""))
                    if not all(part.strip() for part in key):
                        raise ValueError("testcase lacks classname/name")
                    markers = [child.tag for child in case
                               if child.tag in ("failure", "error", "skipped")]
                    if len(markers) > 1:
                        raise ValueError("testcase has conflicting outcome elements")
                    status = {"failure": "failed", "error": "error",
                              "skipped": "skipped"}.get(
                                  markers[0] if markers else "", "passed")
                    counts[status] += 1
                    if key in outcomes:
                        outcomes[key] = "duplicate"
                        issues.append(f"{path.name}: duplicate key {key!r}")
                    else:
                        outcomes[key] = status
                for attribute, expected in (
                    ("tests", len(cases)), ("failures", counts["failed"]),
                    ("errors", counts["error"]), ("skipped", counts["skipped"])
                ):
                    if int(suite.attrib[attribute]) != expected:
                        raise ValueError(f"incomplete {attribute} count")
                if not cases:
                    raise ValueError("empty test suite")
                if any(next(suite.iter(tag), None) is not None for tag in
                       ("rerunFailure", "rerunError", "flakyFailure", "flakyError")):
                    raise ValueError("retry evidence is forbidden")
        except (OSError, ET.ParseError, ValueError, KeyError) as exc:
            issues.append(f"{path.name}: {exc}")
    if not outcomes:
        issues.append("no test outcomes available")
    return outcomes, issues


def analyze_runs(run_dirs, expected_test_count=None):
    """Analyze every requested slot, including absent receipts and reports."""
    runs = []
    for index, folder in enumerate(map(Path, run_dirs), 1):
        outcomes, issues = read_reports(folder / "surefire-reports")
        if expected_test_count is not None and len(outcomes) != expected_test_count:
            issues.append(f"expected {expected_test_count} test keys, found {len(outcomes)}")
        try:
            receipt = json.loads((folder / "run.json").read_text())
            if not isinstance(receipt, dict):
                raise ValueError("receipt must be an object")
        except (OSError, ValueError) as exc:
            receipt = {}
            issues.append(f"unavailable execution receipt: {exc}")
        if receipt.get("state") != "completed":
            issues.append(f"execution {receipt.get('state', 'unavailable')}")
        if type(receipt.get("returncode")) is not int or receipt["returncode"] != 0:
            issues.append(f"verifier returncode {receipt.get('returncode', 'unavailable')}")
        runs.append({"run": index, "receipt": receipt, "issues": issues,
                     "outcomes": outcomes})
    keys = sorted(set().union(*(run["outcomes"] for run in runs)))
    tests = []
    for classname, name in keys:
        statuses = []
        for run in runs:
            default = "unavailable" if run["issues"] else "missing"
            statuses.append(run["outcomes"].get((classname, name), default))
        tests.append({"classname": classname, "name": name, "outcomes": statuses,
                      "changed": len(set(statuses)) > 1,
                      "all_passed": all(status == "passed" for status in statuses)})
    changed = sum(test["changed"] for test in tests)
    nonpassing = sum(not test["all_passed"] for test in tests)
    success = bool(runs and tests) and not (changed or nonpassing) and all(
        not run["issues"] for run in runs)
    # JSON uses explicit fields rather than encoded tuple keys.
    for run in runs:
        run["test_count"] = len(run.pop("outcomes"))
    return {"schema_version": 1, "sample": "nightly" if len(runs) == 10 else "diagnostic",
            "requested_runs": len(runs), "success": success,
            "changed_test_count": changed, "nonpassing_test_count": nonpassing,
            "runs": runs, "tests": tests}


def markdown(summary):
    lines = ["## Verification stability sample",
             f"**{'PASS' if summary['success'] else 'FAIL'}** — "
             f"{summary['requested_runs']} sequential runs ({summary['sample']}); "
             f"{len(summary['tests'])} test keys; "
             f"{summary['changed_test_count']} changed; "
             f"{summary['nonpassing_test_count']} nonpassing.",
             "", "Ten clean runs establish no observed instability in that sample; "
             "they provide no guarantee of flake-free tests.", "",
             "Source SHA: `" + summary.get("source_sha", "unavailable") + "`.",
             "Full outcomes, receipts, commands, logs and reports are in the artifact."]
    if any(run["issues"] for run in summary["runs"]):
        lines.append("")
    for run in summary["runs"]:
        if run["issues"]:
            lines.append(f"- Run {run['run']}: " + "; ".join(run["issues"]))
    flagged = [test for test in summary["tests"]
               if test["changed"] or not test["all_passed"]]
    if flagged:
        lines.extend(["", "| Test (classname, name) | Outcomes in run order |",
                      "| --- | --- |"])
        for test in flagged[:30]:
            label = f"{test['classname']} :: {test['name']}"
            label = label.replace("&", "&amp;").replace("<", "&lt;").replace(
                ">", "&gt;").replace("|", "&#124;").replace("\n", " ")
            lines.append(f"| {label} | {', '.join(test['outcomes'])} |")
        if len(flagged) > 30:
            lines.append(f"\n{len(flagged) - 30} additional keys appear in summary.json.")
    return "\n".join(lines) + "\n"


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def publish_summary(destination, run_dirs, source_sha):
    summary = analyze_runs(run_dirs, EXPECTED_TEST_COUNT)
    summary["source_sha"] = source_sha
    summary["expected_test_count"] = EXPECTED_TEST_COUNT
    write_json(destination / "summary.json", summary)
    (destination / "summary.md").write_text(markdown(summary))
    return summary


def execute_run(folder, timeout):
    """Execute one fixed command, with a bounded process group and no retries."""
    receipt = {"command": COMMAND, "command_shell": shlex.join(COMMAND),
               "source_sha": json.loads((folder / "run.json").read_text())["source_sha"],
               "cwd": str(ROOT), "environment_overrides": ENVIRONMENT,
               "started_utc": datetime.now(timezone.utc).isoformat(),
               "timeout_seconds": timeout, "state": "running", "returncode": None}
    write_json(folder / "run.json", receipt)
    process = None
    started = time.monotonic()
    try:
        # verify.sh checks Docker before clean: clear old evidence even on preflight failure.
        for relative in EVIDENCE:
            path = ROOT / "target" / relative
            if path.exists():
                shutil.rmtree(path)
        with (folder / "verify.log").open("wb") as log:
            process = subprocess.Popen(COMMAND, cwd=ROOT, env={**os.environ, **ENVIRONMENT},
                                       stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            receipt["returncode"] = process.wait(timeout=timeout)
            receipt["state"] = "completed"
    except subprocess.TimeoutExpired:
        receipt["state"] = "timeout"
    except KeyboardInterrupt:
        receipt["state"] = "interrupted"
        raise
    except OSError as exc:
        receipt["state"] = "unavailable"
        receipt["error"] = str(exc)
    finally:
        if process is not None and process.poll() is None:
            # Stop all Java/browser descendants before another run can start.
            os.killpg(process.pid, signal.SIGKILL)
            receipt["returncode"] = process.wait()
        receipt["elapsed_seconds"] = round(time.monotonic() - started, 3)
        receipt["finished_utc"] = datetime.now(timezone.utc).isoformat()
        try:
            for relative in EVIDENCE:
                source = ROOT / "target" / relative
                if source.exists():
                    shutil.copytree(source, folder / relative)
        except OSError as exc:
            receipt["state"] = "archive_failed"
            receipt["error"] = str(exc)
        write_json(folder / "run.json", receipt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, choices=range(1, 11), default=10,
                        help="1–9 diagnostic runs; default 10 for the nightly sample")
    args = parser.parse_args()
    # Actions cancellation normally sends SIGTERM; retain the current evidence too.
    def stop(signum, frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, stop)
    source_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT,
                                         text=True).strip()
    result_root = ROOT / "stability-results"
    result_root.mkdir(exist_ok=True)
    # Never overwrite or cherry-pick an earlier invocation.
    destination = result_root / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
                                 + f"-{os.getpid()}")
    destination.mkdir()
    run_dirs = [destination / f"run-{number:02d}" for number in range(1, args.count + 1)]
    write_json(destination / "invocation.json", {
        "source_sha": source_sha, "count": args.count, "command": COMMAND,
        "invocation": ["python3", "scripts/check_stability.py", "--count", str(args.count)],
        "dirty_worktree": bool(subprocess.check_output(
            ["git", "status", "--porcelain", "--untracked-files=normal"], cwd=ROOT)),
        "budget_seconds": TOTAL_SECONDS,
    })
    # Precreate every slot so interruption cannot hide unavailable executions.
    for folder in run_dirs:
        folder.mkdir()
        write_json(folder / "run.json", {"state": "not_started", "returncode": None,
                                         "command": COMMAND, "source_sha": source_sha})
    publish_summary(destination, run_dirs, source_sha)
    deadline = time.monotonic() + TOTAL_SECONDS
    interrupted = False
    try:
        for number, folder in enumerate(run_dirs, 1):
            remaining = int(deadline - time.monotonic())
            if remaining <= 0:
                break
            print(f"Run {number}/{args.count}: {shlex.join(COMMAND)} -> {folder}", flush=True)
            execute_run(folder, min(RUN_SECONDS, remaining))
            publish_summary(destination, run_dirs, source_sha)
    except KeyboardInterrupt:
        interrupted = True
    finally:
        summary = publish_summary(destination, run_dirs, source_sha)
        print(markdown(summary))
        print(f"Evidence: {destination}")
    return 130 if interrupted else (0 if summary["success"] else 1)


if __name__ == "__main__":
    raise SystemExit(main())
