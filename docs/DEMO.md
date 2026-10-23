# Five-minute interview walkthrough

[Watch the recorded five-minute walkthrough](evidence/manacheck-demo.webm). The recording predates the sunset interface redesign; it shows the same create/import/save/suggest/fallback/apply/compare workflow. It is a silent capture of the running local browser at one frame per second, with explanatory captions. Current screenshots are linked in [UI-REDESIGN.md](UI-REDESIGN.md).

To record another copy with Chrome and FFmpeg installed, run `./scripts/record-demo.sh 300 .local/another-demo.webm` while the app is running. Use a local app started without an API key to demonstrate the offline fallback. The recorder creates a separate deck and preserves existing output files.

Start with `./scripts/dev.sh` and open http://localhost:8080. A fresh database needs no Scryfall access or API key. Use the bundled unfinished Meren deck, or create a separate demonstration deck.

| Time | Show | Explain |
| --- | --- | --- |
| 0:00–0:40 | Deck library and selected commander | The project turns data quality and business rules into a useful hobby application. Java owns the behavior; PostgreSQL owns persisted history and substantial analysis. |
| 0:40–1:30 | Paste `18 Forest`, `18 Swamp`, `1 Sol Ring`, `1 Cultivate`; preview and save | Name resolution, Oracle identity, preserved originals in history, atomic snapshots, and expected-revision checks. |
| 1:30–2:10 | Construction findings, role coverage, and mana curve | Distinguish strict construction rules from composition heuristics. Explain multifaced identity and copy-limit exceptions. |
| 2:10–3:00 | Generate and review improvements; apply one | Walk through filtering, the SQL CTE/window ranking, stable ties, simulated validation, and stale-run rejection. |
| 3:00–3:40 | Compare the old and new revisions | FULL OUTER JOIN exposes additions, removals, quantity changes, and commander changes. Old membership is sealed at the database layer. |
| 3:40–4:20 | Explain with AI, using the offline fallback when no key is configured | The model explains already selected changes. IDs and fields are checked. One attempt, bounded tokens/time, retained usage, and human review of claims. |
| 4:20–5:00 | Test report, one recommendation scenario, and SQL execution plan | Real PostgreSQL, deterministic fixtures, deliberate failure scenarios, browser waits, measured local timings, and boundaries of the evidence. |

Do not delay a job application until every optional feature has been demonstrated. Describe work in progress accurately. Before presenting, practice changing one Java validation rule and writing a role-count SQL query without AI assistance.

## Questions to rehearse

- Why identify cards with Oracle IDs rather than printing IDs or names?
- How does a failed import preserve the last good catalog?
- What prevents two tabs from overwriting one another?
- Why are legal recommendations different from strategically useful ones?
- What makes a provider test reliable when live model output varies?
- Which measurements came from the local machine, and which acceptance checks remain external?

Resume wording after verification: “Built a Java/Spring Boot Commander deck tracker with PostgreSQL revision history, SQL-ranked recommendations, transactional imports, and automated API/browser tests; added constrained AI explanations with validated output and failure handling.” Add counts and timings only from recorded evidence.
