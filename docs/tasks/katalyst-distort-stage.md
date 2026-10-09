# A `distort` stage on the Katalyst: the clipper and the glue for buses and the master

Status: **agreed with the maintainer 2026-10-09, not started.** The first item out of the production research
([`../plans/aaa-production-tricks.md`](../plans/aaa-production-tricks.md), candidate K1, there named `saturate`). It
absorbs [`future/idea-master-saturation.md`](future/idea-master-saturation.md). It needs no routing, so it is neutral
to the Motor design ([`../plans/future/signal-graph-engine.md`](../plans/future/signal-graph-engine.md) §6).

## Why

- **The master:** a soft clipper before the limiter rounds the shortest peaks (the snare's spikes), so the limiter
  works less and the mix sounds denser and punchier at the same -14 LUFS. This is the standard modern loudness move
  ("clip, then limit"). A gentle tube shape gives glue.
- **A bus:** a guitar amp fed by the whole chord, for the power-chord growl that per-note distortion cannot make.

## What we build (version 1)

**One stage, `distort`, the same tool as the voice's.** This was decided in conversation (maintainer, 2026-10-09):
- One word per concept: saturation and distortion are one DSP at different strengths, so the stage is not
  `saturate`.
- A clipper is `distort` with a clipping shape (`soft`, `hard`) and a small `amount`, so there is no `clip` stage.
  `clip` is also already sprudel's alias of `legato`.

- **The door:** `k.distort(amount, shape = "soft", oversample = ...)` on the Katalyst builder, at every position
  (orbit chains and `master(Katalyst(k => ...))`). The names, scales and shapes are the voice's: `amount` is the
  exponential drive `10^(amount * 1.2)` (0.1 is about +2.4 dB, 0.25 about +6 dB, 0.5 about +12 dB), `shape` is the
  16-shape catalogue, and `oversample` is the factor. KlangScript and Kotlin, with a door-parity spec.
- **The wire:** `KatalystStageDsl.Distort` (a `@WireName`, knobs as `IgnitorDsl` like every bus knob). `shape` and
  `oversample` are read at build, as on the voice.
- **The engine:** a Katalyst effect running the existing `DistortionCore` on left and right, each channel with its
  own oversampler and DC blocker.
  - **The template is `KatalystCompressorEffect`** with look-ahead: it keeps running through Off, blends against a
    DELAYED dry when it switches, and reports `latencyFrames` to the chain.
  - **The stage's latency is the oversampler's group delay:** 4.0 samples at 2x, 5.75 at 4x, 6.625 at 8x
    (`Oversampler.kt`). The fractional ones need a decision (round, or a fractional delay on the dry for the fade).
  - **Switching:** on and off without a click, by a blend with the delayed dry over `KNOB_GLIDE_SECONDS`.
    `amount` 0 is not the identity, since the curve still bends at unity drive, so the switch cannot be a glide of
    `amount` to 0.
  - **The gate:** a leaf `amount` at or below 0 is not built (the voice's off value, `audio/ref/off-values.md`).
  - **Knob changes:** a changed `amount` glides (`KnobGlide`).
- **No sprudel pattern door.** `.distort()` on a note stays the voice's door. A bus `distort` is reached through an
  authored chain (`katalyst(...)`, `master(...)`). What a bus door on a note means is the open Motor question.

**Not in version 1:**
- **`wet`:** later, on the voice and the bus at once, when a song asks. The door rule puts `wet` first, which would
  change the meaning of every existing `.distort(x)`, so it is a `/dsl-design` decision of its own.
- **A band** (saturate only above or below a frequency: the exciter, the bass harmonics on any bass).
- **A "straight until a threshold, then round" shape** for a fully transparent clipper: one appended catalogue entry,
  only if `soft` does not convince by ear.

## Open, to settle during the step

1. **Is the oversampler clean enough on a whole mix?** Its KDoc calls it "cheap and cheerful", fine for a distorted
   voice, but on a master at a small `amount` it also touches the clean part (the linear-interpolation upsampler).
   Measure on a finished mix before deciding the default. The alternative is antiderivative anti-aliasing for the
   soft shapes (no resampling, no latency).
2. **The default `oversample` on a bus:** 0 like the voice (parity), or 2 (a full mix aliases sooner). Decide with
   measurement 1.

## Proof

- Specs mutation-checked (the engine tier), the review loop until a clean round.
- The identity: a chain without the stage renders bit for bit as before.
- **By ear:** Kokon and Der Schmetterling with `.distort(amount = 0.15)` (shape `soft`) before the master limiter,
  against the current master, at the same -14 LUFS.
