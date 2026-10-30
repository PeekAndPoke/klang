# KLANG-00392: The scope radiates: a blurred afterglow in place of the glow

> **Status (2026-10-30): IN PROGRESS.** Asked for by the maintainer, 2026-10-30

## Why this exists

The maintainer (2026-10-30): "About the glow in the oscillator. I would remove the "generic" glow and replace it with
something different. in the code in the osci we overpaint the current contents with 90% black, leading to a fadeout
effect. Let us do this plus blurring the current content on every render step. This should then look as if the
waveform radiates (i hope). lets try"

The glow (`ScopeGlow`, KLANG-00389's sweep, d7c5f5d64 to cfa49f8f5) is a radial gradient behind the scope, brighter with
the low end's energy: the same shape whatever plays. The scope's own afterglow keeps a little of the last frames.

## Decide before implementing

- The blur in the canvas (`ctx.filter = "blur(...)"`): Chrome and Firefox have it; where a browser has none the
  afterglow just fades, as before.
- More of the last frame kept than today, so the blurred trails can spread: tuned by eye.
- `ScopeGlow` and its driver (`MotorReadings.signal`, `GlowFollower`, `glowEnergy`, their spec) go with it: an
  experiment, git keeps them.

## The work

1. Each frame the scope copies what it shows, clears, draws the copy back blurred and faded, then the new trace.
2. `ScopeGlow` out of the Motor's plate and the code page's Motor; the component and its driver deleted.

## Acceptance

- The trace seems to radiate: the last frames spread and fade round it.
- No measurable cost on a frame in a trace of Klang Amp rain (the blur runs on the GPU in Chrome).

## History

- 2026-10-30: created.
- 2026-10-30: built: each frame the last one back blurred by 1 px and at 80 %, then the new trace; `ScopeGlow` and its driver deleted. For the maintainer's eye.
- 2026-10-30: smoke puffs: each frame the last one drawn back 1.5 CSS px bigger on every side ("draw the previous state
  a bit bigger, so that it appears like "smoke puffs""); then the smoke on a canvas of its own under the trace, kept
  longer (0.94 a frame) and shown at 0.4, the trace's canvas clear each frame ("a second canvas for the "smoke" ... keep
  the "smoke" at a low opacity so that it does not smear the actual osci").
- 2026-10-30: it rises (0.6 px a frame); out of its place: `Oscilloscope.SmokeRoom`, how far the smoke may go past the
  scope, over the Motor's gauges on the code page and out of the visuals' hole (the hole, the scope and the glass as
  layers, only the hole and the glass cut to its shape); it grows round the scope's middle, not its canvas's, which
  with more room above had made it fall.
