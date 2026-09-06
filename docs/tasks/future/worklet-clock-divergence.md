# Worklet clock divergence (frame time vs wall time)

> **Status: deferred, no plan.** Restored 2026-09-06 from session memory during the memory
> housekeeping. Related: `midi-latency-optimizations.md` (Bluetooth latency probe idea).

The two `KlangTime` clocks are derived differently and can drift apart:

- **Frontend** `KlangTime` (`MainThreadTimeSource`) is `Date.now()`-anchored: true wall clock.
- **Backend** `KlangTime` in the browser AudioWorklet (`AudioWorkletTimeSource`) is
  `baseTimeMs + (currentFrame - startFrame) / sampleRate`: frame-derived. It only advances when
  the worklet actually renders.

If the worklet sleeps or throttles (background tab, audio glitch, OS audio suspension), its frame
count advances slower than wall time, so `backendNowMs` falls behind and the FE clock runs away.
This is the clock the editor highlights ride on.

`BackendClockSync` (the FE mirror of `BackendClock`, fed by Diagnostics) corrects the FE/BE offset
via an EMA of about 1 s with a snap above 500 ms. It absorbs big jumps and slow drift, but a
worklet that chronically renders slightly slow leaves a persistent offset the EMA keeps chasing.

**Why it matters:** frame-count time is the correct clock for sample-accurate scheduling and the
wrong clock for wall-aligned UI highlights. That tension is the root.

**Ideas for later:** have the worklet also read `Date.now()` and report frame-time-vs-wall
divergence so the FE can detect and compensate it directly; or periodically re-anchor the
worklet's `baseTimeMs` to `Date.now()`.
