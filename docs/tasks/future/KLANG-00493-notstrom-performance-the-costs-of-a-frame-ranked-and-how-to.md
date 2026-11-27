# KLANG-00493: notstrom performance: the costs of a frame, ranked, and how to measure them

> **Status (2026-11-27): IDEA.** A list to work through, from a session with the maintainer on a MacBook Air
> (retina, 60 Hz) that the machine test put on Low while it runs the High picture at 60 fps.

## Why this exists

The maintainer (2026-11-27): "Ok can you formalize what we just discussed and sort thing by potential savings so we
have a list to work through. If you cannot estimate yet please make a plan for each point how we can figure out if
the point is worth addressing."

## What we know (measured 2026-11-27)

On the Air, the `?dev` line at the top:

| Moment | Picture | fps | Slowest frame | Pixels | Draws | Triangles |
|---|---|---|---|---|---|---|
| Opening, t 2.9 s, total camera, new look, drift world, ghosts | Low | 51 | 24 ms | 2.00x | 556 | 577k |
| t 38.6 s, a close-up on Beben, same look | High | 60 | 45 ms | 1.19x | 230 | 388k |

In a local build with a probe that counts every WebGL draw (the HUD's count is the true count per frame):

- Opening, Low: 556 draws. Total camera at t 40, High: 735. The opening on High with the front lamp lit: 863.
- Every shadow-casting lamp redrew about 125 objects; the marimba (37 bars, each its own material) and the drum kit
  (42 meshes) were about 85 of them.
- The figures are few draws (13 parts) but most of the triangles: about 130k of them in every pass that draws them
  (main view, three or four shadow maps, the mirror), skinned anew in each.
- While the world drifts, the lacquer's room is redrawn at most every 120 ms: 34 draws each time.

How a frame's cost is made up, in short: the CPU prepares the frame (our per-frame code, then three.js and WebGL per
draw call, on a Mac translated to Metal), the GPU draws it (vertices, skinning per pass, pixels times the shader's
cost, transparent layers, a render target switch per pass, which a tile-based GPU like Apple's pays for). The slower
of the two sets the frame rate. three.js sorts opaque objects by material (then front to back) and skips state
changes that are not needed; transparent ones are sorted by distance only.

## The list, by what it can save

### A. Known, done or under way

1. **The machine test measures what the show draws.** Today it measures the show frozen at its opening, at full
   retina pixels (a frozen show does not adapt its sharpness), against a fixed 55 fps. The show then adapts its
   pixels (1.19x above) and holds 60 fps with High. Fix: measure at the sharpness the show settles at, on a typical
   moment as well as the opening, against the screen's own rate. Saves no frame time, but it is the biggest change
   for a visitor: the Air gets High instead of Low (and its reflections). Next to do.
2. **Draw calls of the instruments** (kit merged by material at build time, its layout still free; the marimba's
   bars instanced; trumpet and strings merged). Measured: 556 to 227 at the opening, 735 to 309 at High, 863 to
   359 with the front lamp. Built 2026-11-27, in review.

### B. Likely large, to be measured first

3. **Pixel work.** At pixels 2.00x the GPU colours four times the pixels of 1.00x, each with the lacquer, several
   lamps and a shadow lookup per lamp. Likely the Air's main limit.
   *Measure:* on the Air at the opening, `?dev&sharp` (pixels 2.00) against `?dev&sharp&oversample=0.5` (half the
   pixels), same picture. If fps rises a lot, pixels are the limit; then look at 5, 8 and the lacquer's cost.
4. **The figures in the shadow passes.** About 130k skinned triangles redrawn for every shadow-casting lamp.
   A coarse stand-in (10k to 20k triangles, the same skeleton) only for casting shadows would hardly show, since
   shadow maps are soft and low in resolution.
   *Measure:* a `?dev` switch that turns the figures' `castShadow` off; compare fps at the opening with shadows
   High. A clear rise means the stand-in pays.
5. **Shadow-casting lamps: how many, how large, how soft.** Each is a whole pass, and every lit pixel samples
   every map.
   *Measure:* shadows High against Low on the Air (the preset already switches it), then one lamp at a time off.
   Options then: fewer lamps cast, smaller maps, fewer filter samples, the back lamps' maps every other frame (a
   frame late: may show on fast sticks, the maintainer's call).

### C. Medium, to be measured

6. **Far shots draw the figures at full detail.** In a total shot a figure's triangles are about a pixel each; the
   GPU colours pixels in 2x2 blocks, so it may shade up to four times the pixels it needs. A lighter version for far
   shots (a third of the triangles), the full one in close-ups.
   *Measure:* the same picture and pixels in the total shot and a close-up, fps of each; or one figure thinned with
   gltfpack (meshoptimizer) swapped in and compared.
7. **The mirror pass** draws the scene once more (177 draws in the total shot before item 2).
   *Measure:* reflections Off, Mid, High on the Air (a setting already). Options: keep fine parts (strings, gold
   inlays, the shadow hair) out of it by a layer (about 20 draws), a smaller mirror picture.
8. **Transparent layers** (fog sheets, haze, ghosts, eyes): every layer costs its full pixels again.
   *Measure:* fog Off, Low, High at the opening, and the drift world against plain.
9. **Shader programs and compile hitches.** Materials changed with `onBeforeCompile` get a program key from the
   function's text unless `customProgramCacheKey` is set; more programs mean more switching and more compiles when
   the picture changes (the 24 ms and 45 ms slowest frames may be these, or item 10).
   *Measure:* add `renderer.info.programs.length` to the `?dev` line and compare it with the kinds of material we
   have. Options: set the cache keys; compile ahead with `renderer.compileAsync` at the start and before a preset
   switch.
10. **Hitches from garbage collection.** Objects made every frame (vectors, lists, closures) are freed in batches.
    *Measure:* a Chrome Performance recording of 30 s of the show; count frames over 25 ms and what they hold
    (GC, compile, our code).

### D. Small, known

11. The mallets (two materials each: 8 draws per pass), the strings' hardware (1 or 2 meshes per instrument), the
    lacquer room's redraw (every 250 ms instead of 120: its sun strip follows the drift a little more coarsely),
    the shadow hair drawn in the main view and the mirror with `colorWrite` off (1 to 2 draws).
12. **Thinning the figures' flat areas** in the full model: a modest saving (maybe 20 to 40% of the triangles),
    smaller files and a faster load. Careful on curves: the lacquer shows any faceting.
13. Our per-frame code on the CPU (Kotlin/JS): only if item 10's recording shows it near the frame's budget.

## The tools

What three.js itself ships:

- `renderer.info`: draw calls, triangles, points and lines of the frame, the geometries and textures held, and the
  compiled programs (`info.programs`). Our `?dev` line shows the first ones.
- `Stats` (examples, `stats.module.js`): fps, frame time and memory in a small panel.
- `renderer.compile` / `compileAsync`: compiles a scene's shaders ahead of time (against hitches).
- No GPU timing for the WebGL renderer out of the box (the newer WebGPU renderer has timestamp queries).

Beside it, all free:

- **Spector.js**: captures one frame and lists every WebGL call, with the state, the program switches, the textures
  and each draw's picture. The best tool for items 2, 7 and 9.
- **stats-gl**: a panel like Stats with CPU and GPU time, where the browser offers WebGL's timer queries (it may not
  on every Mac browser).
- **Chrome DevTools, Performance**: the frames, our code, garbage collection and the GPU's activity on a timeline
  (item 10). **Safari Web Inspector**, the Graphics tab: records a canvas's WebGL calls on the Mac.
- Our own: the `?dev` line, and the draw-counting probe used on 2026-11-27 (to be kept as a console tool if it
  proves useful again).

## Decide before implementing

- The order after item 1: the measurements of B first (they are a few minutes each on the Air), then whatever they
  point to.
- Whether a shadow stand-in and a far-shot figure are made by hand (Blender) or by a tool (gltfpack), and where the
  extra files live.
