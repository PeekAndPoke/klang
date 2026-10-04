# The pitch pipeline moves into the Ignitor tree

Status: **future, not planned.** Named "its own later item" by the phase 3 spike (2026-09-20); opened as a file
2026-09-28 when the phase 3 record was archived (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`,
section 5, first bullet). Plan context: `docs/plans/signal-flow-redesign.md` section 5.

## What it is

Phase 3 made every voice stage an Ignitor tree node, except the pitch modulations. Sprudel's vibrato,
`accelerate`, `penv` and `fm` still run outside the tree: `buildPitchPipeline`
(`audio_be/.../voices/strip/pitch/PitchPipelineBuilder.kt`) builds `VibratoRenderer`, `AccelerateRenderer`,
`PitchEnvelopeRenderer` and `FmRenderer`, which write `BlockContext.freqModBuffer`; `IgniteRenderer` hands it to the
tree as `phaseMod` (`IgniteContext`), where a tree's own pitch modulations compose with it. So their wire fields
stayed (`vibrato`, `vibratoMod`, `accelerate`, `pAttack`, `pDecay`, `pSustain`, `pRelease`, `pEnv` and the three `p*Curve` fields, `fmh` to `fmEnv` in
`audio_bridge/.../VoiceData.kt`).

## Why it is open

Phase 3 kept the pitch path untouched on purpose (the spike verified the separation is real). Moving it is the last
step to "the tree is the whole instrument" and would let the pitch doors become `classic()` slots like the rest.

## What is known

- The shared cores exist: the pitch envelope already runs on `EnvelopeCore` through the mapping it shares with the
  Ignitor node (phase 3 step 5b c1), and the Ignitor has its own `vibrato`, `pitchEnvelope` and `fm` nodes.
- The package `voices/strip/` kept its name after step 9 "for now" (record section 9, step 9's decisions); it now
  holds `BlockContext`, `BlockRenderer`, `IgniteRenderer`, the pitch renderers and `SendRenderer`. This task is the
  natural moment to rename or dissolve it.

## The work

Decide which classic stages the pitch doors become (order against the existing `classic()` stages, and whether the
tree's own pitch nodes and the classic ones compose as today), then move one door at a time with a render-identity
proof per door, and cut the wire fields when the last one moves.

A second step, noted 2026-10-02 (coordinator, in the conversation about reusing primitives after the tremolo): once
the strip is gone, the tree's own `Vibrato` and `PitchEnvelope` nodes could be composed from building blocks, the
way the tremolo was (`docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md`): one pitch primitive in
semitones (ratio `2^(x/12)`), fed by `Ignitor.sine(rate)` times the depth (analog 0) for the vibrato and by an
envelope times the amount for the pitch envelope; then `VibratoModIgnitor` and `pitchEnvelopeModIgnitor` go, the
doors stay. Open before building: the start phase (needs `docs/tasks/oscillator-phase-knob.md`), whether accelerate
(progress over the note's length) needs a new building block or stays, FM last (modulator at a multiple of the note,
depth in Hz, its own envelope), sample voices, and a listening pair wherever the composed form is not bit-identical.
