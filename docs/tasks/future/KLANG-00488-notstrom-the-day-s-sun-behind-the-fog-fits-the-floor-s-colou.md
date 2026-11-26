# KLANG-00488: notstrom the day's sun behind the fog fits the floor's colours

> **Status (2026-11-26): IDEA.** The maintainer's note for later, 2026-11-26.

## Why this exists

The maintainer (2026-11-26): "The day lighting, the sun behind the fog is too yellow somehow. It does not mix with
the floor colors. We need to adjust the sun so that it fits better to the floor colors."

## What is there today

The day's own colour is one constant, `WorldLight.DAY_SUN` (an orange, `0xf5a050`); the light behind the fence and
the fog takes it, leaning toward the mood's colour (`WorldLight`, around `colorAt`). The floor is lit by the stage's
lamps in the mood's colours (since 2026-11-23 three sets of them, for day, twilight and night), so the two are
chosen apart from each other. In the stills of 2026-11-24 the wall behind the fog reads clearly yellow beside a
floor in cool blues and purples.

## Decide before implementing

Look tuning at the screen, with the maintainer:

- Which way the sun moves: paler and warmer (less saturated), or toward the floor's colours of the mood that
  plays, so that it follows them.
- Whether the fog's own tint in the day needs the same step (it carries the sun's colour forward).
- The lamps' day set was chosen against this sun: it may want a look again once the sun has moved.

## The work

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-26: created from the maintainer's words.
