"""Synthetic XML detector regressions: no Maven, Docker or browser processes."""

import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from check_stability import analyze_runs, markdown


class StabilityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def run_fixture(self, number, cases=None, returncode=0, state="completed"):
        folder = self.root / str(number)
        reports = folder / "surefire-reports"
        reports.mkdir(parents=True)
        (folder / "run.json").write_text(json.dumps(
            {"state": state, "returncode": returncode}))
        if cases is not None:
            self.report(reports / "TEST-fixture.xml", cases)
        return folder

    def report(self, path, cases):
        root = ET.Element("testsuite", tests=str(len(cases)), failures="0",
                          errors="0", skipped="0")
        for classname, name, status in cases:
            case = ET.SubElement(root, "testcase", classname=classname, name=name)
            tag = {"failed": "failure", "error": "error", "skipped": "skipped"}.get(status)
            if tag:
                ET.SubElement(case, tag)
                attribute = {"failed": "failures", "error": "errors",
                             "skipped": "skipped"}[status]
                root.set(attribute, str(int(root.get(attribute)) + 1))
        ET.ElementTree(root).write(path)

    def test_changing_results_include_all_statuses_and_later_pass_does_not_erase(self):
        statuses = ["passed", "failed", "error", "skipped", "passed"]
        folders = [self.run_fixture(i, [("C", "test", status)])
                   for i, status in enumerate(statuses)]
        summary = analyze_runs(folders)
        self.assertFalse(summary["success"])
        self.assertEqual(summary["tests"][0]["outcomes"], statuses)
        self.assertEqual(summary["changed_test_count"], 1)
        self.assertIn("FAIL", markdown(summary))

    def test_stable_failure_is_failure_even_with_zero_exit_codes(self):
        folders = [self.run_fixture(i, [("C", "test", "failed")]) for i in range(10)]
        summary = analyze_runs(folders)
        self.assertFalse(summary["success"])
        self.assertEqual(summary["changed_test_count"], 0)
        self.assertEqual(summary["nonpassing_test_count"], 1)

    def test_missing_test_and_report_are_visible_in_run_order(self):
        folders = [self.run_fixture(1, [("C", "a", "passed"), ("C", "b", "passed")]),
                   self.run_fixture(2, [("C", "a", "passed")]), self.run_fixture(3)]
        summary = analyze_runs(folders)
        self.assertFalse(summary["success"])
        self.assertEqual(summary["tests"][1]["outcomes"],
                         ["passed", "missing", "unavailable"])
        self.assertTrue(summary["runs"][2]["issues"])

    def test_duplicate_key_in_one_or_multiple_reports_fails(self):
        for cross_file in (False, True):
            with self.subTest(cross_file=cross_file):
                folder = self.run_fixture(str(cross_file), [("C", "test", "passed")])
                report = folder / "surefire-reports" / (
                    "TEST-other.xml" if cross_file else "TEST-fixture.xml")
                cases = [("C", "test", "passed")] * (1 if cross_file else 2)
                self.report(report, cases)
                summary = analyze_runs([folder])
                self.assertFalse(summary["success"])
                self.assertEqual(summary["tests"][0]["outcomes"], ["duplicate"])
                self.assertTrue(any("duplicate key" in issue
                                    for issue in summary["runs"][0]["issues"]))

    def test_ten_stable_passing_runs(self):
        folders = [self.run_fixture(i, [("C", "test", "passed"),
                                        ("Other", "test", "passed")]) for i in range(10)]
        summary = analyze_runs(folders, expected_test_count=2)
        self.assertTrue(summary["success"])
        self.assertEqual(summary["sample"], "nightly")
        self.assertEqual(summary["changed_test_count"], 0)
        self.assertEqual(summary["nonpassing_test_count"], 0)
        self.assertIn("no guarantee", markdown(summary))

    def test_empty_malformed_and_incomplete_reports_fail(self):
        samples = ("", "<testsuite", '<testsuite tests="0" failures="0" errors="0" skipped="0"/>',
                   '<testsuite tests="2" failures="0" errors="0" skipped="0">'
                   '<testcase classname="C" name="test"/></testsuite>',
                   '<testsuite><testcase classname="C" name="test"/></testsuite>')
        for index, xml in enumerate(samples):
            with self.subTest(xml=xml):
                folder = self.run_fixture(index)
                (folder / "surefire-reports" / "TEST-invalid.xml").write_text(xml)
                summary = analyze_runs([folder])
                self.assertFalse(summary["success"])
                self.assertTrue(summary["runs"][0]["issues"])

    def test_runtime_failure_after_passing_tests_still_fails(self):
        for index, state in enumerate(("completed", "timeout", "interrupted", "not_started")):
            with self.subTest(state=state):
                folder = self.run_fixture(index, [("C", "test", "passed")],
                                          returncode=1, state=state)
                summary = analyze_runs([folder])
                self.assertFalse(summary["success"])
                self.assertTrue(summary["runs"][0]["issues"])

    def test_missing_execution_receipt_and_slot_are_unavailable(self):
        good = self.run_fixture(1, [("C", "test", "passed")])
        no_receipt = self.run_fixture(2)
        (no_receipt / "run.json").unlink()
        summary = analyze_runs([good, no_receipt, self.root / "absent"])
        self.assertFalse(summary["success"])
        self.assertEqual(summary["tests"][0]["outcomes"],
                         ["passed", "unavailable", "unavailable"])

    def test_stable_skip_error_and_empty_sample_fail(self):
        self.assertFalse(analyze_runs([])["success"])
        for status in ("skipped", "error"):
            folders = [self.run_fixture(f"{status}-{i}", [("C", "test", status)])
                       for i in range(2)]
            summary = analyze_runs(folders)
            self.assertFalse(summary["success"])
            self.assertEqual(summary["changed_test_count"], 0)
            self.assertEqual(summary["sample"], "diagnostic")

    def test_stably_absent_report_inventory_fails(self):
        folders = [self.run_fixture(i, [("C", "test", "passed")]) for i in range(10)]
        summary = analyze_runs(folders, expected_test_count=2)
        self.assertFalse(summary["success"])
        self.assertTrue(all(run["issues"] for run in summary["runs"]))

    def test_retry_markers_are_rejected(self):
        folder = self.run_fixture(1, [("C", "test", "passed")])
        path = folder / "surefire-reports" / "TEST-fixture.xml"
        root = ET.parse(path).getroot()
        ET.SubElement(root.find("testcase"), "flakyFailure")
        ET.ElementTree(root).write(path)
        self.assertFalse(analyze_runs([folder])["success"])


if __name__ == "__main__":
    unittest.main()
