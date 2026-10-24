# Haiku review: completed with failures

Codex completed the delegated content review on **24 October 2026 UTC**. This is
an assistant review. The owner's earlier “its fine” is general approval of the
initial worksheet; no individual human ratings or human attestation were created.

Two fresh batches used the full **34,473-card catalog**, ten fixed synthetic
scenarios and 50 explanations each. Each run made one provider request and reused
its retained result on a second request. No personal deck was sent.

The final batch used `claude-haiku-5-5`, `roles-v4` and `explanations-v4` at source
`c5200d03d03b09451e2f8f824992e181ab03d7db`: **25,845 input / 5,371 output tokens**.
All ten results passed structural checks and retention. Content review found
**3 passing runs, 2 failing runs and 5 runs with wording flags**. The quality gate
is **failed**. These results do not establish the original human-quality criterion.

The [complete receipt](evidence/haiku-review-closeout.json) contains both batches,
all supplied card facts, every explanation, per-run judgments and source hashes.
The original [fixture review](HAIKU-REVIEW-INITIAL.md) and
[roles-v2 receipt](evidence/role-tag-verification.json) remain unchanged. These are
known regression scenarios used while editing the prompt, so this is not a blind
quality evaluation.

## The three original wording findings

| Finding | Review and resulting change |
| --- | --- |
| Stardew Valley | Food creation and drawing are separate activations. The prompt now preserves separate Oracle lines, costs, effect order and optional choices. The final R07/s4 separates draw from Food and preserves the later optional transfer; its Food cost is still incomplete. |
| Archdruid's Charm | A land enters tapped; a creature goes to hand. The prompt now specifies conditional destinations. R06/s2, R09/s4 and R10/s4 still omit or blur part of that mapping; those runs receive no clean content verdict. |
| Lazotep Quarry | Recursion restricted to your own graveyard supplies no opposing interaction. The tag defect is fixed in `roles-v4`, covered by a named regression and verified in the refreshed catalog. It did not appear among the final 50 ranked changes. |

Corrected reference wording, written by the assistant from the supplied Oracle text:

- **Stardew Valley:** Create Food for {2}, {T}, and tapping an untapped creature you
  control. A separate sorcery-speed {3}, {T} ability chooses a permanent you control
  and draws a card, then another player of your choice may gain control of it.
- **Archdruid's Charm:** Choose one mode. The tutor puts a land onto the battlefield
  tapped or a creature into your hand. The other modes put a +1/+1 counter on your
  creature and have it deal its power in damage to a creature you do not control,
  or exile an artifact or enchantment.
- **Lazotep Quarry:** Its {T}, sacrifice-a-creature ability makes one mana of any
  color. Its sorcery-speed {X}{2}, {T} ability sacrifices a Desert and exiles a
  creature card of mana value X from your graveyard to make a 4/4 black Zombie copy.
  These are conditional recursion and token support.

## Final run judgments

A pass covers the stated facts and reason in that sample. It does not mean every
ability cost was described or the suggestion is strategically optimal. A flag
marks wording that needs correction; a failure identifies an incorrect claim or
change description. Full four-criterion results are in the receipt.

| Run | Theme | Verdict | Main observation |
| --- | --- | --- | --- |
| R01 | Graveyard | Flag | Say a land that produces black mana; “black land” is ambiguous. The entry trigger is now described as required. |
| R02 | Graveyard | Flag | Avoid “drain” for life loss without life gain, and clarify which effects scale with X. |
| R03 | General | Pass | Effects and activation restrictions are supported. |
| R04 | General | Fail | An addition is described with removed-card boilerplate. |
| R05 | General | Pass | Supported changes and the lost Command Tower mana source are described. |
| R06 | Counters | Fail | Choose two modes from four; a creature enters with a counter, rather than a creature spell receiving one. |
| R07 | Tokens | Flag | Food and draw are separated; include Food's full activation cost. |
| R08 | Tokens | Pass | Conditional removal/draw and the mana-source tradeoff are supported. |
| R09 | Counters | Flag | State the creature tutor's hand destination. |
| R10 | Counters | Flag | “As stated” does not explain the tutor's type-to-destination mapping. |

## What changed in the implementation

The classifier excludes observed removal/bounce limited to your own resources.
It recognizes direct tap-for-mana wording such as Arcane Signet while excluding
observed paid filters and land fixing from that new rule. The first live batch
that exposed those filtering mistakes remains in the receipt.

Java now enforces the response's closed object shapes and string field types,
plus exact suggestion IDs, completeness, uniqueness and length. Wrong fields,
refusals, timeout and missing credentials preserve rule explanations and usage.
The input retains separate Oracle lines and real add/cut facts.

AI explanations remain labeled previews requiring review. Java and SQL select
and validate every change; the model supplies no card choices. The passing
**208-check offline suite** establishes the documented constraints and fallback.
The live results establish that prose quality still needs judgment. No unattended
quality claim or guarantee is made.
