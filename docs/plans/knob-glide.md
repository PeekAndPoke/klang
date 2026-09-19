# Knob glide: orbit settings change smoothly, never in one step

Decided 2026-09-19 with the maintainer (signal-flow plan section 7, the insert-style sends).
Status: the pilot on the orbit reverb is the first step; this file is where its lessons land.

## 1. Why

An orbit has one owner voice at a time, and two patterns on one orbit take turns owning it. Each
owner change can move every knob of the orbit's chain at once. Today most of them jump, which
clicks (a level) or zips (a coefficient). The rule: every orbit knob GLIDES to its new value over
`KNOB_GLIDE_SECONDS = 0.05` (a constant in `audio_bridge/constants/`, one day perhaps a user knob).

**What a glide is for (maintainer, 2026-09-19): a safety net against clicks and sudden unwanted
sounds, not a sound-proof one.** It does NOT preserve loudness: a linear blend of two different
sounds may dip by about 3 dB in the middle, and that is fine. No equal-power law, no loudness
compensation, no cleverness. Different-sounding voices that must not disturb each other belong on
different orbits; that is the user's call, and the glide does not try to make one orbit behave
like two.

## 2. The rule, per kind of knob

| kind | examples | how it glides | cost |
|---|---|---|---|
| LEVEL | reverb and delay `wet`, body, vowel and phaser mix, the fader, the crossfade between two delay taps | linear ramp PER SAMPLE: start value and step computed once per block, one add per sample in the effect's own loop, on locals | one add per sample |
| COEFFICIENT | reverb size and damping, delay feedback, compressor settings, filter frequencies | linear glide PER BLOCK: the value used for a block moves one step toward the target | nothing per sample; the coefficient math stays at block rate |
| exception: delay TIME | | never glides (a glide bends the echoes' pitch); a change crossfades from the old tap to the new one, which is a LEVEL ramp | as LEVEL |

Why the split: a gain that moves in per-block steps (about 19 steps over 50 ms at 128 frames)
zips audibly on a loud signal; recomputing a coefficient per sample would put `exp` or `tan` in the
hot path. Small per-block steps of a recursive filter's coefficient are ordinary control rate and
do not click.

## 3. The helper

One small plain class, one instance per knob, allocated with the effect (no framework, the sibling
of `Crossfade`):
- LINEAR with an EXACT landing: after the glide it holds the target exactly and does no work, so a
  settled knob is bit-transparent and costs nothing.
- A new target mid-glide restarts from the CURRENT value, so owner flip-flops chain smoothly.
- A non-finite target is substituted before it is stored (the review ledger's cached-config rule):
  a glide can never get stuck on a NaN.
- The FIRST value after construction or reset SNAPS (no glide from a meaningless default), like
  `KatalystGainEffect`'s fresh state. Without that, every song's reverb would open with a 50 ms
  glide from nothing, and every render would change.

## 4. Rollout

1. Pilot: the orbit reverb's size and damping (coefficient knobs). Its `wet` does not exist as an
   orbit knob until 5b-2 makes the reverb an insert; the LEVEL half of the helper is exercised
   there. A sound change for any song whose reverb settings change while it plays; a song with
   constant settings stays bit-identical.
2. 5b-2: the reverb's and the delay's `wet` (LEVEL), the delay's feedback (COEFFICIENT), the tap
   crossfade on a time change.
3. 5c: every other orbit knob, together with the switch-off fades.

## 5. Pilot log: problems met, and how they were solved

(Filled by the pilot. Each entry: the problem, where it showed, the decision, and what the next
effect must do differently.)
