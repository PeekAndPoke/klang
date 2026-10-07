# A non-finite pitch amount freezes the oscillator

Status: **done 2026-10-07** (see "What was done" at the end); the leftover found in review is its own task,
`bugfix-non-finite-pitch-strip-and-signals.md`. Found 2026-09-24 (pre-existing), moved here 2026-09-27 from
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

## What was done (2026-10-07)

What we built: the house `finiteOr` at the six reads in `PitchModFactories.kt`, each with its node's own default, no
clamp. Line numbers on the day: vibrato `rate` and `semitones` (`VibratoModIgnitor`, then `:75-76`), accelerate's
`semitones` (`AccelerateModIgnitor`, `:127`), the pitch envelope's `semitones` (`PitchEnvelopeModIgnitor`, `:209`),
FM's `ratio` and `depth` (`FmModIgnitor`, `:379-380`).

- The defaults have one home: `VIBRATO_RATE_HZ` (5.0), `VIBRATO_SEMITONES` (0.25) and `FM_RATIO` (1.0) moved into
  `audio_bridge/.../constants/PitchModDefaults.kt`, read by the `IgnitorDsl.Vibrato` / `Fm` field defaults and by
  the runtime (values unchanged). The three switch knobs (accelerate's and the pitch envelope's `semitones`, FM's
  `depth`) default to 0, which is "off", and a non-finite one reads as off.
- What a NaN did, checked on the code: a NaN amount, depth or vibrato depth skips its `== 0.0` / `<= 0.0` bypass and
  `safeOut` turns every ratio into 0, a DC hold; a NaN vibrato rate pins the LFO at phase 0 (no vibrato); a NaN FM
  ratio drives a note-pitched modulator at a NaN frequency.
- The rest of the file was re-checked: the envelope times (both envelopes) need no guard (the envelope law reads a
  NaN time as a zero-length stage); FM's `freq` has its `!(x > 0.0)` NaN-guard; the two sustains already had
  `finiteOr`. Two per-sample SIGNAL paths still carry a NaN to the oscillator's phase, both left as they are because a
  per-sample guard there is a hot-path cost decision, not an amount knob: `DeviationToRatioIgnitor` (`pitchMod`)
  passes a NaN signal sample through, and FM's MODULATOR output does too (`safeOut(1.0 + mod * depth / freq)` turns a
  NaN modulator sample into ratio 0, a DC hold; review round 1 measured `Fm(sine, modulator = NaN)` as exact silence).
- Tests: six rows in `PitchModSafetyTest` ("a NaN <knob> reads as unset"), each rendering a voice through
  `buildExciter` with the knob NaN against the same node with the knob left at its default: bit-equal, and peak over
  0.1. Mutations (one build-lock call, `cp` restore, `cmp` verified): removing each `finiteOr` turns exactly its own
  row red, six of six (the FM depth row on its not-silence floor, the other five on the comparison).
- Behaviour change worth knowing (review round 1, B-4, left as is): a `-Infinity` vibrato depth used to take the
  `<= 0.0` bypass (no vibrato, a clean note); now it reads as unset and plays the default vibrato (0.25 semitones at the
  given rate), the documented rule for every non-finite knob. Only arithmetic feeding the knob can produce it.

## Left open, carried to its own task

The NaN class on the sprudel pitch path and the two per-sample signal paths named above (`pitchMod`, FM's modulator
output) are open in [`bugfix-non-finite-pitch-strip-and-signals.md`](bugfix-non-finite-pitch-strip-and-signals.md), so
this task can be archived as done.

