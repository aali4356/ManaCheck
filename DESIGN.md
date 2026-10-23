---
name: ManaCheck
description: A compact Commander deck workspace in Pacific navy, coral and apricot.
colors:
  bg: '#151f30'
  header: '#1a293f'
  panel: '#1d2c42'
  surface-hover: '#263850'
  input: '#131e2f'
  line: '#35465e'
  text: '#f1eef1'
  muted: '#b1bdce'
  coral: '#f39a86'
  coral-hover: '#ffb39e'
  apricot: '#edbd88'
  rose: '#d88b93'
  success: '#a2d0bb'
  danger: '#ffb0b0'
  field-hover-line: '#71849d'
  theme-bg: '#37313e'
typography:
  headline:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 28px
    fontWeight: 700
    lineHeight: '1.25'
    letterSpacing: -0.025em
  headline-editor:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 26px
    fontWeight: 700
    lineHeight: '1.25'
    letterSpacing: -0.025em
  headline-mobile:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 24px
    fontWeight: 700
    lineHeight: '1.25'
    letterSpacing: -0.025em
  title:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 18px
    fontWeight: 650
    lineHeight: '1.4'
  section:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 15px
    fontWeight: 650
    lineHeight: '1.4'
  body:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: '1.5'
  button-label:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 13px
    fontWeight: 650
    lineHeight: 20px
  field-label:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 12px
    fontWeight: 600
    lineHeight: '1.5'
  quiet:
    fontFamily: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
    fontSize: 12px
    fontWeight: 400
    lineHeight: '1.5'
rounded:
  sm: 6px
  panel: 12px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 20px
  '6': 24px
  '8': 32px
components:
  button-primary:
    backgroundColor: '{colors.coral}'
    textColor: '{colors.input}'
    typography: '{typography.button-label}'
    rounded: '{rounded.sm}'
    padding: 9px 15px
  button-primary-hover:
    backgroundColor: '{colors.coral-hover}'
    textColor: '{colors.input}'
  button-primary-active:
    backgroundColor: '{colors.apricot}'
  button-secondary:
    backgroundColor: transparent
    textColor: '{colors.text}'
    typography: '{typography.button-label}'
    rounded: '{rounded.sm}'
    padding: 9px 15px
  button-secondary-hover:
    backgroundColor: '{colors.surface-hover}'
    textColor: '{colors.text}'
  button-secondary-active:
    backgroundColor: '{colors.panel}'
  button-disabled:
    backgroundColor: '{colors.panel}'
    textColor: '{colors.muted}'
  input:
    backgroundColor: '{colors.input}'
    textColor: '{colors.text}'
    rounded: '{rounded.sm}'
    padding: 9px 12px
    width: 100%
  theme-tag:
    backgroundColor: '{colors.theme-bg}'
    textColor: '{colors.apricot}'
    padding: 1px 7px
  create-panel:
    backgroundColor: '{colors.panel}'
    rounded: '{rounded.panel}'
    padding: 24px
  analysis-panel:
    backgroundColor: '{colors.panel}'
    rounded: '{rounded.panel}'
    padding: 20px
  suggestion-card:
    backgroundColor: '{colors.panel}'
    rounded: '{rounded.panel}'
    padding: 20px
  deck-entry:
    padding: 10px 4px 10px 8px
---

# Design System: ManaCheck

## Overview

**Creative North Star: "The Pacific Deck Workbench"**

A compact local deck workbench, following the user-confirmed Moxfield-inspired working density. Pacific navy surrounds blue-slate panels; coral identifies primary actions and apricot marks focus and analysis. Full card imagery anchors the deck library and commander area.

The interface uses one system sans, aligned rows, short labels and native controls. Deck editing, saved-revision findings and comparison stay close to the task. Supporting rules, provenance and optional AI details use progressive disclosure.

**Key Characteristics:**

- Dark Pacific navy with restrained coral and apricot accents.
- Dense aligned rows and desktop working columns that stack on mobile.
- Real card imagery, short copy and visible keyboard focus.

## Colors

The frontmatter preserves the source palette; its names match CSS custom properties except the two named literal colors.

### Primary

- **Coral:** primary actions, search-result actions, brand mark and active navigation. **Coral hover** brightens interaction.

### Secondary

- **Apricot:** visible focus, theme tags, role progress, mana values and neutral feedback.
- **Rose:** mana-curve bars and proposed cuts.
- **Success / Danger:** confirmed selections and positive deltas / errors, findings and negative deltas. Pair these with text.

### Neutral

- **Pacific navy background:** page canvas. **Header** and **Panel:** shell and grouped work areas.
- **Input:** inset fields and dark primary-button text. **Surface hover:** interactive neutral surfaces.
- **Text / Muted:** primary content / secondary content. **Line:** dividers and field strokes.
- **Field hover line:** the shared brighter field and color-chip stroke. **Theme background:** the tag's muted warm backing.

**The Action and Evidence Rule.** Use coral for primary actions, apricot for focus and role coverage, rose for mana-curve marks, and text alongside status color.

## Typography

The frontmatter records the system-sans hierarchy. Headings are compact and moderately heavy; body text stays readable within dense controls and rows. There is no separate display face or external font dependency.

