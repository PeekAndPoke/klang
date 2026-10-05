# A `phase` knob on every periodic oscillator, `bias` on vibrato and tremolo

Status: **queued 2026-09-30 by the maintainer.** Not started.

## The ask (maintainer, 2026-09-30)

"It seems to me that we will need a phaseShift knob on each oscillator, where 0 is no shift, 0.5 is the middle and
1 is a full phase equivalent to 0." And: "Look at the new snare of Schmetterling, here we produce deterministic noise
through sine partials and a phase offset would be even better."

## Why (the consumers)

1. **The tremolo's start points.** Since `docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md` the tremolo is an oscillator, and the
   oscillator's phase decides where a note starts in the tremolo cycle (listening pairs 80 to 83: the triangle now
   starts at its lowest point, the square low). The door's own `phase` knob was dropped on 2026-09-29; the knob moves
   one level down, where every composition gets it. Verdict 2026-10-02 (pairs 80 to 83): the oscillators' own start
   points are accepted for all shapes, so the tremolo uses phase 0 and its sound does not change; the knob makes
   another start point one constant away if a song ever wants it.
2. **Der Schmetterling's snare thud** (`metalSnare`, commit `20d74bb8`): 13 sines at fixed ratios, whose start phases
   today can only be 0 or a half cycle (a flipped sign). The song comment records the search: all 8192 sign patterns,
   the calmest leaving a 12 dB crest. With free start phases the known answer is the Schroeder phases
   (phi_k = pi * k^2 / N, M. R. Schroeder, "Synthesis of low-peak-factor signals and binary sequences with low
   autocorrelation", IEEE Trans. Inf. Theory 16(1), 1970), which flatten a multitone's peak much further. Measure
   before claiming: crest of the thud with the best sign pattern against Schroeder phases. See also
   `docs/tasks/future/sine-inharmonic-partials.md`, where a partials node would want per-partial phases.
3. **Vibrato and other LFOs** (`docs/tasks/future/pitch-pipeline-into-the-tree.md`): composing the vibrato from
   `Ignitor.sine` needs a defined start point, the same question as the tremolo.
4. **Stereo pairs**: two LFOs at 0 and 0.5 are an auto-pan or a stereo tremolo, composed.

## The design (proposed 2026-09-30, to be confirmed when the task starts)

- **Unit**: a fraction of one cycle. 0 is no shift, 0.5 half a cycle, 1 the same as 0. Out-of-range values WRAP
  (1.25 is 0.25, -0.25 is 0.75): no clamp, nothing an author can break (the raw-Motor rule, `/dsl-design` section 6).
- **An offset added every sample, not only a start value**: the knob is an Ignitor input. A constant is exactly the
  start phase and costs nothing (a block-constant input is read once per block); a moving one is phase modulation,
  the DX7 principle, for free. A jumping offset clicks: raw by design.
- **Where**: the periodic oscillators only (sine, saw, ramp, square, pulze, triangle, zawtooth and friends, and the
  super oscillators, where it shifts the whole stack). The noise sources get none.
- **Door shape**: a secondary knob, so it lives on the builder behind the configure lambda:
  `Ignitor.sine(4, x => x.phase(0.25))`, on both doors with a door-parity spec (`/dsl-design` sections 2 to 4). The same
  builders `docs/tasks/future/chip-style-instruments.md` builds on.
- **Name**: `phase` proposed (the common synth word, one word per concept; nothing in the DSL uses it since the
  tremolo's knob went); the maintainer said "phaseShift". Decide at start.
- **Default 0 is bit-identical**: a spec renders every built-in song before and after with the default and compares
  bit for bit.

## Decided 2026-10-05 (maintainer): `bias` on vibrato and tremolo; `unipolar` / `bipolar` go

**`bias`, the swing's position around the note**, a dedicated knob of the LFO doors only (NOT on the oscillators,
NOT on sprudel's normal sounds): from -1 to 1, 0 swings around the center (-1..1), 1 only upward (0..1), -1 only
downward (-1..0), continuous in between (0.5 swings -0.5..1). The maintainer's case: a guitar's vibrato goes UP from
the fretted note ("you cannot really vibrato the frequency down when playing a string on the guitar").
- **Vibrato**: `bias` default 0 (today's sound); a secondary knob, so on the builder behind the configure lambda on
  both doors (sprudel `vibrato(rate, depth, x => x.bias(1))`, the Ignitor vibrato node the same).
- **Tremolo**: `bias` default **-1**, which is today's tremolo (the gain dips from 1 to 1 - depth); built so that
  -1 is literally today's `range(1 - depth, 1)`, bit-identical. 0 also swings above unity, 1 only upward.
- Per sample: one multiply and one add on the LFO value; a constant bias folds per block.
- Same concept in an instrument's own LFO: `Ign.sine(rate).range(lo, hi)` (bias 1 is `.range(0, 1)`, bias 0.5 is
  `.range(-0.5, 1)`). Document `range` with that table.
- Not "skew": skew/symmetry in synth language is the horizontal asymmetry (triangle toward saw); not wanted now.

**`unipolar()` and `bipolar()` are removed** (maintainer: "can be expressed with .range()"): `unipolar()` is exactly
`.range(0, 1)`. `bipolar()` maps a 0..1 source (an envelope, a constant) to -1..1, which `range` cannot say
readably (`range(-3, 1)`); its replacement is `.mul(2).minus(1)` (pending the maintainer's confirmation of that
reading). No song uses either; the uses are `WarmupVocabulary.kt`, the wire nodes `IgnitorDsl.Unipolar` / `Bipolar`,
the runtime `UnipolarIgnitor` / `BipolarIgnitor`, the script doors and their tests. Removed, not deprecated.

## Open when it starts

- The zero point per shape: what "phase 0" is for each oscillator today (the sine starts at 0 rising; where do the
  trapezoid engine's saw, square and triangle start?). The knob shifts from today's start, whatever it is; record it
  in each builder's KDoc.
- The super oscillators already draw per-voice start phases (or not): the knob shifts all voices together.
- Mutation-checked specs (engine tier): the wrap, the default bit-identity, a constant offset equals a delayed start,
  both doors.
