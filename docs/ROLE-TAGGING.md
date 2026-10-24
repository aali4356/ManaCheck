# Role tags: roles-v2

Role tags guide SQL ranking. They describe likely functions and themes; they do
not establish that a card improves every deck.

| Card | Correction |
| --- | --- |
| Guardian Project | Keeps draw; `nontoken` no longer supplies the tokens theme |
| Sign in Blood | Recognizes “draws two cards” as draw |
| Life from the Loam | Keeps graveyard; dredge reminder text no longer supplies draw |

The draw rule ignores parenthesized reminder text, distinguishes draw conditions
from draw instructions within punctuation-separated clauses, and supports a
bounded quantity vocabulary. Conditional instructions such as “If you do, draw a
card” still count. Tokens require a standalone word match. Ramp, interaction,
counters, graveyard and construction rules retain their earlier behavior.

Limits remain: arbitrary Oracle grammar, negation and quoted abilities can fool
these heuristics. Keyword abilities whose draw appears only in reminder text,
such as cycling, can be missed. A token mention can describe an opponent's
tokens and does not establish strategic benefit. Cards can fill multiple roles.

Both classifier and role versions contribute to the import identity. Importing
the same source snapshot under `roles-v2` recomputes tags through the normal
staging/promotion transaction. `catalog-v3` retains its existing identity rules.
The local cached-source refresh retained 34,473 active cards and preserved exact
fingerprints for 10 decks, 20 revisions and 876 entries. A private database backup
was retained before the refresh.

`CardClassifierTest` adds 32 regression invocations. Run the full verifier with
`./scripts/verify.sh`; its expected inventory is 130, including browser checks.
Historical AI and stability receipts remain pinned to their original versions.
