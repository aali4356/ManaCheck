# Local REST interface

Base URL: `http://localhost:8080/api`. Requests and responses use JSON. UUIDs are Oracle card identities or application-generated entity IDs. The API is intended for the local workspace.

Run the executable example while the application is running:

```bash
python3 scripts/demo-api.py
```

It creates a separate deck, previews a list, saves it, inspects analysis, generates suggestions, applies one, and reads the difference. It makes no AI request.

## Cards and imports

- `GET /cards?q=Sol%20Ring&offset=0` returns `{ "cards": [...], "offset": 0 }`, up to 25 cards, with deterministic ordering.
- `GET /cards?q=Meren&commanders=true` searches eligible commander types. `commanderId=<oracle-id>` filters search by color identity.
- `GET /catalog/imports` returns active snapshot ID, `activeSource`, timestamp, active-card count, and recent import results. Active provenance remains available even when its import has aged out of the recent-history list.
- `POST /catalog/imports` starts an asynchronous Scryfall import and returns `202 { "id": "..." }`. One live import runs at a time.
- `GET /catalog/imports/{id}` returns `RUNNING`, `SUCCEEDED`, `UNCHANGED`, or `FAILED`, counts, timestamps, and a bounded error message. Failed imports preserve the active catalog. Old snapshots cannot replace newer snapshots.

The importer accepts the current gzipped JSONL export. It excludes non-paper records and non-gameplay layouts, rejects bad required fields and duplicate Oracle IDs, and ignores unknown optional fields. Refresh at most daily for routine use; all normal reads use PostgreSQL.

## Decks and revisions

`GET /decks` lists deck summaries. `GET /decks/{id}` returns the current revision. Create with:

```json
{
  "name": "The graveyard shift",
  "commanderOracleId": "<Oracle UUID from card search>",
  "theme": "graveyard"
}
```

`POST /decks` returns `201` and revision 1. Themes are `general`, `tokens`, `counters`, and `graveyard`.

`POST /decks/{id}/imports/preview` accepts:

```json
{
  "text": "18 Forest\n18 Swamp\n1 Sol Ring",
  "commanderOracleId": "<selected commander UUID>"
}
```

The preview returns resolved lines, aggregated `{oracleId, quantity}` entries, and errors. A selected commander appearing once in the text is recognized separately. Unknown names and bad quantities need correction. Previewing creates no revision.

`POST /decks/{id}/revisions` accepts a full replacement snapshot:

```json
{
  "expectedRevision": 1,
  "commanderOracleId": "<selected commander UUID>",
  "theme": "graveyard",
  "note": "More draw, fewer expensive spells",
  "entries": [{ "oracleId": "<card UUID>", "quantity": 18 }]
}
```

It returns `201`. Existing revisions remain unchanged. Known cards with construction violations can be saved as drafts; invalid identities, nonpositive quantities, and malformed input are rejected.

- `GET /decks/{id}/revisions` lists revision numbers, themes, notes, and timestamps.
- `GET /decks/{id}/revisions/{number}` retrieves a historical snapshot, with card details from the current catalog.
- `GET /decks/{id}/revisions/diff?from=1&to=2` returns name, Oracle ID, before/after quantity, and delta. Commander changes are included.
- `GET /decks/{id}/analysis` returns count, completeness, recommendation eligibility, errors, heuristic warnings, overlapping roles and targets, mana buckets, color counts, and catalog provenance.

Historical card membership and quantities are immutable. Analysis uses the current catalog; a source update can change present-day legality without rewriting a deck's history.

## Recommendations

`POST /decks/{id}/recommendations` returns `201` with an audited run containing its UUID, deck revision, catalog ID, up to five suggestions, rule explanations, and explanation status. A suggestion includes `id`, `add`, optional `cut`, and score. An empty list is a successful outcome when no useful candidate survives filtering.

`POST /recommendations/{runId}/explanations` attempts Haiku explanations once and returns the retained run. Status is `rules`, `pending`, `haiku`, or `fallback`. Each explanation must match the supplied suggestion IDs. Returned provider usage is retained even when output validation fails. Requests for outdated runs are rejected before spending on a model call.

`POST /recommendations/{runId}/apply` accepts:

```json
{ "suggestionId": "s1", "expectedRevision": 2 }
```

It returns `201` and a new deck revision. Each suggestion is an independent alternative. Applying one invalidates the other suggestions from that run. The server repeats the construction checks and rejects changed deck/catalog versions.

## Errors

API errors use a problem document with `status`, `detail`, and `code`. Main outcomes: `400` malformed request, `403` cross-origin write, `404` missing entity, `409` stale revision or conflicting change, and `422` invalid input or ineligible deck. Refresh the deck after a conflict and review before trying again. A raw SQL error or credential is never returned to the client.
