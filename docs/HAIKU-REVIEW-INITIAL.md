# Live Haiku review — 23 October 2026

Ten application runs; five suggestions each. Model: `claude-haiku-5-5`. Inputs are the fixed offline scenarios R01–R10, using a disposable database. No personal deck was sent.

All runs returned structured explanations, retained usage, and reused the saved result on a second request. General human approval was received; individual rubric scores were not supplied. Known findings remain open. These checks establish response shape and one-attempt retention.

For every run, record pass/fail for: supported card facts; actual change described; useful reason with appropriate uncertainty; no invented statistics, optimality, or injected instructions. Any unsupported card fact fails that run.

| Run | Facts | Change | Usefulness | No inventions | Reviewer |
| --- | --- | --- | --- | --- | --- |
| R01 | pending | pending | pending | pending | — |
| R02 | pending | pending | pending | pending | — |
| R03 | pending | pending | pending | pending | — |
| R04 | pending | pending | pending | pending | — |
| R05 | pending | pending | pending | pending | — |
| R06 | pending | pending | pending | pending | — |
| R07 | pending | pending | pending | pending | — |
| R08 | pending | pending | pending | pending | — |
| R09 | pending | pending | pending | pending | — |
| R10 | pending | pending | pending | pending | — |

Assistant precheck: R08/s5 claims a tokens-theme match for Guardian Project, whose Oracle text specifies nontoken creatures. The rule tag uses a substring match. Review that strategic claim. R01/s4 and R02/s3 describe Life from the Loam as filling a draw gap; check whether the card-advantage shorthand is useful and precise. These notes are not human scores.

## R01 — Meren of Clan Nel Toth / graveyard / addition

**s1: Mind Stone**
Mind Stone adds a colorless mana source and can be sacrificed to draw a card, covering the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary taps for green mana and draws a card when it enters, covering both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Deathrite Shaman**
Deathrite Shaman can exile graveyard cards for mana or life loss and gain, matching the graveyard theme and filling the interaction gap.
Rule: Fills the interaction gap; matches the graveyard theme.

**s4: Life from the Loam**
Life from the Loam returns land cards from the graveyard to hand and has dredge, matching the graveyard theme and filling the draw gap.
Rule: Fills the draw gap; matches the graveyard theme.

**s5: Return to Nature**
Return to Nature can destroy an artifact or enchantment or exile a graveyard card, matching the graveyard theme and filling the interaction gap.
Rule: Fills the interaction gap; matches the graveyard theme.

## R02 — Meren of Clan Nel Toth / graveyard / swap

**s1: Llanowar Visionary for Arcane Signet**
Llanowar Visionary replaces Arcane Signet, adding a mana creature that also draws a card on entry. It helps fill the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Deathrite Shaman for Arcane Signet**
Deathrite Shaman replaces Arcane Signet and adds graveyard-based interaction that fits the graveyard theme. It fills the interaction gap.
Rule: Fills the interaction gap; matches the graveyard theme.

**s3: Life from the Loam for Arcane Signet**
Life from the Loam replaces Arcane Signet, adding card advantage through land recursion from the graveyard. It fills the draw gap and fits the theme.
Rule: Fills the draw gap; matches the graveyard theme.

**s4: Return to Nature for Arcane Signet**
Return to Nature replaces Arcane Signet with an instant that can destroy an artifact or enchantment or exile a graveyard card. It fills the interaction gap and matches the theme.
Rule: Fills the interaction gap; matches the graveyard theme.

**s5: Grist, the Hunger Tide for Arcane Signet**
Grist, the Hunger Tide replaces Arcane Signet and adds a planeswalker that makes tokens and can destroy creatures. It fills the interaction gap and matches the graveyard theme.
Rule: Fills the interaction gap; matches the graveyard theme.

## R03 — Meren of Clan Nel Toth / general / addition

**s1: Mind Stone**
Mind Stone is a colorless mana rock that taps for mana and can be sacrificed to draw a card. It fills the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary draws a card when it enters and taps for green mana. It fills the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Solemn Simulacrum**
Solemn Simulacrum fetches a basic land when it enters and draws a card when it dies. It fills the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s4: Command Tower**
Command Tower taps for any color in the commander's color identity. It fills the land gap.
Rule: Fills the land gap.

**s5: Forest**
Forest is a basic land that taps for green mana. It fills the land gap.
Rule: Fills the land gap.

## R04 — Ezuri, Renegade Leader / general / addition

**s1: Mind Stone**
Mind Stone adds colorless mana and can be sacrificed to draw a card, covering the ramp and draw gaps. It is an artifact that taps for {C}.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary draws a card when it enters and taps for green mana, covering both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Solemn Simulacrum**
Solemn Simulacrum fetches a basic land when it enters and draws a card when it dies, covering both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s4: Command Tower**
Command Tower taps for mana of any color in the commander's color identity, filling the land gap.
Rule: Fills the land gap.

