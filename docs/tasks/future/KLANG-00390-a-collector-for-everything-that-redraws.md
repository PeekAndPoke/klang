# KLANG-00390: A collector for everything that redraws

> **Status (2026-10-29): NOT SCHEDULED.** Asked for by the maintainer (2026-10-29, on measuring the GPU load in the
> page: "we would need a general "collector" for everything that redraws").

## Why this exists

The performance work on the visuals (2026-10-29) needed three traces from the maintainer's Chrome DevTools to learn
what costs: the domes' repaints, the gauges drawn whole every frame, the page under a visual still drawing, the rain
reading its size every frame. None of it showed in the app. The Motor window's graphs (KLANG-00385) show the audio
engine's load and the song's work, but nothing of the drawing.

A page cannot read the GPU's whole load. It can measure:

- **its own WebGL drawing on the GPU:** timer queries (`EXT_disjoint_timer_query_webgl2`) give a draw's GPU time a frame
  or two later. Chrome on the desktop offers them, with reduced precision; Firefox and Safari do not.
- **its own work per frame on the main thread:** the time each redraw takes, measured around it.
- **whether the frames keep up:** the frame rate and the late frames from the animation frames' timing; in Chrome the
  Long Animation Frames API (`PerformanceObserver`, `long-animation-frame`) breaks a slow frame into script, style and
  layout, and render.

Not measurable: the browser's own GPU work (compositing, rastering the page, 2D canvases) and other tabs.

## The aim

One **collector** that every part that redraws goes through: the plate (three.js), the scope, the spectrum, the rain,
the glass domes' highlights, the gauges' needles, the editor's highlight overlay. It knows what redraws and how often,
measures what can be measured, and posts it to the diagnostics as metrics of its own (KLANG-00385: metrics belong to
their owner, the collector owns these):

- the frame rate, and the share of late frames;
- per part: its main-thread time per frame, and for WebGL parts their GPU time per frame where timer queries exist;
- the sum per frame against the frame's budget.

A new category in the Motor window ("Frames"), or one per kind; the parts that rest (a page under a visual) show as
resting.

## Decide before implementing

- **Together with the scheduler (KLANG-00388):** a part that redraws on the one animation frame scheduler is already
  known to it; the collector may be the scheduler's measuring side rather than a second registry. Decide with that
  ticket.
- **Cost of measuring:** two timestamps per part per frame are cheap; a timer query per WebGL draw is cheap, but its
  result arrives late and may be unavailable (`GPU_DISJOINT`). Measuring is switched off while no Motor window shows the
  graphs, if that matters.
- **Where it lives:** with the Spine (KLANG-00389), as data under the UI, the parts reporting to it.

## The work

1. The collector, with the frame rate and the late frames.
2. The parts report: their redraw time; the plate its GPU time through timer queries.
3. The Motor window's graph for it; the parts' names in its legend.

## Acceptance

- With Klang Amp rain playing, the Motor window shows the frame rate, each part's share of the frame, and the plate's
  GPU time in Chrome; a part that rests shows as resting.
- What the maintainer's traces of 2026-10-29 found by hand would have shown in the graphs.

## History

- 2026-10-29: created from the maintainer's question about sampling the GPU load, after three traces of Klang Amp rain
  (the Motor's pause 14964a0f2, the gauges d329990b9, the Spine's lamp and the domes f378a6d63, the cheap wins
  d93b1e695).
