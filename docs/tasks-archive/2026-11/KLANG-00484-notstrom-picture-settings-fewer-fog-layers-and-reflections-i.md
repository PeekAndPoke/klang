# KLANG-00484: notstrom picture settings fewer fog layers and reflections in three steps

> **Status (2026-11-26): DONE.** The fog in half the sheets; the reflections in four steps, mid as they were.

## Why this exists

The maintainer (2026-11-24), two notes for later:

1. "i think we can reduce the number of fog layers. There is barely any difference noticable between low and high,
   so we could maybe half the number of layer."
2. "another setting for the reflection quality would be nice: low, mid, high ... where mid is the current setting".

## What is there today

- The fog (`FogQuality`): off, low with 4 sheets, high with 8. Each sheet lets less through where there are fewer,
  so the veil is as dense either way.
- The reflections (`ReflectionQuality`): off or on. The drawer and the address (`?reflections=off`) set it.

## Decide before implementing

- The fog: halved would be 2 sheets for low and 4 for high. Whether both steps stay, or one step is enough once
  they look alike, is the maintainer's to say at the screen.
- The reflections: mid is today's "on". What low saves and what high adds is not looked at yet (the mirror's
  picture size is the first thing to look at); off stays.
- The picture test of the app chooses the fog today; whether it also chooses a reflection step.

## The work

Done 2026-11-26.

- **The fog** (`FogQuality`): low two sheets (maps 2 by 1), high four (2 by 2); each sheet lets less through, so the
  veil is as dense as before. Both steps stay for now (asked: one step enough, now that they look alike?). The
  machine test's version went to 5 (`StoredTest.TEST_VERSION`): a kept choice was made on the heavier fog.
- **The reflections** (`ReflectionQuality`): off, low, mid, high; mid is the reflections as they were ("on"), and an
  address's `?reflections=on` gives mid. Each step has a size (its share of mid's picture each way, after the cap),
  a budget (the share of the picture drawn in a frame where the floor fills the screen) and a cap of pixels:
  - low: half of mid's picture each way, a quarter of its pixels on every screen (softer, about a quarter the cost);
  - mid: the screen's own pixels up to 1920 by 1200, at most half of them a frame close over the floor;
  - high: all of the picture every frame, up to a 4K screen's pixels. On a 4K screen the mirror's picture then holds
    some 130 MB on the graphics card; in a normal view on a screen below 1920 by 1200 it looks as mid does (the
    screenshots of 2026-11-26), it shows only close over the floor or on large sharp screens.
- The drawer has four buttons; the Meet the Band stage keeps mid.
- Not done (asked): whether the machine test also chooses a reflection step (low on a slow machine); it leaves them
  at mid.

## Acceptance

- `FogQualitySpec`, `ReflectionQualitySpec`, `StageSpec` (the sheets read from the fog's qualities; the mirror's
  picture at low 640 by 400 and at high 1280 by 800 on a 1280 by 800 canvas; on a 3840 by 2400 canvas mid 1920 by
  1200 and low 960 by 600; close over the floor mid draws less than the whole width, high all of it), `StoredTestSpec`;
  klangviz js and jvm, notstrom js and apps/common green.
- 5 mutants killed (the size ignored, the quality not passed on, the budget fixed, the size before the cap, and the
  arc's in KLANG-00489 run with them).
- Review: round 1 one major (low saved little on high-dpi screens: the size came before the cap), fixed; round 2
  CLEAN (reviewer-opus-high).

## History

- 2026-11-24: created from the maintainer's words.
- 2026-11-26: done: the fog halved, the reflections in four steps.
- 2026-11-26: DONE.
