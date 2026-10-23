# Verification strategy

```bash
./scripts/verify.sh
```

This runs Java unit tests, actual PostgreSQL 17 integration tests, the 20 pinned recommendation scenarios, and headless Chrome journeys; it packages the application and creates a JaCoCo report. Docker and Chrome must be available. A missing runtime fails the checks; integration tests do not silently switch to H2 or skip themselves.

For faster backend iteration, run `./mvnw test` after sourcing `scripts/environment.sh` when this Mac's toolchain needs discovery. For only the browser journeys: `./scripts/verify.sh -Dtest=BrowserTest`.

## Executable business acceptance scenarios

Cucumber JVM **8.0.4** runs through the JUnit Platform Suite engine on the Boot-managed JUnit 6 platform and Java 21. All new dependencies have test scope. `RunCucumberTest` selects only `features` and the Cucumber engine; it does not select Jupiter tests. `junit-platform.properties` disables Cucumber's direct root-engine discovery, so the suite is its sole entry point. Jupiter's existing tests keep their normal discovery. Both engines run sequentially because they share the test database.

Run just the business scenarios:

```bash
DEBUG=false bash -c 'source scripts/environment.sh; ./mvnw -B -Dtest=RunCucumberTest test'
```

Run all existing tests, including the three Chrome journeys, plus the business scenarios:

```bash
DEBUG=false bash -c 'source scripts/environment.sh; ./mvnw -B -Pbrowser test'
```

The existing suite has 69 test invocations with `-Pbrowser` (66 without it). The new feature files add 21 scenario invocations. A full successful run should report **90 tests**, with each existing test and each expanded scenario discovered once. The focused command should report **21 tests**. These counts describe local acceptance expectations; execution receipts belong in the task handoff.

| Feature | Business assertions |
| --- | --- |
| `features/deck-rules.feature` | 99/100/101 cards including commander; exact error codes, completion and recommendation eligibility; illegal color identity including a back face; ordinary singleton copies; repeated basic lands; commander singleton allowance; any-number Relentless Rats; nine versus ten Nazgûl |
| `features/revision-workflow.feature` | Original and populated snapshots retain membership, quantities, commander, theme, note and creation time; exact additions/removals/quantity deltas in both directions; unchanged cards omitted; commander differences; same-revision comparison; stale expected revision returns 409/`STALE_REVISION` without a partial save; PostgreSQL rejects revision update/delete and entry insert/update/delete |

`CucumberSpringConfiguration` inherits the existing Testcontainers PostgreSQL 17 configuration with demo seeding and provider credentials disabled. Each scenario gets fresh glue state. `CommanderSteps.resetScenario` is a Cucumber `@Before` hook: it first checks the actual JDBC URL against that container and verifies PostgreSQL major version 17, then explicitly calls the shared truncate-and-fixture-load method. Jupiter's `@BeforeEach` does not run for Cucumber. Every background asserts an empty deck/history/entry store and the reloaded fixture catalog; every scenario leaves a saved deck, so a missing reset fails subsequent scenarios. Construction scenarios save synthetic decks and inspect the real service analysis; persistence scenarios use actual migrations and constraints. No scenario uses Compose, personal decks, Scryfall, recommendations, or an AI provider.

Console and Surefire reports use the long naming strategy. Outline titles include their business inputs, giving every expanded scenario a unique stable name. Cucumber writes `target/cucumber/cucumber.json`; match feature URI, scenario name and step status for later stability checks, ignoring durations, generated IDs and timestamps. Surefire XML remains in `target/surefire-reports`. Each invocation overwrites the Cucumber JSON report; retain a copy outside the source tree before a later run if comparing receipts. Do not set `cucumber.features` globally or add a second runner, which can bypass suite selectors or duplicate execution.

For a restricted clone that needs its own writable Maven cache, prefix either command with:

```bash
MAVEN_USER_HOME="$PWD/.local/maven" MAVEN_OPTS="-Dmaven.repo.local=$PWD/.local/maven/repository"
```

