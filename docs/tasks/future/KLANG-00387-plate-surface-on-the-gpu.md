# KLANG-00387: Plate surface on the GPU

> **Status (2026-10-29): NOT SCHEDULED.** A performance follow-up for the visuals, asked for by the maintainer
> (2026-10-29: "would it possible to generate the brushes on the tiles through a gpu shader? Just asking we will not do it
> yet, but if possible as a follow up performance ticket for the visuals").

## Why this exists

The start page's and the visuals' plate (`MotorBackground`) gets its relief from a normal map that the CPU computes
pixel by pixel: about 7 million pixels on a large screen, each with the seams, the tile's tilt and turn, two layers of
brush strokes and the brush's pressure noise. Even tile by tile, from a cache, and with the per-pixel work trimmed
(eb8534eba, c9a7a2f89), a full build takes seconds: on the first visit, on a resize, and for every tile a plate covers
when the visuals switch.

Every pixel of that surface depends only on its own position and a few numbers (the seed, the grid's origin, the
plates' rectangles). No pixel reads another. That is what a fragment shader does well: all pixels at once, in
milliseconds.

## Decide before implementing

- **A, a pass into a texture (recommended):** at build time one shader pass draws the normal map into a
  `WebGLRenderTarget`; the plate's material takes it as its `normalMap`, as now. Lighting, the lamp and the look stay
  as they are. Builds become near instant, so the surface cache (`SurfaceCache`) and the tile-by-tile build can go.
- **B, live in the material:** the normal worked out per fragment every frame, by patching
  `MeshStandardMaterial` (`onBeforeCompile`). No texture, sharp at any size, but a cost every frame and a patch into
  three.js's own shader. Only if A is not enough.
- **The look moves a little, once:** the hash (`hashCell`) and the noise (`PerlinNoise2D`) are ported to GLSL. Float
  maths on the GPU does not match the CPU bit for bit, so the plate looks the same in kind, not pixel for pixel. Agree
  that this is wanted before starting.
- **The title's engraving stays on the canvas** for its text mask (shaders do not draw text); only the relief computed
  from the mask could move to a shader, as a second step.

## The work

1. The surface as GLSL: the grid and its offset rows, the seams' V-grooves, the per-tile tilt, the turned tiles (with
   their neighbour rule), the two stroke layers, the pressure noise, and the raised plates as a small uniform array of
   rectangles (`PlateComposition`'s raised tiles).
2. A render pass into a target the size of today's texture, its result the plate's `normalMap`. The renderer is at
   hand: Kraft's three.js addon gives it in its context (`ThreeJsComponent.kt`).
3. The build path (`startPrepareIfPossible`, `rebuildTextures`) on the pass; the CPU path, the cache and the
   tile-by-tile build removed once the GPU path has proven itself.
4. Fail soft as today: no WebGL2 or a shader that does not compile leaves the plate as `markFailed` leaves it.

## Acceptance

- The start page, the three visuals and a resize show the plate with no build delay to speak of.
- Side by side with the CPU surface, the maintainer finds the look unchanged in kind (strokes, turned tiles, seams,
  plates, the pressure's patches).
- A check that runs where WebGL does (Chrome's software GPU, SwiftShader) renders the plate once and compares a small
  patch with the CPU surface within a tolerance, for as long as both paths exist; scaffolding, removed with the CPU path.

## History

- 2026-10-29: created from the maintainer's question, after the tile-by-tile build and the surface cache.
