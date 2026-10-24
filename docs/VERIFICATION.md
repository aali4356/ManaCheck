# Local verification receipt

## Delegated review and validator rewrite — 24 October 2026 UTC

- **208 application checks passed**, zero failures/errors/skips, plus 11 stability-detector checks.
- The rewritten validator has **54 focused invocations**. The classifier has 58 and provider adapter 24; the complete verifier includes PostgreSQL, 20 recommendation scenarios, API/Cucumber and all three Chrome journeys.
- [Hosted verification](https://github.com/aali4356/ManaCheck/actions/runs/40058891991) passed at source `c5200d03d03b09451e2f8f824992e181ab03d7db`.
- [The source read-through](CODE-REVIEW.md) covers all 29 Java source files, migrations, ranking SQL and browser request paths. [Authorship](OWNERSHIP.md) is explicit.
- [The completed assistant review](HAIKU-REVIEW.md) preserves both fresh Haiku batches and its failing content verdict. Structural success is separate from human ratings and factual quality.
- The catalog refresh preserved exact fingerprints of 10 decks, 20 revisions and 876 entries; 34,473 cards remain active. No personal deck was sent to a provider.

The [closeout receipt](evidence/delegated-closeout.json) pins source hashes, tests, failed diagnostics, runtime activation and actual execution settings. Earlier receipts below describe their own source revisions and inventories.

## Role tags and explanation follow-up — October 23, 2026

The [current receipt](evidence/role-tag-verification.json) separates tested source
revisions and preserves the earlier receipts below.

| Check | Result |
| --- | --- |
| Full verifier | 130 passing checks, zero failures/errors/skips; role regressions add 32 |
| Public CI | [Role-tag PR 7](https://github.com/aali4356/ManaCheck/pull/7) passed [Actions](https://github.com/aali4356/ManaCheck/actions/runs/40053866047) |
| Ten-run samples | Ten local and [ten hosted runs](https://github.com/aali4356/ManaCheck/actions/runs/40054469840), each with 130 passing keys; zero changed/nonpassing outcomes |
| Artifact verification | Download digest matched; an independent XML recount found all 10 × 130 passing cases |
| Jenkins | Actual local build 6 checked the updated explanation source: 130 app checks and 11 detector checks passed |
| Personal library | Cached-source catalog refresh retained 34,473 cards and exact fingerprints of 10 decks, 20 revisions and 876 entries |
| Live Haiku | Two full-catalog fixture batches, ten runs and 50 explanations each; retained results passed; [human review and wording flags remain open](HAIKU-REVIEW.md) |

The hosted ten-run sample is pinned to the role-tag merge before the subsequent
explanation prompt edit. Jenkins and the full local verifier also checked that
prompt edit. These are bounded samples, with no guarantee of future stability.

## Published verification — October 23, 2026

The [public repository](https://github.com/aali4356/ManaCheck) records the existing AI-assisted implementation with its actual commit dates. The employer-specific README section is now **What this project tests**.

| Check | Evidence |
| --- | --- |
| Initial publication | [69 checks passed in Actions](https://github.com/aali4356/ManaCheck/actions/runs/40030099677) |
| Cucumber business rules | [PR 1](https://github.com/aali4356/ManaCheck/pull/1), green hosted checks; 21 scenarios plus 69 existing tests |
| REST Assured and JSON schemas | [PR 2](https://github.com/aali4356/ManaCheck/pull/2), green hosted checks; 98 local checks with no failures, errors, or skips |
| Jenkins | [PR 4](https://github.com/aali4356/ManaCheck/pull/4), actual local Linux builds: 98 app checks plus 11 detector checks passed; [receipt](evidence/jenkins-verification.json) |
| Nightly stability | [PR 5](https://github.com/aali4356/ManaCheck/pull/5), 11 detector regressions; ten local and [ten hosted runs](https://github.com/aali4356/ManaCheck/actions/runs/40049439290), each with 98 passing keys and zero changed outcomes; [local receipt](evidence/stability-verification.json), [hosted receipt](evidence/stability-hosted.json) |
| Live explanations | Ten fixture runs, 50 explanations; [review findings and limitations](HAIKU-REVIEW.md) |
| Owner's learning | [Class reading and unaided rewrite](OWNERSHIP.md) remain unverified |

The receipts below retain the original October 22 results. Later integration receipts are [Cucumber](evidence/cucumber-integration.json) and [REST Assured](evidence/rest-assured-integration.json). Passing checks do not establish human code ownership or strategic recommendation quality.

## Original implementation receipt

Verified on October 22, 2026. The machine-readable receipt is [verification.json](evidence/verification.json); it includes the application/source hashes and test-suite results.

The later sunset UI redesign passed **68 checks**, including a third Chrome journey covering deck search and sorting. See [UI-REDESIGN.md](UI-REDESIGN.md) and its separate receipt for the current interface. The 67-check receipt and performance measurements below remain the original implementation evidence.

The subsequent real-deck import exposed a printing-versus-Oracle-identity catalog gap. Classifier v3 corrected it and passed **69 checks**. Replaying the same source snapshot recovered 1,062 paper identities, bringing the catalog to **34,473** cards. See [CATALOG-COVERAGE.md](CATALOG-COVERAGE.md) for the regression, source provenance, and latest verification receipt.

## Acceptance evidence

| Check | Result |
| --- | --- |
| Clean build and verification | `DEBUG=false ./scripts/verify.sh`: BUILD SUCCESS, 67 tests, zero failures/errors/skips; 19.944 seconds |
| Rule checks | 18 Java cases, including boundaries, copy exceptions, multifaced identity, Vehicles/Spacecraft, and Grist |
| Recommendations | 25 checks, including the 20 pinned commander/theme/count scenarios |
| Database, connector, and API | 13 workflow checks plus populated-schema migration verification |
| Provider failure handling | Eight local HTTP-stub cases; live inference not used |
| Desktop and mobile | Two actual Chrome journeys, including import/save/suggest/fallback/apply/compare |
| Fresh startup | Disposable empty database seeded 115 gameplay cards and two decks, using the offline fixture; database removed after the check |
| Live source import | 33,411 gameplay records promoted; 5,536 excluded records reported; existing decks/history preserved |
| SQL plan and local budget | Same saved Meren revision, twenty subsequent requests: p50 59.315 ms, p95 69.055 ms; 200 ms local p95 budget passed |
| API example | `scripts/demo-api.py` created a separate walkthrough deck, applied a checked addition, and read its revision difference |
| Demo recording | 300-second silent browser capture, VP9, 1440 × 937; actual application workflow with captions |
| Static checks | Java compilation/formatting, Prettier check, JavaScript syntax, shell syntax, and Python compilation |

The recording demonstrates the Meren workflow on the full source snapshot. The final automated receipt also covers the subsequent query optimization, Grist override, and active-source provenance check. Historical card quantities remain immutable; present-day card details and legality use the current catalog.

See [fresh-startup.json](evidence/fresh-startup.json), [full-catalog-import.json](evidence/full-catalog-import.json), and the [performance investigation](PERFORMANCE.md). The startup demonstration and full-catalog measurements used separate databases from the disposable integration-test container.

## Development attempts and corrections

Seven Maven test invocations were used, including focused runs: three failed development runs and four passing runs. Compilation checks are separate from this count.

| Finding | Correction and evidence |
| --- | --- |
| `int cannot be converted to HttpStatusCode` | Supply Spring's status-code type when creating problem responses; compilation passes. |
| `Result value is null but no null value expected` | Handle the empty catalog pointer explicitly; fresh-database startup passes. |
| PostgreSQL could not infer a parameter type for `java.time.Instant` | Bind a UTC `OffsetDateTime`; connector and migration/database tests pass. |
| Fixture expectation was 115 imported cards; actual was 114 | Inspect the source: one selected Llanowar Elves record was a token. Keep it excluded and test the skipped count. Adding the later Grist fixture produces 116 source records and 115 imported cards. |
| Native clicks occurred while smooth scrolling moved targets | Scroll the target into view and wait for hit-test visibility before a native click; desktop/mobile journeys pass without sleeps or retries. |
| Correlated SQL repeated role aggregates | Retain the materialization change after an identical first-50 candidate/score comparison, a faster measured plan, and passing regressions. |
| Grist's printed type omitted its deck-construction creature ability | Add an Oracle-ID override supported by the official release notes; cover it with a regression test and version the classifier's import identity. |
| Active import could leave the twelve-row recent-history window | Read active provenance from the active import itself; test thirteen failed refreshes without losing source identity. |

## Runtime and limits

- One coding agent. Symphony worker attempts: zero. Its workflow requires an existing committed checkout, so this new repository was implemented directly.
- Requested model/effort selection could not be verified or changed through this session. Actual model, effort, and token usage remain **unknown**.
- JDK: Homebrew OpenJDK 21.0.12.1. Maven Wrapper: 3.3.4, Maven 3.10.0. Spring Boot: 4.1.1. PostgreSQL: 17.11. Selenium: 4.51.0. Chrome/ChromeDriver: 156.0.8078.47.
- The dedicated `manacheck` Colima profile uses two CPUs, 4 GiB memory, and a 20 GiB disk. The existing Docker context and shell defaults were preserved.
- Homebrew installed the JDK and its dependencies. Its fontconfig post-install step reported a warning and was not rerun; the Java build and Chrome checks passed.
- Selenium reports its optional CDP binding is one version behind Chrome. These journeys use standard WebDriver and passed; additional CDP features were not evaluated.
- GitHub Actions is configured locally. No hosted CI run or public deployment is claimed.
- Haiku's live output quality and human strategic review remain pending. No live provider inference was requested. The application demonstration runs with an empty API key and uses the documented fallback.

To restart the local app, use `./scripts/dev.sh`. To enable optional live explanations, export your API key and restart it. The application does not read `.env` automatically.