These process-local settings put Maven Wrapper and dependency downloads in ignored clone-local storage. They change no global configuration. The first run needs public Maven artifacts and the PostgreSQL 17 image; browser tests also need an existing Chrome/ChromeDriver. `scripts/environment.sh` can discover the installed JDK and dedicated Colima socket and create the clone-local empty Docker auth config. It does not start Compose. Missing Java, Docker, dependencies, or Chrome is a failed prerequisite, never a skipped acceptance result. These scenarios establish automated local behavior; hosted CI, human learning and live AI review require separate evidence.

Suite discovery and naming follow the [Cucumber 8.0.4 JUnit Platform instructions](https://github.com/cucumber/cucumber-jvm/blob/v8.0.4/cucumber-junit-platform-engine/README.md); context integration follows the [Cucumber Spring instructions](https://github.com/cucumber/cucumber-jvm/blob/v8.0.4/cucumber-spring/README.md).

## What each layer establishes

| Layer | Evidence |
| --- | --- |
| Pure Java | Card count boundaries, copy limits, multifaced identity, intrinsic land types, commander eligibility, overlap, schema parsing |
| PostgreSQL | Constraints, snapshot sealing, transactions, conflicting saves, catalog staging/promotion, retired identities, migration from populated V1 |
| Recommendation cases | 20 fixed commander/theme/count combinations; suggestions satisfy construction checks and have deterministic ordering |
| HTTP | Real Spring server, search payloads, structured errors, local-origin write protection |
| Provider stub | Strict output IDs/fields, missing output, malformed response, refusal, timeout, missing credentials, 429, and no automatic retry |
| Browser | Create → preview import → save → suggest → fallback → apply → compare; mobile layout and failed-import behavior |

Tests use a separately owned disposable PostgreSQL container. They truncate only that test database between cases. The Compose demonstration database is separate. JUnit runs these database tests sequentially. A shared container remains alive for the cached Spring context's lifetime.

The pinned source sample is under `src/main/resources/demo`; its provenance manifest records the original export and checksums. Recommendation scenarios are in `src/test/resources/recommendation-scenarios.json`. They check legal additions and swaps across eight commanders and multiple themes. They do not establish that a suggested card is strategically optimal.

## Avoiding false failures

- Assertions depend on semantic results and pinned inputs, with stable SQL tie-breaking.
- Browser waits target application readiness and observable state changes. There are no fixed UI sleeps or test retries.
- Browser tests disable remote card images. No test calls Scryfall or an actual model provider.
- HTTP stubs control both provider payloads and timing. The timeout test checks one attempt.
- Testcontainers uses PostgreSQL's actual constraints and transactions.
- A checked-in additive migration is tested against populated prior data.

Reports: `target/surefire-reports`, `target/site/jacoco`, and `target/browser`. GitHub Actions uploads them even when a job fails. The workflow exists locally; a hosted run is separate evidence and requires publishing the repository.

## SQL measurement

With the local app running, load the full catalog, then run:

```bash
python3 scripts/benchmark.py
```

The script measures one first request and 20 subsequent requests, then executes `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` on the same ranking SQL using local `psql`. Results go to `docs/evidence/performance.json` and `recommendation-plan.json`. It creates recommendation audit rows but does not modify deck revisions or call a model. The labels distinguish warm local timing from a cold start or deployed load test.

## Live explanation review

Live model quality needs an API key and a human reviewer. Keep this separate from the passing offline suite. Review at least ten explanation runs across general, tokens, counters, and graveyard decks. Record model ID, prompt/rules version, token usage, and these checks for each run:

1. Every card fact and claimed interaction is supported by the supplied Oracle text.
2. The explanation describes the actual addition or swap.
3. It gives a useful reason and preserves uncertainty about strategic value.
4. It avoids invented statistics, unsupported optimality claims, and instructions taken from card text.

Any unsupported card fact fails that run. Revisit the prompt and rerun the affected cases. Live review is pending until results are recorded; schema compliance alone cannot pass it.
