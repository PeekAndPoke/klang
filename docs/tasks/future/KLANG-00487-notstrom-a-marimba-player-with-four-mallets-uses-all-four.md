# KLANG-00487: notstrom a marimba player with four mallets uses all four

> **Status (2026-11-26): IDEA.** The maintainer's note for later, 2026-11-26.

## Why this exists

The maintainer (2026-11-26): "Rune (or any Marimba player) when equipped with four mallets actually needs to use all
four. Currently it looks as if only one on each hand is used."

## What is there today

A marimba player holds two mallets in each hand (`Mallets.kt`, `MarimbaPlan.kt`). A hand that plays two notes at
once lands both of its mallets; a hand that plays one note lands its inner mallet on the bar, and the outer one
rides beside it. Most of the marimba's part is single notes, so nearly every blow is an inner mallet's: that is
what the maintainer sees.

## Decide before implementing

Nothing is designed yet. First thoughts, for the maintainer:

- A single note could be given to either mallet of the hand, by where the bar lies: the outer one for a note
  further out, the inner one for a note nearer the middle, so that the hand turns less.
- Runs could go across all four mallets in turn, as players do.
- The plan is told from the notes alone (nothing precomputed), so the choice has to be a rule of the plan.
- Any player with four mallets: the rule belongs to the marimba's plan, not to Rune's sheet.

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-26: created from the maintainer's words.
