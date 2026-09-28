# What is a click, and what is the right transition time?

Status: **future, raised by the maintainer 2026-09-20** after listening to Katalyst 5c. Not a
defect report: every 5c transition measured clean by the metric we used, and the maintainer
confirmed 5c7 and 5c8 as fine by ear. The question is whether the metric and the constant are
right.

> "How can we define a click mathematically? I think the transition of 50 ms is too long, as the
> human ear can perceive it. So the question is: can we find the optimal transition time for all
> note frequencies? What is the best transition time so we do not get clicks?"

## 1. What we use today, and why it is not an answer

**One constant for everything.** `KNOB_GLIDE_SECONDS = 0.05` in
`audio_bridge/.../constants/BusEffectDefaults.kt` is the fade and glide time of every orbit
transition: the reverb and delay `wet`, the delay's tap crossfade, the body, vowel and EQ swap,
the compressor's blend and its knobs, the orbit fader, the phaser's coefficients, the duck's
weight. It was chosen by the maintainer in conversation (2026-09-19), not measured: "I think 50 ms
is a good timing for the glide."

**One metric for everything.** Every 5c step measured a transition as the peak of a 0.7 ms RMS
window above 8 kHz, relative to the signal, on sources band-limited to 3 kHz, later with a peak
20 ms RMS below 60 Hz beside it. That metric is a proxy for "a step in the waveform puts energy
where the source has none". It is not a model of audibility, and three things it cannot see have
already bitten:

- **A low thump.** The high-frequency half missed it entirely until a reviewer added the
  low-frequency half in 5c-7, and the maintainer still hears a "blub" on the phaser's transitions
  in 5c-9 where both halves measure at their floor (see section 4).
- **A sweep.** A resonance travelling over the transition is not a step at all, so the metric reads
  clean while the ear hears a filter sweep. That is what rejected the resonator morph in 5c-10.
- **Masking.** The metric compares against the signal's own RMS, not against what the ear can hear
  under that signal. The 5c-9 listening material was nearly silent, which is why its thumps stood
  out; in a mix the same numbers may be inaudible.

So the honest statement of where we are: **we have a proxy that catches steps, we have one constant
that was chosen by taste, and both have held up in listening so far.** The question is what the
right answer would look like.

## 2. The question, stated so it can be answered

**(a) What is a click, mathematically?** Candidates, cheapest first:
- the proxy we use (out-of-band energy from a discontinuity), which needs the source's own band
  limit to mean anything;
- the size of the discontinuity in the signal and in its first derivative at the transition
  (a step is C0, a kink is C1), which is what the crossfade laws already reason about;
- a loudness model: the transient's level against the masking threshold of what is playing, which
  is the only one that answers "is it audible" rather than "is it there".

**(b) Is the transition time a function of frequency?** The physical intuition says yes: a
transition is heard as a click when it is short against the period of the content it interrupts,
and as a sweep or a swell when it is long against it. At 50 Hz one period is 20 ms and a 50 ms fade
spans 2.5 periods; at 2 kHz it spans 100. So the same constant is a different thing to a bass note
and to a hi-hat, which is exactly the maintainer's point.

**(c) Is one time right for every kind of transition?** A level fade (a `wet`, a fader), a
coefficient move (a filter cutoff), a bank swap (a material change) and a state release (a
compressor's reduction) fail in different ways. The 5c measurements already show they are not
interchangeable: the same 50 ms that removes a click from a fader is what makes a vowel morph
sweep audibly.

## 3. What an answer would have to produce

- A **definition** of a click that we can compute on a render, that agrees with the ear on the
  cases we already have (the 5c "before" and "after" WAVs are a labelled corpus: the maintainer has
  confirmed which ones click and which do not).
- A **law** for the transition time, either one better constant or a function of the content
  (its fundamental, its level, the kind of transition). If it is a function, it has to be readable
  at the moment the transition starts, which the engine does not do today: the orbit does not know
  the note's frequency.
- A **cost check**: a shorter time means more transitions in flight per second and a steeper ramp;
  a longer one means more smear (the one-pole rule in `docs/plans/knob-glide.md`: a knob retargeted
  every block converges like a one-pole with tau about 49 ms, so a fast-wobbled knob already loses
  modulation depth).
- **Per-effect answers**, if they differ, and then a defensible way to spell more than one
  constant without the rules register growing a table nobody can keep true.

## 4. The adjacent finding that belongs with this work

**The phaser's low "blub" (maintainer, 2026-09-20, listening to 5c-9).** With the high-frequency
click gone, a low-frequency thump is audible at `phaser.wet` transitions and at the other phaser
settings, on material that is nearly silent. The measurements for that step read -46 to -55 dB
below 60 Hz against a floor of -49 to -53, i.e. at the floor of the metric we used, which is
exactly the blind spot section 1 names. It needs its own measurement (what moves below 100 Hz when
the C4 coefficients ramp, and whether it is the ramp or the cascade's state), and it may turn out
to be an argument for a different time on that stage rather than a defect in the law.

## 5. Every effect this would touch

All of these transition on `KNOB_GLIDE_SECONDS` today: the orbit reverb `wet` and size, the delay
`wet`, its tap crossfade and its feedback, body, vowel and the orbit EQ (the swap's fades and the
crossfade between banks), the compressor (its blend with dry, plus threshold, the inverse ratio and
knee), the orbit fader, the phaser (its two C4 coefficients per sample, its breakpoint per block),
the duck (its weight and its depth), and the chain swap itself. The master's own fades are a
separate family and would be asked the same question.
