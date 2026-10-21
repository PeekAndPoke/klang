# KLANG-00327: Editor: navigate back and forward through jump-off points

> **Status (2026-10-21): IDEA.** Prepared in the key map (KLANG-00325) without a key; built later.

## Why this exists

The key map interview (2026-10-21): "Navigate back / forward" as in IntelliJ, returning to where the cursor was before
a jump (go to definition, a bookmark, go to line, find). The maintainer chose "prepare, no key today".

## The idea

- A list of jump-off points, kept like the edit places of KLANG-00322 (positions mapped through every change).
- Back returns to the previous point, forward undoes a back.
- IntelliJ's keys are Ctrl+Alt+Left / Right; the key is decided when this is built. Alt+Left / Right stay free (the
  browser's back and forward, and later switching between files).

## History

- 2026-10-21: created as an idea (the KLANG-00325 interview).
