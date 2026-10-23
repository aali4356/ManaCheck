# Ranking query investigation

The initial local measurement used the full 33,411-card gameplay catalog and the saved **The graveyard shift**, revision 2. The first measured HTTP request took 437.888 ms; twenty subsequent requests had p50 145.197 ms and p95 351.674 ms. This was a running JVM, with no provider call.

The inspected ad hoc SQL plan took **414.697 ms**, including **238.249 ms of JIT work**. It repeatedly recomputed the same deck role aggregate inside correlated candidate expressions; one card-tag scan ran over 140,000 times.

The change explicitly materializes the small `role_counts` CTE and the scored candidate `pool`. In a read-only experiment on the same database and deck, the first fifty candidate IDs, their ordering, and scores remained identical. Execution fell to **29.350 ms**, and the plan no longer triggered JIT. The fix changes query evaluation, while retaining filtering, scoring, and simulated construction validation. PostgreSQL documents how explicit materialization controls CTE reuse: [PostgreSQL 17 CTE materialization](https://www.postgresql.org/docs/17/queries-with.html#QUERIES-WITH-CTE-MATERIALIZATION).

| Attempt | Before | Controlled experiment | Decision |
| --- | ---: | ---: | --- |
| Materialize role totals and candidate pool | 414.697 ms SQL | 29.350 ms SQL; same first 50 IDs and scores | Keep, with construction and API/browser regression checks |

The initial evidence remains in [performance-before.json](evidence/performance-before.json) and [recommendation-plan-before.json](evidence/recommendation-plan-before.json). The controlled comparison is in [sql-optimization.json](evidence/sql-optimization.json). Final application timings and plan are in [performance.json](evidence/performance.json) and [recommendation-plan.json](evidence/recommendation-plan.json).

The final application measurement reused **The graveyard shift, revision 2**, on the same card snapshot: p50 **59.315 ms**, p95 **69.055 ms**, and ad hoc SQL execution **59.226 ms**. The final ad hoc plan reported **18.095 ms** of JIT work, with inlining and optimization disabled. This differs from the controlled experiment's plan; planner statistics and query preparation can affect that overhead. Database-wide JIT settings were preserved. The measured response distributions improved beyond their observed run-to-run variation.

To repeat the local budget check on that same deck:

```bash
python3 scripts/benchmark.py --deck-id '<deck UUID>' --max-p95-ms 200
```

The tool retains evidence before returning failure if the configured local p95 budget is exceeded. It uses one first measurement and twenty subsequent requests; it does not clear database caches or restart the JVM. Wall-clock timing is kept out of the small-fixture CI gate to avoid turning runner load into a false functional failure. Recheck this report after ranking changes, using the full pinned catalog and the same saved deck.

These are local samples on one machine and deck. They establish neither production throughput nor cold-start, concurrent-load, or live-model latency.
