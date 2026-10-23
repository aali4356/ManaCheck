# Verification strategy

```bash
./scripts/verify.sh
```

This runs Java unit tests, actual PostgreSQL 17 integration tests, the 20 pinned recommendation scenarios, and headless Chrome journeys; it packages the application and creates a JaCoCo report. Docker and Chrome must be available. A missing runtime fails the checks; integration tests do not silently switch to H2 or skip themselves.

For faster backend iteration, run `./mvnw test` after sourcing `scripts/environment.sh` when this Mac's toolchain needs discovery. For only the browser journeys: `./scripts/verify.sh -Dtest=BrowserTest`.

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
