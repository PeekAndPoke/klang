# KLANG-00393: The rain's depth: a wider spread of falling speeds

> **Status (2026-10-30): IN PROGRESS.** Asked for by the maintainer, 2026-10-30

## Why this exists

The maintainer (2026-10-30): "The matrix display in the visualizations. Spread the random "falling" speed more. Keep
the max as is but allow for a smaller minimum to increase the paralax "depth"."

## Decide before implementing

- How far down the minimum goes: by eye; slow drops stay on screen longer, so at most `MAX_DROPS` fall at once still.

## The work

1. `VoiceRain`'s `SPEED_MIN` lower, `SPEED_MAX` kept.

## Acceptance

- Some drops fall clearly slower, deep behind the fast ones.

## History

- 2026-10-30: created.
- 2026-10-30: built: `SPEED_MIN` 5 to 2 letters a second, `SPEED_MAX` 11 kept.
