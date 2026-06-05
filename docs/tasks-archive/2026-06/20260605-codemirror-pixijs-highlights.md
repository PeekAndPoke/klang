# CodeMirror playback highlights → PixiJS (WebGL) overlay

Last updated: 2026-06-05.

Status: **done, verified.** All code landed, `compileKotlinJs` passes, and the user confirmed in
the app: highlights appear in sync, fade correctly, scroll-track, no gutter overlap, and the
alignment is dialed in (box nudged 3px left / 1px up via the `showHighlight` offset constants).
Performance on the weak machine is "much much better." Replaces the DOM-`<mark>` overlay highlight
renderer with a single PixiJS v8 (WebGL) canvas driven by a Pixi `Ticker`. Scope was the
**CodeMirror script editor only** — the KlangBlocks inline highlights are a separate, lighter
mechanism and were intentionally left untouched.

---

## Goal

Dramatically cut GPU load of the realtime playback highlights on weak graphics hardware.

The old overlay (`CodeMirrorHighlightBuffer`) created/destroyed an absolutely-positioned
`<mark>` per voice event and animated each one via a CSS `@keyframes pulse`. The aim is to
collapse all of that onto **one WebGL canvas, one composite per frame, zero per-event DOM
mutation**.

## Why the old approach is taxing

Two costs, both on the **main thread**:

1. **DOM churn** — `showHighlight`/`removeHighlight` `createElement` + `appendChild` a `<mark>`
   per event and remove it ~`durationMs` later. Each is a style-recalc + layout.
2. **The real killer — paint-bound animation.** `@keyframes pulse` animated `border-color` and
   `background-color`. Those are **paint** properties (not compositor-accelerated), and CSS
   animations on paint properties are **not** promoted to a compositor layer. So the browser
   re-rasterizes every active mark's box on *every* frame for its whole lifetime. On a weak GPU,
   per-frame rasterization is exactly the worst path.

## Expected improvement (estimate — profile to confirm)

PixiJS draws all highlights as GPU-batched quads on **one canvas / one composite per frame**, and
nothing mutates the CodeMirror DOM. The per-frame paint of the editor region disappears.

- **Highlight subsystem itself: ~10–50× cheaper** — from "re-rasterize N boxes + style/layout
  churn" (often several-to-tens of ms/frame on weak hardware in dense passages) to **sub-ms**
  batched GPU draw. High confidence.
- **Whole-app FPS during busy playback:** likely **sub-30 fps jank → steady ~60 fps** *if*
  highlights are the dominant cost (the symptoms suggest they are). **Amdahl-bounded** — if CM
  reflow / audio scheduling / other rendering is also heavy, the app-level gain shrinks even
  though the highlight cost still drops ~10–50×.
- Proof: a CPU-throttled DevTools Performance capture on a dense pattern — the "Painting" track
  should shrink to near-zero for the highlight region.

## Design

Renderer: **PixiJS v8 via the Kraft `addons-pixijs` addon** (`io.peekandpoke.kraft.addons.pixijs`).
`ultra_version` is `0.107.2`, which ships the addon. Registered lazily, same as the existing
`threeJs(lazy = true)`.

### Wiring

- `buildSrc/src/main/java/Deps.kt` — add `addons_pixijs` coordinate const.
- `build.gradle.kts` — add `api(Deps.KotlinLibs.Kraft.addons_pixijs)` next to the other addons.
- `src/jsMain/kotlin/index.kt` — `addons { … pixiJs(lazy = true) }`.
- `src/jsMain/kotlin/comp/KlangCodeEditorComp.kt` — `private val pixiAddon by subscribingTo(addons.pixiJs)`;
  push it into the buffer via `setPixiAddon(pixiAddon)` from both `onMount` and `onUpdate` (the
  addon is lazy → arrives after mount). Destroy happens in the buffer's existing `detach()`.

### Buffer rewrite — `CodeMirrorHighlightBuffer`

**Unchanged** (the whole scheduling brain stays): `scheduleHighlight`, `scheduleForLocation`,
start-time batching, rate-limiting (`minIntervalMs` / `lastHighlightTime`), `maxSimultaneousHighlights`,
`maxHighlightsPerEvent`, `currentSource` filtering, `lineColToPos`, blur-cancel, `document.hasFocus()`
gating, `maxOverdueMs` drop.

**Replaced** (the rendering layer):

