# A non-finite pitch amount on the sprudel strip, and the per-sample NaN signal paths

Status: **open, correctness, small.** Section 1 closed 2026-10-10 (pitch pipeline step 4); section 2 open. Carved out 2026-10-07 from
[`20261007-bugfix-ignitor-non-finite-pitch-amount.md`](../tasks-archive/2026-10/20261007-bugfix-ignitor-non-finite-pitch-amount.md) (done for the Ignitor door:
`finiteOr` on vibrato, accelerate, pitch envelope and FM amounts in `PitchModFactories.kt`), where review round 1
found these. Section 2 not started.

## 1. The sprudel pitch path read four amounts raw (CLOSED 2026-10-10, pitch pipeline step 4)

The strip's pitch stages, built from the wire fields in `VoiceFactory`, read four amounts the Ignitor door guards, and
sprudel can deliver a NaN through a string atom (`"NaN"`, `"Infinity"` both parse). The strip is gone (pitch pipeline
steps 1 to 4) and every door is a `classic()` stage now: sprudel drops a non-finite value at the wire boundary
(`classicSlotParams`), the `Param` leaf reads a non-finite override as unset (the slot's default), and the nodes keep
their own `finiteOr`.

- `accelerate`: dissolved in step 3 (off when non-finite).
- `vib` (the vibrato rate): dissolved in step 2 (the default 5 Hz).
- `fm` (step 4): every `fm.*` slot reads a non-finite value as its default: `fm.depth` 0 (no FM), `fm.ratio` 1,
  `fm.attack` / `fm.decay` / `fm.release` 0, `fm.sustain` 1. What HEAD's strip did, measured on the full engine
  (`docs/tasks/pitch-pipeline-into-the-tree.md`, step 4 record): a non-finite depth silenced the whole voice
  (every frame 0), a non-finite ratio played the bare carrier (no FM), a non-finite sustain silenced the voice from the
  decay on, a +Infinity attack never ended (no FM), a +Infinity decay never ended; NaN and -Infinity times were
  zero-length stages. No NaN sample reached the output on either side.

Guards, one NaN row per `fm.*` slot (NaN, +Infinity, -Infinity each), mutation-checked (mandatory tier): `ClassicFmSpec`
("depth 0 is the bare voice, and so is an unwritten or a non-finite depth", "a non-finite ratio, attack, decay, sustain
or release in the bag reads as the slot's default"; red when the `Param` leaf passes a non-finite override through)
and `ClassicSlotParamsSpec` (sprudel drops a NaN depth; red when the boundary keeps a non-finite value). A non-finite
depth stays green under the first mutant: the fm gate reads it as off too.

## 2. Three per-sample signal paths carry a NaN to the oscillator's phase (decide, do not just guard)

- `DeviationToRatioIgnitor` (`pitchMod`) passes a NaN sample of the user's signal through.
- FM's MODULATOR output: `safeOut(1.0 + mod * depth / freq)` turns a NaN modulator sample into ratio 0, a DC hold
  (review round 1 measured `Fm(sine, modulator = NaN)` as exact silence).
- `SemitonesToRatioIgnitor` (`pitchModSemitones`, pitch pipeline 7a, 2026-10-10): `safeOut(fastExp2(mod / 12))`
  turns a NaN or -Infinity SAMPLE of a signal mod into ratio 0 (a saw renders one constant value, a DC), as FM's
  modulator does, and +Infinity into `SAFE_MAX`; pinned by `PitchModSemitonesSpec`. A literal (leaf) non-finite mod
  never reaches it: the build gate reads it as off, the bare voice (review round 1).
- The composed vibrato (pitch pipeline 7b) reaches that path with a +Infinity depth sample (unguarded, finite, kept
  with this section by review round 1); its edge rules: the `vibrato` row of `audio/ref/off-values.md`. Since 7c a
  non-finite SIGNAL range bound reaches it too (a NaN bound or an infinite `from` reads as 1.0, a `to` of +Infinity
  as `SAFE_MAX`, of -Infinity as 0; the same row).

These are signals, not amount knobs, and a per-sample guard is a hot-path cost decision (the `DelayLine` precedent: a
per-sample `isFinite` cost about +30 %). Decide whether any needs one, and measure it first.
