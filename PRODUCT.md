# ManaCheck

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Commander players maintaining personal decklists.

## Product Purpose

Save deck revisions, inspect construction findings, compare changes, and review up to five targeted additions or swaps.

## Operating Context

A single-user local application, used on desktop and mobile browsers. Java 21, Spring Boot, Thymeleaf, vanilla JavaScript, and PostgreSQL support the existing application. The bundled card sample supports an offline demonstration; a deliberate Scryfall import expands the catalog.

## Capabilities and Constraints

- Preserve deck creation, commander/card search, pasted-list preview, quantity editing, immutable saves, revision comparison, analysis, suggestions, and optional AI explanations.
- Support one commander and the four existing themes: general, tokens, counters, and graveyard.
- Incomplete drafts remain saveable. Recommendations run against saved revisions and a catalog snapshot.
- Construction findings must retain catalog provenance and the existing manual-review boundaries.
- AI explains changes already selected and validated by Java and SQL. Missing credentials and provider failures retain rule explanations.
- Public hosting, multiplayer accounts, prices, trading, and complete deck generation remain outside v1.
- Existing content security policy allows local scripts/styles and card images from cards.scryfall.io. Keep external fonts and scripts out.

## Brand Commitments

ManaCheck remains the product name. The user requested a compact Moxfield-inspired interface, a San Diego sunset palette with dark Pacific navy, coral and apricot accents, card imagery, and much shorter copy. They confirmed this direction and direct implementation in the app on 2026-10-22.

## Evidence on Hand

Working library, editor, and comparison screens; real imported Scryfall card data; isolated browser journeys; API and SQL documentation; local verification receipts in docs/evidence. The existing recording predates this visual redesign. No live AI quality review or hosted deployment is claimed.

## Product Principles

- Put the deck and the next useful action first.
- Keep history and validation visible without repeating instructions.
- Preserve the distinction between construction checks, heuristics, and optional AI explanations.
- Keep the app usable without remote card images or an AI key.

## Accessibility & Inclusion

Preserve semantic controls, visible keyboard focus, live feedback, text descriptions of status, and layouts without horizontal page overflow on mobile.
