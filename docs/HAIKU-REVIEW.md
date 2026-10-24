# Haiku follow-up — 23 October 2026

Two batches repeated the ten fixed deck scenarios against the full **34,473-card
catalog**, using `claude-haiku-5-5` and a separate disposable database. Each batch
returned 50 structured explanations and retained all ten results on repeat requests.
No personal deck was sent. Candidate changes were identical across these two
batches. The [original review](HAIKU-REVIEW-INITIAL.md) retains its earlier outputs
and general owner approval; individual human rubric scores were never supplied.

The original findings led to `roles-v2`: Guardian Project's `nontoken` wording no
longer supplies a tokens theme, Sign in Blood gains draw, and Life from the Loam's
dredge reminder no longer supplies draw. The normal catalog refresh preserved
all personal decks and revisions. [Tagging limits](ROLE-TAGGING.md) remain explicit.

The first full-catalog batch exposed another issue at R05/s1: its explanation
implied Arcane Signet had supplied draw and interaction. The prompt now restricts
swap comparisons to the cut card's supplied Oracle facts, distinguishes existing
deck gaps from the cut's abilities, and asks for relevant conditions and costs.
The repeated R05/s1 identifies the lost mana ability correctly. The final batch
used **17,758 input tokens and 5,180 output tokens**.

The [receipt](evidence/role-tag-verification.json) contains both batch summaries,
the final 50 explanations, supplied card facts and source hashes. Structural
validation and retained results passed. These checks do not establish factual or
strategic quality. Human review remains pending for all ten runs.

Assistant review flags to inspect:

- **R07/s5:** separate Stardew Valley's Food ability from its draw ability; the
  explanation compresses them into ambiguous wording.
- **R09/s4:** make Archdruid's Charm's land-versus-creature destination restriction
  explicit when describing its tutor mode.
- **R07/s4:** assess the usefulness of the coarse interaction label on Lazotep
  Quarry's self-recursion. A keyword match does not establish a useful upgrade.

For each R01–R10 run, a human reviewer should still assess supported facts,
correct change, usefulness and uncertainty, and absence of invented claims under
the rubric in [TESTING.md](TESTING.md). No human scores or attestation were created
by the coding workflow. Java/SQL continue to select and validate every change.
