# Role tags: roles-v4

Role tags guide SQL ranking. They describe likely functions and themes; they do
not establish that a card improves every deck. Main-deck quantities supply role
coverage; the commander counts toward construction totals and copy allowances.

| Card | Current behavior |
| --- | --- |
| Lazotep Quarry | Land, tokens and graveyard; exiling your own graveyard card for a copy supplies no opposing interaction |
| Staff of Compleation | Ramp, draw and counters; destroying a permanent you own supplies no opposing interaction |
| Ally Encampment | Land; returning your own Ally supplies no opposing interaction |
| Arcane Signet | Ramp; recognizes mana written as “add one mana” |
| Guardian Project | Draw and graveyard; `nontoken` supplies no tokens theme |
| Sign in Blood | Draw; recognizes “draws two cards” |
| Life from the Loam | Graveyard; dredge reminder text supplies no draw |

Interaction matching checks bounded effect clauses. A restriction to cards you
own/control or your graveyard removes that clause's interaction contribution;
a separate unrestricted or opposing removal/counter/bounce effect still counts.
Mana in symbols can supply nonland ramp. Number-word/number mana text counts when a direct tap/untap activation has no mana payment. Paying one mana to produce one, or granting a land a fixing ability, supplies no ramp through that template.
These rules cover the observed errors and their explicit regressions.

Draw ignores parenthesized reminder text, distinguishes draw conditions from
instructions within punctuation-separated clauses, and uses a bounded quantity
vocabulary. Conditional instructions such as “If you do, draw a card” still count.
Tokens require a standalone word match. Cards can fill multiple roles.

Limits remain: complex compound targets, negation, quoted abilities and arbitrary
Oracle grammar can fool these heuristics. Keyword draw appearing only in reminder
text, such as cycling, can be missed. A token mention can concern an opponent's
tokens. A mana ability with a cost does not guarantee a net increase in mana.
Theme matches do not establish strategic benefit.

Classifier and role versions both contribute to import identity. Reimporting the
cached source under `roles-v4` recomputes tags through normal staging/promotion;
`catalog-v3` retains its identity rules. The [closeout receipt](evidence/delegated-closeout.json)
records the refresh and personal-history fingerprints. The prior `roles-v2`
[receipt](evidence/role-tag-verification.json) remains unchanged.

`CardClassifierTest` now has 58 invocations. The complete suite and its intentional
inventory changes are described in [TESTING.md](TESTING.md).
