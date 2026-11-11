# KLANG-00439: A feedback door for the Ignitor phaser

> **Status (2026-11-11): IDEA.** Follow-up of KLANG-00433 (L4-12), maintainer 2026-11-11.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The Ignitor phaser (the per-note stage) runs with a feedback fixed at 0.5 inside the node, and no door reaches it
(KLANG-00433, audit finding L4-12). The Katalyst phaser on a cylinder has a `feedback` knob. The maintainer
(2026-11-11): "leave it like this, write a followup task for adding the door".

## Decide before implementing

- The default: 0.5 keeps every song as it is.
- The range and whether the knob may be modulated (the Katalyst phaser's rule is the model).

## The work

A `feedback` knob on the Ignitor phaser in both doors (Kotlin and KlangScript, parameter parity), the slot, the node's
read, the docs.

## Acceptance

- Unset, every render is bit-identical to today's.
- Both doors carry the knob with the same name, default and text.

## History

- 2026-11-11: created.
