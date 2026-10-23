# Oracle identity and printing availability

Importing real decklists exposed missing cards including Badlands, Plateau, Strip Mine, and Memnite. The downloaded Oracle export contained those identities, but selected digital printings. The old filter treated the selected printing's `games` field as the identity's paper availability and discarded them.

Classifier **catalog-v3** retains an identity when its representative printing includes paper, or Scryfall reports the identity as Commander-legal or banned. Token and non-gameplay layout exclusions still apply. A banned paper identity remains in the catalog so an invalid draft can preserve it; the construction validator rejects it for recommendations. Digital-only cards without paper Commander legality remain excluded.

The classifier version participates in the import hash. Replaying the validated, cached **2026-10-21** export therefore reprocesses unchanged source bytes with the corrected rules and promotes one complete snapshot. The active catalog increased from **33,411** to **34,473** identities; excluded records decreased from 5,536 to 4,474. No old deck membership or revision was rewritten. The earlier performance measurements retain their original catalog size and dates.

The fixed regression uses actual Scryfall records for:

- Badlands: preferred MTGO printing, Commander-legal paper identity; retained.
- Black Lotus: preferred MTGO printing, banned paper identity; retained with its banned status.
- Oracle of the Alpha: Arena-only identity without Commander legality; excluded.

The test covers publication, card counts, existing deck history, and an identical rerun. It failed against the old filter, then passed with the correction. The complete local suite passed **69 tests**, with zero failures, errors, or skips. Fixtures and their source/checksum manifest are in `src/test/resources/catalog-digital-preferred*`. [Machine-readable receipt](evidence/catalog-coverage.json).

This task used one agent and one implementation correction. Two Maven test invocations ran: the expected failing reproduction and the passing full suite. Symphony worker attempts: zero; the checkout remains uncommitted. Runtime model, effort, and measured token usage are unknown. Personal deck snapshots and import receipts remain local under ignored `.local/archidekt-imports/`.

Sources: [Oracle bulk metadata](https://api.scryfall.com/bulk-data/27bf3214-1271-490b-bdfe-c0be6c23d02e), [Badlands representative printing](https://api.scryfall.com/cards/73403d04-fe97-4830-8b80-16dd1a1a6cc1), [Badlands paper printing](https://api.scryfall.com/cards/377cee38-e769-47b0-abd5-0f528600f36a).
