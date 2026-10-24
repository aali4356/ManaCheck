# Source read-through

Reviewed by Codex on 24 October 2026 UTC at the owner's request. This records an
assistant's source review. It makes no claim about the owner's unaided knowledge.
The final verification receipt records the exact reviewed file hashes.

## Application classes

All 15 application source files were read, including their nested records.

| File | Responsibility and boundary checked |
| --- | --- |
| `Domain` | HTTP/service records use Oracle identity; `DeckView.totalCards()` adds one commander. A recommendation retains full card facts. Records alone do not deeply freeze supplied collections. |
| `ManaCheckApplication` | The Spring entry point scans the `dev.manacheck` package. No catalog download or provider request starts here. |
| `CardClassifier` | Validates required source fields, excludes non-gameplay/digital-only records, combines face text, classifies commander/copy exceptions, and supplies versioned heuristic tags. These tags do not interpret arbitrary Oracle grammar. |
| `CatalogImporter` | Streams into per-import staging; duplicate identities fail its primary key. A transaction locks the catalog pointer before upsert, retirement and promotion. Failed staging/promotion preserves the prior active catalog. Hashes include classifier and role versions. |
| `CatalogRepository` | Binds search/identity parameters; fetches faces/tags; reads source provenance from the actual active import. Historical identities remain retrievable after retirement. `write()` changes several tables and requires a caller transaction for atomic production use. |
| `DeckValidator` | Separates construction findings, copy allowances, composition and eligibility. Commander counts toward size/copies; composition covers the main deck, matching SQL. Undersize alone permits recommendations. |
| `DeckService` | `create` and `save` publish complete snapshots transactionally. Saves share-lock the catalog, lock the deck, check expected revision, aggregate entries and advance the pointer last. Preview resolves names without saving. Differences include the commander. |
| `RecommendationService` | Bound SQL selects candidates; Java protects existing coverage and validates each simulated addition/swap. Applying checks both revision and catalog under locks. An atomic status update claims one explanation attempt; saved results are reused. |
| `HaikuExplainer` | Sends supplied facts and selected changes; bounds time/output; validates the returned structure and exact IDs. Refusal, bad fields, timeout and missing credentials preserve rule explanations and returned usage. Structural checks cannot establish strategic truth. |
| `ApiController` | Translates the documented cards/import/deck/history/analysis/recommendation endpoints; creation returns 201 and asynchronous catalog start returns 202. Selection and persistence stay in services. |
| `ApiException` | Carries domain status/code: invalid input 422, missing resource 404, conflict 409. |
| `ApiErrors` | Converts malformed requests to 400 problems, constraints to 409 and unexpected failures to a safe 500 response with local server diagnostics. |
| `LocalOnlyGuard` | Restricts Host and unsafe browser origins to IPv4 loopback/localhost; sets CSP, content-type and referrer headers. This local guard provides no multi-user authentication. |
| `PageController` | Supplies library/editor/comparison models to Thymeleaf; writes use the REST interface. |
| `DemoSeeder` | Marks interrupted imports/explanations explicitly, then seeds only an empty catalog when enabled. Personal runtime keeps demo seeding disabled. |

## Tests and supporting code

All 13 test source files were read:

| Files | What was checked |
| --- | --- |
| `DeckValidatorTest`, `CardClassifierTest` | Construction boundaries and independently asserted tag/rule examples; no live source/model dependency. |
| `Fixture`, `PostgresTestBase` | Pinned offline cards, separately owned PostgreSQL 17, explicit reset and disabled demo/provider settings. |
| `WorkflowIntegrationTest`, `MigrationIntegrationTest` | Atomic and conflicting saves, sealed history, provenance, connector failures/identity retention, and a populated V1 upgrade. |
| `RecommendationIntegrationTest` | Twenty synthetic scenarios, repeat ordering, construction-safe changes, stale rejection, empty candidates and retained fallback. |
| `HaikuExplainerTest` | Local HTTP stub, request facts/schema, exact response shape, one attempt, usage and fallback. |
| `ApiContractTest` | Real HTTP responses and closed JSON schemas; negative schema checks use actual responses. |
| `CommanderSteps`, `CucumberSpringConfiguration`, `RunCucumberTest` | Sole Cucumber suite entry point; scenario reset verifies the owned JDBC URL before truncation; expanded names stay unique. |
| `BrowserTest` | Three isolated Chrome journeys; readiness/hit-test waits, remote images disabled, no fixed UI sleeps or test retries. |

Also read `scripts/RecordDemo.java`, the three Flyway migrations, ranking SQL,
`app.js`, the business feature files, stability detector/tests, verifier,
Actions job and Jenkinsfile. The recorder creates a demonstration deck and waits
to capture frames; use a disposable demonstration database when recording again.
Its frame delays are outside the automated browser tests.

## Trace a save and an apply

`app.js` sends the current revision and complete entry list to `ApiController`.
The Spring proxy opens `DeckService.save`'s transaction. It obtains catalog/deck
locks, rejects a stale revision, writes the next revision and its entries, and
advances `deck.current_revision`. The response updates the editor and clears old
suggestions; analysis is fetched again.

An apply sends one suggestion ID and expected revision to
`RecommendationService.apply`. It locks the current catalog and deck, rejects
stale source/revision, simulates that single change, reruns validation, then calls
`DeckService.save`. That external bean call joins the same transaction. The old
revision remains intact.

## Interview explanations from this review

1. **Transactions:** externally invoked `create` starts a transaction. Its internal
   call to `save` bypasses a second Spring proxy, while the existing transaction
   still covers both methods. Catalog share locks block promotion; the deck row
   lock plus expected revision gives competing editors one winner.
2. **Snapshot sealing:** V1 rejects revision/entry update or delete. V3 rejects
   insertion into a published revision. Advancing the deck pointer seals entry
   membership. A service failure rolls back the revision, entries and pointer.
3. **Ranking:** joined tags aggregate quantities by role. Missing roles score six,
   a theme four, a qualifying curve benefit three, and mana value at most three
   two. Window ordering breaks ties by mana, lower-case name and Oracle ID. The
   materialized role CTE avoids repeated aggregation; its measured plan belongs
   to the source/catalog pinned in the performance receipt.
4. **History:** revision membership, quantities, commander identity, theme and
   note are immutable. Card facts and legality are current catalog data, so old
   revisions can display updated Oracle wording and retired cards. The saved
   catalog ID provides provenance, not a time-travel copy of every card fact.
5. **Twenty scenarios:** they establish construction-safe additions/swaps and
   deterministic results for those pinned fixtures. They cannot establish an
   upgrade's win rate, tribal fit, playability at a particular table or optimality.
6. **False failures:** fixed fixtures, owned databases, disabled live services,
   explicit browser readiness, exact XML inventory and no retries keep failures
   attributable. Inspect the first failing prerequisite and retained diagnostics
   before changing assertions or rerunning a failed release gate.
7. **AI limits:** a well-formed response can repeat a bad tag or merge two valid
   abilities into an invalid claim. Fix the tag/prompt, preserve failed outputs,
   and review a fresh live sample. Human learning remains a separate fact.

## Deliberate limits

One commander is represented. Paired commanders, companion designation, selectable
identity and unrecognized construction exceptions need manual review. Singleton
accounting uses Oracle IDs; this is not a general engine for interchangeable-name
families or every future deck-construction exception. Recommendation role/theme
scores remain coarse. The request's JSON schema and Java checks validate shape
and identity; they do not prove the prose's card facts or usefulness.

Rules checked against the [25 September 2026 comprehensive rules, section 903](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt).
