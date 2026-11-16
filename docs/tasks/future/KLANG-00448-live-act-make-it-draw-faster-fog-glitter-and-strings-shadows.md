# KLANG-00448: Live act: make it draw faster (fog, glitter and strings, shadows)

> **Status (2026-11-16): IDEA.** Kept for later by the maintainer (2026-11-16): optimization comes last.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

With four times the pixels drawn (the key o, "oversampling 4x") the live act drops from 60 to about 30 frames a
second on the maintainer's machine (2026-11-16). The maintainer: "Optimization will come last".

Oversampling this way is brute force: every pixel is fully computed. Almost all of the scene's cost is per pixel,
so the cost grows nearly as the pixels do. It is wanted for what flickers inside a surface (the glitter, the thin
strings, the fine patterns), which the graphics card's own edge smoothing does not touch.

## What is known

Nothing is measured yet. Read from the shaders, where the cost is likely to sit:

1. The fog, most likely by far: twelve see-through sheets over most of the picture, each pixel of each with five
   noise lookups and a loop over five lamps.
2. The lit surfaces: floor and bodies under five lamps, four of them with shadows, each shadow lookup several
   samples a pixel; the cloth's grain and glitter on top.
3. The four shadow maps, redrawn every frame with all six figures: a fixed cost that does not grow with
   oversampling.

A first test costs nothing: 4x with `?nofog`. If that holds 60, the fog is the place.

## Decide before implementing

- Measure first, on the maintainer's machine: with and without the fog, with and without shadows, at 1x, 2x, 4x.
- Whether oversampling stays at all, once the flicker is fixed where it arises.

## The work

Ideas, in the order they look worth trying:

1. Draw the fog at half or quarter size and lay it over the picture: it is soft by nature and needs no
   oversampling.
2. Cheaper fog: its noise read from a small prepared texture, fewer sheets.
3. Fix the flicker where it arises: glitter and patterns that soften with distance, strings never thinner than a
   pixel. Then 1x or 2x may look as 4x does now.
4. Shadows: smaller maps for the lamps that matter less; not every map every frame.

## Acceptance

At the oversampling the maintainer chooses to show it with, the live act holds the screen's rate on the
maintainer's machine.

## History

- 2026-11-16: created.
