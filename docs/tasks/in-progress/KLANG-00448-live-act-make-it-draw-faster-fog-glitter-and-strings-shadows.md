# KLANG-00448: Live act: make it draw faster (fog, glitter and strings, shadows)

> **Status (2026-11-16): IN PROGRESS.** The maintainer saw 22 frames a second on a MacBook Air and asked for the low hanging fruit: a first round (the fog's noise from a texture, a dark lamp's shadow not drawn, the resolution follows the frame rate).
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

A second cost, apart from the drawing (the maintainer, 2026-11-16, after seeing the band follow a live song): "when
sprudel fetches the next cycle it start to stutter a little... so in the long run we will need have more
webworkers ... one for klangscript and sprudel etc ... and one for visuals." Today the page's own thread asks the
pattern for each cycle (the player's scheduler, and the band's reader once more in KLANG-00449), and draws. A
worker for KlangScript and sprudel would take the asking off the thread that draws; one for the visuals would take
the drawing off the thread the app's interface runs on. Larger than this ticket, and touching the player: likely a
ticket of its own when it is taken up, with Klang-Main-Dev.

## Measured and done, round 1 (2026-11-16)

The maintainer, looking at it on a MacBook Air: "have only 22fps ... this is not good". A fine screen draws four
times the pixels of a plain one, and the scene's cost is per pixel.

Measured in the software renderer of the test browser (720 by 450 at two pixels each way, one frozen moment; it
ranks per-pixel costs but says nothing sure about a real graphics card):

| | frame |
|---|---|
| as it was | about 510 ms |
| without the fog (`?nofog`) | about 230 ms |

So the fog was more than half of a frame. The figures are not the problem: the six have 53,000 triangles together;
with instruments and the four shadow passes a frame draws about 290,000 triangles in about 755 calls (the status
line shows both now).

Done:

1. The fog's noise is read from a block of random numbers on the graphics card (64 each way, repeating), in place
   of forty computed random numbers a pixel and sheet. Same look, another pattern. The software renderer shows no
   gain from it (it reads textures slowly); a real card should, and the MacBook is the judge.
2. A dark lamp's shadow is not drawn. The front lamp is dark 32 of every 40 bars, and the whole band was drawn into
   its shadow all the same. Once it must be drawn, though: without a shadow to read, three.js draws nothing lit at
   all (found by the stills and by the reviewer's probe).
3. The resolution follows the frame rate (`Sharpness`, with a spec): after two seconds under 47 frames a second
   the picture is drawn with fewer pixels (0.84 each way a step), never coarser than a plain screen. It does not
   come up by itself; a new window size starts it fine again. Where two steps did not help (a browser saving power
   draws 30 frames whatever there is) it goes back to full and stays. `?sharp` keeps the screen's pixels. Not with
   oversampling, not in a still.
   A page in the background is not judged. Known limit: a slow machine in a browser capped at 30 frames goes down
   to the floor, the last steps for nothing.
4. The status line is written ten times a second instead of with every frame, and shows the pixels drawn.

Not measured yet: what the MacBook does now. Candidates after that, by what they should give:

- The fog at half size, laid over the picture (idea 1 below): the fog would cost a quarter.
- Fewer shadow lookups a pixel: every lit pixel reads four shadow maps, several samples each.
- The instruments as fewer meshes: about 150 calls a pass, five passes.
- Shadow maps of 1024 for the lamps that matter less.

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
- 2026-11-16: the maintainer's direction for the long run noted: web workers for KlangScript with sprudel, and for the visuals.
- 2026-11-16: IN PROGRESS. The maintainer saw 22 frames a second on a MacBook Air and asked for the low hanging fruit: a first round (the fog's noise from a texture, a dark lamp's shadow not drawn, the resolution follows the frame rate).
- 2026-11-16: round 1 done and reviewed (three rounds: the vanished band behind a never drawn shadow, the background tab in Firefox, a fast half second taken for proof); 18 mutants on the new specs, all caught.
