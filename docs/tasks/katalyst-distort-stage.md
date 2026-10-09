# A `distort` stage on the Katalyst: the clipper and the glue for buses and the master

Status: **built 2026-10-09 on branch `katalyst-distort` (worktree), in review.** Agreed with the maintainer 2026-10-09. The first item out of the production research
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
  - **The stage's latency is the oversampler's group delay:** 4.0 samples at 2x, 5.5 at 4x, 6.25 at 8x (corrected 2026-10-09; first written here as 5.75 and 6.625 from the oversampler's old KDoc)
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

## What was built, and what was found on the way (2026-10-09)

- **The door:** `k.distort(amount, shape = "soft", oversample = 0)` (`KatalystBuilders.kt`), the voice's flat door
  position for position. `amount` takes a number or a `Katalyst.param` slot; `shape` takes a NAME and `oversample`
  an Int, on both doors, because both are fixed with the chain (the compressor's `lookahead` precedent; recorded in
  `.claude/skills/dsl-design/door-shapes.md`). No `wet`, no sprudel door. The default amount is `DISTORT_AMOUNT`
  (0.5, the voice nodes' default; `KatalystDefaultsSyncSpec` keeps them equal).
- **The wire:** `KatalystStageDsl.Distort(amount: IgnitorDsl, shape: Int, oversample: Int)`.
- **The engine:** `KatalystDistortEffect`, two `DistortionCore`s, the compressor's state machine (Off, Engaged,
  Fading; a blend with the dry over `KNOB_GLIDE_SECONDS`), the amount gliding linear in the amount with the drive
  ramped per sample, OFF at an amount that is not finite or at or below 0. `DistortConfig` is the writer's holder
  (the V8 rule). `KatalystLatentEffect` is the new interface the chain sums latency over (the compressor's lookahead
  and this stage's oversampling).
- **Open question 1 answered, measured:** today's oversampler is NOT clean enough for a whole mix. At 48 kHz and 2x a
  clean signal loses 0.7 dB at 10 kHz, 2 dB at 16 kHz and 4 dB at 20 kHz (the linear-interpolation upsampler and the
  15-tap half-band). So the default stays 0 (**question 2 answered**), the author opts in, and a transparent
  oversampler for the master is a follow-up of its own.
- **The group delay, corrected:** derived from the taps and pinned by `OversamplerGroupDelaySpec`, the round trip
  delays by 4.0, 5.5 and 6.25 input samples (`Oversampler.groupDelaySamples`); the KDoc's 5.75 and 6.625 were wrong.
  The stage holds `round()` of it in every state (4, 6, 6 frames), so a fade blends at most half a frame apart.
- **The DC pole, a finding:** the core's DC blocker (0.995) is a first-order high-pass near 35 Hz, about -2.5 dB at
  40 Hz. Harmless per note, wrong on a bus that carries the sub. The core gained a `dcBlockCoefficient` (default
  unchanged, so every voice renders as before) and the stage passes the house stage's 0.999 (near 7 Hz), now one
  named constant, `HOUSE_DC_BLOCK_COEFF`, that `MasterStage` reads too (the same value, bit-identical). Side note for
  the maintainer: every VOICE that uses `distort` loses that much at 40 Hz too, per note (a distorted bass).
- **The core gained `reset()`** for the stage's hard cut; the voice never calls it.

## Review (2026-10-09)

- **Round 1** (opus, code and audio, blind): 2 MAJOR, 5 MINOR, all fixed.
  - MAJOR: an amount glide clicked at both seams with oversampling on. Fixed by `DistortionCore.processRamped`.
  - MAJOR: a chain swap cut an asymmetric shape's DC blocker decay. Fixed by `DcBlocker.holdsEnergy` in `hasTail`.
  - The same swap mechanism may cut body, vowel and phaser: [`chain-swap-cuts-ringing-inserts.md`](chain-swap-cuts-ringing-inserts.md).
- **Round 2** (reviewer-high, two-phase): both reviewers found 1 MAJOR, the same one. The master never asked the
  stage for its tail, because `KatalystChain.declaresTail` counted only reverb and delay, so a stopped playback cut the
  decay. Fixed: `declaresTail` counts the stage, and `MasterOutputChainSpec` has a row for it. The reconcile phase
  confirmed every round-1 fix, and two MINORs were withdrawn.
- **Mutation checks:** 21 mutants, all red after the M11 and M20 rows were added.
- **Escapes filed in the ledger:** two. The process changes are in `audio/ref/katalyst.md`, "Writing a stage
  lifecycle": the three tail askers, and the build-time option multiplying the behaviour rows.