- **Headline:** library and comparison titles; the editor uses the smaller editor role. All use the mobile headline below the mobile breakpoint.
- **Title / Section:** section headings; analysis and suggestion titles also use observed local sizes (16px).
- **Body:** general reading. **Button label:** actions. **Field label / Quiet:** form labels and supporting context.
- **Rows:** library deck names use (16px), reducing to (14px) on mobile; card names use (13px), reducing to (12px). Metadata spans (10–12px).
- **Numbers:** quantities, counts, progress labels and revision tables use tabular figures. Explanatory paragraphs may be constrained to (70ch).

**The One Interface Voice Rule.** Use the system sans throughout the interface; reserve monospace for pasted decklists and diagnostic output.

## Layout

The shared shell is centered with a maximum width of (1440px), desktop side padding of (36px), and top padding of (32px). Comparison narrows to (1120px). Reused spacing steps live in the frontmatter; compact component padding also uses source-specific values.

The library places deck rows beside a creation panel (320px) with a (32px) gap. The desktop editor uses commander, decklist and analysis columns (200px / flexible / 280px) with (28px) gaps. Suggestion panels use an auto-fitting grid with a minimum card width of (220px).

- At (1150px) and below, editor side columns shrink to (165px / 250px), gaps become (20px), library creation narrows to (290px), and shell side padding becomes (28px).
- At (950px) and below, the commander becomes a full-width horizontal area above decklist and analysis (flexible / 260px). The library creation panel moves below the list.
- At (680px) and below, the editor and suggestions stack into one column, shell padding becomes (24px 20px 0), and headings use the mobile role. Library rows hide revision and updated metadata while keeping count and artwork. Header navigation retains the library link; the duplicate new-deck link and workspace label hide.
- Comparison keeps its two selectors side by side at mobile widths, with smaller padding. The fixed-layout quantity table wraps long card names and retains numeric columns.

## Elevation & Depth

Depth comes from tonal layering, fine dividers and spacing. Panels sit directly on the canvas with no box shadows, gradients, blur or lifted hover transforms in the shipped stylesheet. Button and row transitions affect color, not position (160ms, ease-out). Reduced-motion mode removes transitions and animation and restores automatic scrolling.

**The Tonal Depth Rule.** Separate work areas with surface tone, spacing and fine borders; the shipped interface has no shadows.

## Shapes

Small controls use the small radius and grouped panels use the panel radius from the frontmatter. Lists stay flat with bottom rules. Native full-card artwork keeps its proportions with contain sizing; library thumbnails use (5px) corners and commander artwork uses (9px). Theme tags use (4px) corners; compact chart tracks use (3px). These local shapes do not replace the shared control and panel radii.

## Components

### Buttons

Compact, text-first actions with a minimum height of (40px). Primary, secondary and disabled colors are in the frontmatter. Secondary buttons use a fine line border; hover brightens that stroke and fills the surface. Active states use apricot for primary and panel tone for secondary. Busy state uses a progress cursor and apricot border. All keyboard-focusable controls share an apricot outline (2px) with an offset of (3px).

### Chips

Theme tags are passive labels with apricot text and warm muted backing, capitalized (11px). Color-identity chips are outlined, muted and numeric; they are not toggle buttons.

### Cards / Containers

Creation, analysis, comparison selectors and suggestions share panel tone and rounded corners. Creation and comparison selectors use (24px) padding on desktop and (20px) on mobile; analysis and suggestions use (20px). Suggestion cards lead with a card name, one reason and the relevant action; any cut is a short rose line. The shared review warning remains at the section heading.

### Inputs / Fields

Inset dark surfaces with fine borders, a minimum height of (42px), source padding and the small radius. Hover uses the brighter field stroke; focus uses the shared outline. Placeholders remain muted and opaque. Quantity inputs shrink to a minimum height of (34px), centered values and narrow padding. Feedback uses explicit status text; source errors use the danger color. No separate field-error or field-disabled visual variant is defined.

### Navigation

A compact header contains the coral sun-over-water SVG mark, wordmark and text links. Desktop header links have a minimum height of (64px), reducing to (60px) on mobile. Hover and the current page use primary text and a coral bottom border. Keep the skip link available to keyboard users.

### Deck Rows and Analysis

Library rows pair full-card thumbnails with deck name, commander, theme, card count and revision. Editor rows align card identity, mana value, editable quantity and an SVG remove control. Row hover uses panel tone. Analysis keeps numeric role labels beside native progress bars and mana-curve values beside meters; roles use apricot and curve marks use rose. Revision comparison aligns quantities in a semantic table with signed, colored deltas.

## Do's and Don'ts

### Do:

- **Do** keep deck names, counts and quantities aligned in rows.
- **Do** retain card-image fallbacks and contain the full card artwork.
- **Do** keep keyboard focus visible and reduced-motion preferences effective.
- **Do** keep shared caveats beside the relevant work and detailed limits in disclosures.

### Don't:

- **Don't** add a marketing hero or ornamental display face to task screens.
- **Don't** repeat card titles or the shared review warning inside each suggestion.
- **Don't** use color as the only description of validation or revision changes.

Source: `src/main/resources/static/app.css`, shared templates and `app.js`. Product constraints: `PRODUCT.md`. The source stylesheet remains authoritative. This document records the completed code-led system; tonal ramps in the sidecar are preview metadata, not additional application colors.
