# KLANG-00484: notstrom picture settings fewer fog layers and reflections in three steps

> **Status (2026-11-24): IDEA.** The maintainer's two notes for later, 2026-11-24.

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

Not planned yet.

## Acceptance

Not planned yet.

## History

- 2026-11-24: created from the maintainer's words.
