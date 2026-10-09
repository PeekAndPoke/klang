# Master saturation: glue and warmth on the final mix

Status: **archived 2026-10-10: the saturator half was built as the Katalyst `distort` stage; the rest is homed below.** Was: future, idea, not designed. Created 2026-09-27 (maintainer) from the March plan
`docs/tasks-archive/2026-03/20260323-klang-audio-master-configuration.md`, which was carried in
`audio-pipeline-open-topics.md` §2 until that file was retired
(`docs/tasks-archive/2026-09/20260927-audio-pipeline-open-topics.md`).

## The idea

A mastering stage on the summed mix that makes it sound "glued" and warm rather than merely limited.
Two parts, in this order:

1. **A glue compressor**: gentle, program-level compression (the March presets: about 4:1, soft
   knee around 12 dB, attack around 30 ms). It controls the dynamics before the saturator.
2. **A soft saturator** with two knobs:
   - `drive`: pushes the signal into the curve. The output is scaled by `1 / drive`, so more drive
     means more harmonics, not more level.
   - `bias`: offsets the signal before the curve and removes the offset after it, making the curve
     asymmetric. 0 is symmetric (odd harmonics, "tape"); above 0 adds even harmonics ("tube").

The March plan also sketched two presets: **Transparent** (limiter-like, no drive) and **Analog
Warmth** (4:1, soft knee, drive 1.2, bias 0.15). The curve it proposed was the rational Pade
approximation `x (27 + x^2) / (27 + 9 x^2)`, clamped at |x| > 3.

## What exists today

- The master is a Katalyst chain at the output (phase 3 step 12, 2026-09-28; the Master DSL is gone):
  `master(Katalyst(k => ...))` takes every Katalyst stage, `gain`, `limiter`, `compressor`, `eq`, `reverb`,
  `delay`, `phaser`, `body`, `vowel` (`duck` is inert there). No saturator stage. Follow-ups:
  `docs/tasks/master-dsl-followups.md`. A saturator would be a new Katalyst stage, so it would work on an
  orbit too.
- The engine has the parts: `effects/Compressor.kt` (the orbit compressor and the master limiter
  share it) and the waveshapers in `ShapingFuncs.kt` / `DistortionCore.kt`, several of them
  asymmetric already (`DistortionShape`).
- The March plan put the chain inside `KlangAudioRenderer`'s output loop. That class is now only the
  offline and benchmark renderer; the realtime path does not go through it, and the house output stage
  is `MasterStage` (DC blockers, limiter, clip). Only the idea and the presets carry over; it would be
  built as authored master stages.

## To decide when it is picked up

- **Is it wanted at all.** `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` ("Settled design decisions", the power-amp bullet) keeps saturation
  on the voice by default, because shared clipping on a bus makes voices intermodulate ("chords eat
  each other"). On the master that cross-voice colour is the point of the effect, but it is a
  different sound, not a cheaper one. Listen before building: a by-ear A/B on a finished song with a
  prototype chain.
- **Shape of the surface**, per `/dsl-design` §2: probably a `compressor` stage and a `saturate` (or
  `drive`) stage on the master, each a door with `wet` first where it applies, the secondary knobs
  behind `configure`. One word per concept: check the names against the voice `distort` and the
  orbit `compressor` so the same word means the same thing on every surface (parameter parity).
- **Presets or knobs.** The March plan's presets are a product decision (one-click "warm master")
  on top of the stages, not a replacement for them.
- **Oversampling.** A saturator on the full mix aliases; it belongs in a region once
  `docs/tasks/oversampling-regions.md` lands (that task already decides whether the master gets
  regions).
- **Where it sits against the house limiter.** The final safety limiter stays on the summed mix in
  `MasterStage` (decision of 2026-08-04, `docs/tasks-archive/2026-09/20260927-master-limiter-lookahead.md`
  §4); an authored saturator runs before it.

## Closed (2026-10-10)

What the idea asked for, and where each part went:

| part | now |
|---|---|
| a soft saturator on the master, `drive` and `bias` | BUILT as the Katalyst `distort` stage (`docs/tasks-archive/2026-10/20261009-katalyst-distort-stage.md`): the voice's law at the bus position, `k.distort(amount, shape, oversample)`. The `bias` is covered by the asymmetric shapes (`tube`, `asym`, `diode`), which add the even harmonics |
| "is it wanted at all; listen first" | ANSWERED by the maintainer's Kokon pair (2026-10-09, `distort(0.15)` before the limiter, loudness-matched): "the glueing effect is real" |
| the shape of the surface | DECIDED: `distort`, one word per concept with the voice; no `saturate` |
| oversampling a full mix | ANSWERED by measurement: today's oversampler dulls a whole mix (at 2x about -2 dB at 16 kHz), so the default is 0 and a transparent oversampler for buses is a follow-up, recorded in `docs/tasks/oversampling-regions.md` |
| where it sits against the house limiter | as written: an authored stage runs before `MasterStage` |
| a glue compressor before the saturator | the `compressor` stage already runs on the master; what glue still needs (a low-cut on the detector, so the bass does not pump it, and a `wet` for parallel use) is candidate K5 of `docs/plans/aaa-production-tricks.md` |
| the output scaled by `1 / drive` (more drive, not more level) | NOT built: a `gain` after the stage does it by hand. Reopen if a song wants it |
| the presets ("Transparent", "Analog Warmth") | NOT built: a product decision on top of the stages, as the idea itself said |

