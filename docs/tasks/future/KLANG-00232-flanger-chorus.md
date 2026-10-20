# KLANG-00232: Flanger and chorus

> **Status (2026-10-20): IDEA.**

Status: **future, feature, not started.** Extracted 2026-09-27 from KLANG-00218 (audio pipeline open topics)
(archived as KLANG-00218 (audio pipeline open topics)). Theory and a
sketch of both: KLANG-00074 (modulated delay line effects), sections 1
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
  factoring rule of KLANG-00214 (oversampling regions) §1(b).
- **Door shape** per `/dsl-design` §2: `wet` first, the musical knobs (rate, depth) on the door, the
  rest behind `configure`. One word per concept: check that `chorus` does not collide with the unison
  "chorusing" wording on the super oscillators.
- **Stereo:** a chorus is usually stereo (opposite LFO phases per channel); the voice path is mono
  until pan.

## History

- 2026-10-20: numbered KLANG-00232 (was `docs/tasks/future/flanger-chorus.md`).