**s5: Forest**
Forest taps for {G} and fills the land gap.
Rule: Fills the land gap.

## R05 — Ezuri, Renegade Leader / general / swap

**s1: Llanowar Visionary for Arcane Signet**
Llanowar Visionary replaces Arcane Signet to add ramp and a card draw effect on entering. It is a green Elf Druid that taps for mana.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Solemn Simulacrum for Arcane Signet**
Solemn Simulacrum replaces Arcane Signet to add ramp by fetching a basic land and draws a card when it dies.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Elvish Mystic for Arcane Signet**
Elvish Mystic replaces Arcane Signet to add one-mana ramp. It is an Elf that taps for green mana.
Rule: Fills the ramp gap.

**s4: Fyndhorn Elves for Arcane Signet**
Fyndhorn Elves replaces Arcane Signet to add one-mana ramp. It is an Elf that taps for green mana.
Rule: Fills the ramp gap.

**s5: Llanowar Elves for Arcane Signet**
Llanowar Elves replaces Arcane Signet to add one-mana ramp. It is an Elf that taps for green mana.
Rule: Fills the ramp gap.

## R06 — Ezuri, Renegade Leader / counters / addition

**s1: Mind Stone**
Mind Stone adds a colorless mana source and can later be sacrificed to draw a card, covering the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary taps for green mana and draws a card when it enters, addressing both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Inspiring Call**
Inspiring Call draws a card for each creature with a +1/+1 counter, filling the draw gap and matching the counters theme.
Rule: Fills the draw gap; matches the counters theme.

**s4: Solemn Simulacrum**
Solemn Simulacrum fetches a basic land on entry and draws a card when it dies, covering both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s5: Command Tower**
Command Tower taps for any color in the commander's color identity, filling the land gap.
Rule: Fills the land gap.

## R07 — Trostani, Selesnya's Voice / tokens / addition

**s1: Mind Stone**
Mind Stone is a mana rock that taps for colorless mana and can be sacrificed to draw a card, filling the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary draws a card when it enters and taps for green mana, filling the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Beast Within**
Beast Within is an instant that destroys a target permanent and gives its controller a 3/3 Beast token, covering the interaction gap with a tokens-related downside.
Rule: Fills the interaction gap; matches the tokens theme.

**s4: Generous Gift**
Generous Gift is an instant that destroys a target permanent and gives its controller a 3/3 Elephant token, covering the interaction gap with a tokens-related downside.
Rule: Fills the interaction gap; matches the tokens theme.

**s5: Solemn Simulacrum**
Solemn Simulacrum ramps a basic land when it enters and draws a card when it dies, filling the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

## R08 — Trostani, Selesnya's Voice / tokens / swap

**s1: Llanowar Visionary for Arcane Signet**
Llanowar Visionary replaces Arcane Signet, adding a creature that draws a card and taps for green mana. It fills the ramp and card draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Beast Within for Arcane Signet**
Beast Within replaces Arcane Signet as an instant that destroys a permanent and gives its controller a 3/3 Beast token. It fills the interaction gap and matches the tokens theme.
Rule: Fills the interaction gap; matches the tokens theme.

**s3: Generous Gift for Arcane Signet**
Generous Gift replaces Arcane Signet as an instant that destroys a permanent and gives its controller a 3/3 Elephant token. It fills the interaction gap and matches the tokens theme.
Rule: Fills the interaction gap; matches the tokens theme.

**s4: Solemn Simulacrum for Arcane Signet**
Solemn Simulacrum replaces Arcane Signet, ramping a basic land when it enters and drawing a card when it dies. It fills the ramp and card draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s5: Guardian Project for Arcane Signet**
Guardian Project replaces Arcane Signet and draws a card when nontoken creatures enter that don't share a name with another creature you control or in your graveyard. It fills the card draw gap and matches the tokens theme.
Rule: Fills the draw gap; matches the tokens theme.

## R09 — Atraxa, Praetors' Voice / counters / addition

**s1: Mind Stone**
Mind Stone adds colorless mana and can be sacrificed to draw a card, covering both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Llanowar Visionary**
Llanowar Visionary draws a card when it enters and taps for green mana, filling the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s3: Inspiring Call**
Inspiring Call draws a card for each creature you control with a +1/+1 counter, fitting the counters theme and the draw gap.
Rule: Fills the draw gap; matches the counters theme.

**s4: Solemn Simulacrum**
Solemn Simulacrum fetches a basic land when it enters and draws a card when it dies, filling both the ramp and draw gaps.
Rule: Fills the ramp gap; fills the draw gap.

**s5: Command Tower**
Command Tower taps for mana of any color in your commander's color identity, filling the land gap.
Rule: Fills the land gap.

