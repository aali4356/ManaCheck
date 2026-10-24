# Know the code

The implementation is AI-assisted. These tasks belong to the owner; automation cannot establish that someone understands the code. Reading and the unaided rewrite remain unverified.

Read each class in this order. Check a box only after explaining its purpose and tracing a request through it without assistance.

- [ ] `Domain`: records, Oracle identity, quantities, and derived totals.
- [ ] `ManaCheckApplication`: Spring startup and component discovery.
- [ ] `CardClassifier`: required fields, commander eligibility, copy exceptions, and versioned text heuristics.
- [ ] `CatalogImporter`: streamed staging, duplicate rejection, atomic promotion, and failed-import preservation.
- [ ] `CatalogRepository`: bound SQL, array containment, current source provenance, and retired identities.
- [ ] `DeckValidator`: supported construction rules, errors versus heuristic warnings, and recommendation eligibility.
- [ ] `DeckService`: complete snapshots, row locks, expected revisions, previews, and SQL differences.
- [ ] `RecommendationService`: SQL candidates, coverage-aware cuts, simulation, stale results, and one-attempt explanations.
- [ ] `HaikuExplainer`: supplied facts, strict IDs, bounded requests, usage, and fallback.
- [ ] `ApiController`: HTTP contracts and status codes.
- [ ] `ApiException` and `ApiErrors`: domain errors, safe problem responses, and unexpected failures.
- [ ] `LocalOnlyGuard`: localhost/origin checks and response headers.
- [ ] `PageController`: server-rendered library, editor, and comparison.
- [ ] `DemoSeeder`: offline startup and recovery of interrupted work.

Then read every test class, including the Cucumber glue and REST Assured contracts, plus the migrations and `sql/recommendations.sql`. Trace the browser's save/apply requests from `app.js` to the transaction and back.

## Unaided exercise

Use a practice branch. Close the existing validator and its tests, then rewrite `DeckValidator` and `DeckValidatorTest` yourself from the product rules. Do not ask an AI to generate or repair either file during the exercise. Keep the public method signatures and response records compatible.

Cover boundaries, color identity and basic land types, singleton/copy exceptions, unsupported constructions, and the distinction between invalid drafts and eligible recommendations. Write your own assertions before checking the existing acceptance suite.

```bash
git switch -c practice/deck-validator
DEBUG=false ./scripts/verify.sh -Dtest=DeckValidatorTest
DEBUG=false ./scripts/verify.sh
```

Keep your diff and failed runs. After finishing, explain a failure you found and why the corrected assertion represents a business rule. A later code review can assess the result; the current project does not claim this exercise is complete.

## Panel rehearsal

Explain these without opening an assistant:

1. Why does saving lock both the catalog pointer and deck row? Where does the transaction begin when `create` calls `save` inside the same service?
2. How do PostgreSQL triggers seal a snapshot? What happens when a save fails halfway through?
3. Walk through role aggregation, candidate filtering, scoring, and deterministic ties in the ranking SQL. Why was a CTE materialized?
4. What stays immutable when Oracle text or legality changes? Why can an old deck revision display current card details?
5. What do the 20 recommendation scenarios establish, and which strategic claims remain outside them?
6. Which inputs are controlled to prevent flaky browser/provider tests? How would you investigate a failing prerequisite?
7. Why can a valid AI response still give poor advice? Use the observed `nontoken` tagging error as an example.

Record a five-minute walkthrough after completing the exercise. Keep learning completion separate from build, CI, and provider receipts.
