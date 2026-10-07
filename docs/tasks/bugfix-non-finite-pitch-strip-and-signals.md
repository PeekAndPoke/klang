# A non-finite pitch amount on the sprudel strip, and the per-sample NaN signal paths

Status: **open, correctness, small.** Carved out 2026-10-07 from
[`20261007-bugfix-ignitor-non-finite-pitch-amount.md`](../tasks-archive/2026-10/20261007-bugfix-ignitor-non-finite-pitch-amount.md) (done for the Ignitor door:
`finiteOr` on vibrato, accelerate, pitch envelope and FM amounts in `PitchModFactories.kt`), where review round 1
found these. Not started.

## 1. The sprudel pitch path reads four amounts raw

The strip's pitch stages, built from the wire fields in `VoiceFactory` (`audio_be/.../voices/VoiceFactory.kt`), read
four of the amounts the Ignitor door now guards, and sprudel can deliver a NaN through a string atom (`"NaN"`,
`"Infinity"` both parse):

- `accelerate`: a NaN passes `accelerate.semitones != 0.0` (`PitchPipelineBuilder.kt:40`) and `AccelerateRenderer`
  writes NaN ratios;
- `vib` (the vibrato rate): NaN with a positive depth makes `VibratoRenderer`'s phase NaN;
- `fmh` and `fmEnv`: a NaN builds `Voice.Fm` and passes `fm.depth != 0.0`.

Only `pEnv` and `pSustain` are guarded there today. Fix: the same shape (`takeIf { it.isFinite() }` with the default,
at the read in `VoiceFactory`; the defaults already point at `PitchModDefaults`, `VIBRATO_RATE_HZ` and `FM_RATIO`),
with one NaN row per field, each mutation-checked (mandatory tier: `audio_be`).

## 2. Two per-sample signal paths carry a NaN to the oscillator's phase (decide, do not just guard)

- `DeviationToRatioIgnitor` (`pitchMod`) passes a NaN sample of the user's signal through.
- FM's MODULATOR output: `safeOut(1.0 + mod * depth / freq)` turns a NaN modulator sample into ratio 0, a DC hold
  (review round 1 measured `Fm(sine, modulator = NaN)` as exact silence).

These are signals, not amount knobs, and a per-sample guard is a hot-path cost decision (the `DelayLine` precedent: a
per-sample `isFinite` cost about +30 %). Decide whether either needs one, and measure it first.
