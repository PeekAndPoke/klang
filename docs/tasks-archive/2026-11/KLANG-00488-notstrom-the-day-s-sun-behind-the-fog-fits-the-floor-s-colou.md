# KLANG-00488: notstrom the day's sun behind the fog fits the floor's colours

> **Status (2026-11-26): DONE.** The day's sun is a pale warm amber and less bright; the maintainer chose it at the screenshots, 2026-11-26.

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

Done 2026-11-26, in `WorldLight`, by screenshots of the same moment (seed 1, t 50, the day), three steps:

1. As it was: `DAY_SUN` `0xf5a050` (a rich amber, saturation 0.67), `sunStrength` by day 1.3. The stage renders with
   ACES filmic tone mapping, which turns a bright amber yellow: run through three.js's curve, this colour leaves at a
   hue of 36° at its own brightness and 40° at twice it (the colour itself is at 29°). The wall behind the drummer
   measured hue 38, saturation 0.81.
2. Only dimmer (the maintainer: "it might be enough if the sun is less bright"): `sunStrength` 1.0. Less yellow (hue
   32), but still a deep orange beside the floor (the maintainer: "still too bright or too orange").
3. Paler and dimmer, kept: `DAY_SUN` `0xf2c08c` (the same hue, saturation 0.42, in the family of the lamps' day
   colours, mostly about 0.3) and `sunStrength` by day 0.85. The wall's middle measured hue 36, saturation 0.61;
   the floor stayed at hue 27, saturation 0.22.

Left as it is, for a later look: the sides of the fence stay a dark, saturated burnt orange (saturation 0.87). That
is not the sun's colour; most likely the haze's day tint (`hazeTint`, 1.1, 0.95, 0.8) and the lamps behind leaning
toward `DAY_TINT` (`0xffb060`). A neutral haze tint by day (about 1.0, 0.97, 0.92) would be the next step if they
bother.

## Acceptance

- The maintainer chose step 3 at the screenshots (2026-11-26: "Ok the last version it is: pale warm amber").
- `WorldLightSpec` holds the day's strength (0.85); klangviz's js tests green (587).

## History

- 2026-11-26: created from the maintainer's words.
- 2026-11-26: done: the sun paler and dimmer by day, chosen at the screenshots.
- 2026-11-26: DONE.