## R10 — Atraxa, Praetors' Voice / counters / swap

**s1: Llanowar Visionary for Arcane Signet**
Llanowar Visionary adds a mana source and draws a card on entry, replacing Arcane Signet, which only produces one mana of the commander's color identity.
Rule: Fills the ramp gap; fills the draw gap.

**s2: Inspiring Call for Arcane Signet**
Inspiring Call draws a card for each creature with a +1/+1 counter, fitting the counters theme in place of Arcane Signet.
Rule: Fills the draw gap; matches the counters theme.

**s3: Solemn Simulacrum for Arcane Signet**
Solemn Simulacrum ramps by fetching a basic land on entry and draws a card when it dies, replacing Arcane Signet.
Rule: Fills the ramp gap; fills the draw gap.

**s4: Deathrite Shaman for Arcane Signet**
Deathrite Shaman adds interaction through its graveyard exile abilities, replacing Arcane Signet.
Rule: Fills the interaction gap.

**s5: Elvish Mystic for Arcane Signet**
Elvish Mystic is a one-mana creature that taps for green, adding ramp in place of Arcane Signet.
Rule: Fills the ramp gap.

## Supplied Oracle facts

### Arcane Signet
Artifact · mana value 2.0
{T}: Add one mana of any color in your commander's color identity.

### Atraxa, Praetors' Voice
Legendary Creature — Phyrexian Angel Horror · mana value 4.0
Flying, vigilance, deathtouch, lifelink
At the beginning of your end step, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)

### Beast Within
Instant · mana value 3.0
Destroy target permanent. Its controller creates a 3/3 green Beast creature token.

### Command Tower
Land · mana value 0.0
{T}: Add one mana of any color in your commander's color identity.

### Deathrite Shaman
Creature — Elf Shaman · mana value 1.0
{T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)
{B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.
{G}, {T}: Exile target creature card from a graveyard. You gain 2 life.

### Elvish Mystic
Creature — Elf Druid · mana value 1.0
{T}: Add {G}.

### Ezuri, Renegade Leader
Legendary Creature — Elf Warrior · mana value 3.0
{G}: Regenerate another target Elf.
{2}{G}{G}{G}: Elf creatures you control get +3/+3 and gain trample until end of turn.

### Forest
Basic Land — Forest · mana value 0.0
({T}: Add {G}.)

### Fyndhorn Elves
Creature — Elf Druid · mana value 1.0
{T}: Add {G}.

### Generous Gift
Instant · mana value 3.0
Destroy target permanent. Its controller creates a 3/3 green Elephant creature token.

### Grist, the Hunger Tide
Legendary Planeswalker — Grist · mana value 3.0
As long as Grist isn't on the battlefield, it's a 1/1 Insect creature in addition to its other types.
+1: Create a 1/1 black and green Insect creature token, then mill a card. If an Insect card was milled this way, put a loyalty counter on Grist and repeat this process.
−2: You may sacrifice a creature. When you do, destroy target creature or planeswalker.
−5: Each opponent loses life equal to the number of creature cards in your graveyard.

### Guardian Project
Enchantment · mana value 4.0
Whenever a nontoken creature you control enters, if it doesn't have the same name as another creature you control or a creature card in your graveyard, draw a card.

### Inspiring Call
Instant · mana value 3.0
Draw a card for each creature you control with a +1/+1 counter on it. Those creatures gain indestructible until end of turn. (Damage and effects that say "destroy" don't destroy them.)

### Life from the Loam
Sorcery · mana value 2.0
Return up to three target land cards from your graveyard to your hand.
Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)

### Llanowar Elves
Creature — Elf Druid · mana value 1.0
{T}: Add {G}.

### Llanowar Visionary
Creature — Elf Druid · mana value 3.0
When this creature enters, draw a card.
{T}: Add {G}.

### Meren of Clan Nel Toth
Legendary Creature — Human Shaman · mana value 4.0
Whenever another creature you control dies, you get an experience counter.
At the beginning of your end step, choose target creature card in your graveyard. If that card's mana value is less than or equal to the number of experience counters you have, return it to the battlefield. Otherwise, put it into your hand.

### Mind Stone
Artifact · mana value 2.0
{T}: Add {C}.
{1}, {T}, Sacrifice this artifact: Draw a card.

### Return to Nature
Instant · mana value 2.0
Choose one —
• Destroy target artifact.
• Destroy target enchantment.
• Exile target card from a graveyard.

### Solemn Simulacrum
Artifact Creature — Golem · mana value 4.0
When this creature enters, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
When this creature dies, you may draw a card.

### Trostani, Selesnya's Voice
Legendary Creature — Dryad · mana value 4.0
Whenever another creature you control enters, you gain life equal to that creature's toughness.
{1}{G}{W}, {T}: Populate. (Create a token that's a copy of a creature token you control.)
