# A non-finite pitch amount freezes the oscillator

Status: **open, correctness, small.** Found 2026-09-24 (pre-existing), moved here 2026-09-27 from
`ignitor-dsl-open-items.md` (archived as `docs/tasks-archive/2026-09/20260927-ignitor-dsl-open-items.md`).
Re-checked against the tree on 2026-09-27: still open.

## The bug

Phase 3 step 3d(i) gave the pitch envelope's sustain and the FM index envelope's sustain the house
`finiteOr` substitution. The amounts next to them read raw, all in `audio_be/src/commonMain/kotlin/ignitor/PitchModFactories.kt`:

- the pitch envelope's `semitones` (`PitchEnvelopeModIgnitor`, `:209`), the case the finding named
- FM's `ratio` and `depth` (`FmModIgnitor`, `:379-380`), the case the finding named
- the same shape, found on the re-check: vibrato's `rate` and `semitones` (`VibratoModIgnitor`,
  `:75-76`) and accelerate's `semitones` (`AccelerateModIgnitor`, `:127`)

A NaN passes the `== 0.0` bypass, the ratio becomes NaN, and `safeOut` turns it into 0, so the
oscillator's phase stops and the note is a DC hold: silent failure, no error. Slots are guarded at
build, so only arithmetic feeding the knob can produce the NaN today.

## Fix

The house `finiteOr` at each read, with the node's own default (the value the knob has when it is not
set), the same rule the two sustains already follow. No clamp: every finite value passes raw (the
Motor stays raw).

## Tests

One NaN row per knob (pitch envelope `semitones`, FM `ratio` and `depth`, vibrato `rate` and
`semitones`, accelerate `semitones`): the voice keeps sounding and equals the default-knob render. Mutation-check each row by removing its
`finiteOr` (mandatory tier: `audio_be`).