- **Pixi app lifecycle.** `setPixiAddon()` + `ensurePixiApp()` lazily create one `Application`
  (`backgroundAlpha = 0`, `antialias = true`, `preference = "webgl"`, `autoDensity = true`,
  `resolution = min(devicePixelRatio, 2)`, `resizeTo = view.dom`). Canvas mounted into `view.dom`
  (`.cm-editor`, the non-scrolling root) at `position:absolute; top:0; left:0; pointer-events:none;
  z-index:5`. `view.dom` is forced to `position:relative` if computed static. `init()` is async
  (`launch { … .await() }`), so highlights scheduled before it resolves simply wait / drop.
- **One Pixi `Ticker` for everything** (replaces both the old rAF `tick()` and the CSS `pulse`).
  The ticker is **stopped while idle** and started when work is queued (mirrors the old
  `frameLoopActive` idle-stop) → an idle editor costs zero rAF. Each tick:
    1. process due `PendingShow` ops → `showHighlight` (create/draw a pooled `Graphics`);
    2. read `scrollLeft/scrollTop` **once**; per active mark offset cached content-relative rect,
       viewport-cull, set `alpha = pulseAlpha(elapsed/duration)`, expire when `elapsed ≥ duration`;
    3. if no pending ops and no active marks → `ticker.stop()`.
- **No per-frame `coordsAtPos`.** Geometry is measured **once** at show time (the existing
  `coordsAtPos` + scroll math, relative to the **canvas's own** `getBoundingClientRect()` — the
  Pixi world origin, so it's immune to any border/padding on the editor root) and stored as
  content-absolute `contentX/contentY/w/h`; each frame just subtracts live scroll. Avoids forced
  reflow on weak machines.
- **Gutter clipping.** The canvas overlays the whole editor (z-index 5, above the sticky
  line-number gutter), so a `clip-path: inset(0 0 0 <gutterRight>px)` is applied to the canvas to
  keep highlights from painting over the gutter when scrolled left. `gutterRight` is recomputed at
  show time (cheap; adapts when the gutter widens, e.g. line 99 → 100) and only re-applied when it
  changes.
- **`Graphics` pooling.** Expired marks are returned to a small pool (capped) instead of destroyed,
  to avoid GC churn during dense playback. Each highlight is one `roundRect().fill().stroke()`,
  drawn once; only `x/y/alpha/visible` change per frame (no per-frame redraw).
- **Fade.** `pulseAlpha(t)` reproduces the old ease-out gold pulse: fill `0xE8B84B`@0.1 + stroke
  `0xFFDC64`@1.0 baked into the Graphics, container `alpha` animated `1.0 → 0.4 → 0.0` on an
  ease-out-cubic timeline.

### CSS cleanup — `src/jsMain/resources/css/klang.css`

Remove the now-dead `.cm-highlight-overlay .cm-highlight-playing`, the legacy `.cm-highlight-playing`,
and the `@keyframes pulse` (grep-confirmed: only this overlay used them).

## Files

| File                                                        | Change                                           |
|-------------------------------------------------------------|--------------------------------------------------|
| `src/jsMain/kotlin/codemirror/CodeMirrorHighlightBuffer.kt` | Rendering layer → PixiJS; scheduling logic kept. |
| `src/jsMain/kotlin/comp/KlangCodeEditorComp.kt`             | Subscribe to addon; feed it to the buffer.       |
| `src/jsMain/kotlin/index.kt`                                | `pixiJs(lazy = true)`.                           |
| `buildSrc/src/main/java/Deps.kt`                            | `addons_pixijs` const.                           |
| `build.gradle.kts`                                          | `api(... addons_pixijs)`.                        |
| `src/jsMain/resources/css/klang.css`                        | Remove dead overlay rules + `@keyframes pulse`.  |

## Verification

- **Build:** `./gradlew compileKotlinJs` — addon resolves, no interop errors.
- **Functional (run app, play a song):** highlights appear in sync, fade with the gold pulse,
  disappear cleanly; horizontal + vertical scroll during playback keeps them aligned; editing /
  switching `currentSource` draws no ghosts; rate-limit, `maxHighlightsPerEvent`, blur-cancels-all,
  modal-pause still hold; multi-line highlight still renders.
- **Performance:** DevTools Performance, CPU-throttled, dense pattern — the old per-frame
  Rasterize/Paint from `border-color` animation is gone; frame budget is one WebGL composite.
- **Regression scope:** KlangBlocks highlights untouched — confirm they still render.
- No automated test covers the overlay (DOM/WebGL); verification is manual.

## Possible later refinements

- Lower the cap on `resolution` (e.g. 1.5) if fill-rate is still tight on the weakest hardware.
