# Flanger and chorus

Status: **future, feature, not started.** Extracted 2026-09-27 from `audio-pipeline-open-topics.md`
(archived as `docs/tasks-archive/2026-09/20260927-audio-pipeline-open-topics.md`). Theory and a
sketch of both: `docs/tasks-archive/2026-03/20260323-modulated-delay-line-effects.md`, sections 1
and 2 (section 3, Karplus-Strong, shipped as `pluck` and `superpluck`).

## The idea

Both are a short delay line whose delay time is swept by an LFO and mixed back with the dry signal:

- **Flanger:** about 1 to 10 ms, with feedback, one tap; the comb sweeps audibly.
- **Chorus:** about 10 to 30 ms, little or no feedback, often two or three taps at different LFO
  phases; the pitch wobble thickens instead of combing.

`effects/DelayLine.kt` already reads at fractional positions, so the core is a modulated read on the
existing line, not a new buffer type.

## To decide when it starts

- **Where it lives:** voice (Ignitor), orbit (Katalyst), or both. One DSP core wrapped per host, the
  factoring rule of `docs/tasks/oversampling-regions.md` §1(b).
- **Door shape** per `/dsl-design` §2: `wet` first, the musical knobs (rate, depth) on the door, the
  rest behind `configure`. One word per concept: check that `chorus` does not collide with the unison
  "chorusing" wording on the super oscillators.
- **Stereo:** a chorus is usually stereo (opposite LFO phases per channel); the voice path is mono
  until pan.
