# KLANG-00391: The visuals' scope: its swing not tied to its height

> **Status (2026-10-30): IN PROGRESS.** Asked for by the maintainer, 2026-10-30

## Why this exists

The maintainer (2026-10-30): "The oscillator in Visual ... the distance from the center must not depend on the height
of the element. in the "thick" 2x1 tiles version in the KlangAmp the distance from the center is too far and this does
not very hectic. So we have to find the sweatspot where it looks dynamic but not hectic. As said the 3x1 tiles version
has the correct setting."

The scope draws full scale at half its height (`Oscilloscope.draw`, `centerY - value * centerY`). On the Motor's plate
(three tiles wide) its hole is 0.42 of a tile high; side by side with the spectrum on one tile row (Klang Amp's tiles,
each half two and a half tiles wide) it is 0.66, so the same signal swings about 1.6 times as far.

## Decide before implementing

- Full scale in pixels, given by the visual: the Motor's plate's swing, a share of a tile, for every hole; the wave is
  clipped by the hole when it is louder than it has room for.
- The other scopes (the code page's Motor, Klang Amp's player) keep half their height: they were not asked about.

## The work

1. `Oscilloscope` takes the full scale's swing in pixels (optional; none keeps half the height).
2. `VisualsBoard` gives each hole the Motor's plate's swing: half of 0.42 of a tile.

## Acceptance

- On Klang Amp's tiles the scope swings as far as on the Motor's plate for the same sound.

## History

- 2026-10-30: created.
- 2026-10-30: built: `Oscilloscope.swingPx`, every hole of the visuals swings as the Motor's plate does (half of 0.42 of a tile); `traceY` with a spec, mutation-checked.
