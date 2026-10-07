# A `phase` knob on every periodic oscillator, `range` on the vibrato and tremolo LFOs

Status: **DONE 2026-10-06, archived.** Built on branch `oscillator-phase` (on top of `signals-range`), commit
`1207ed50`; review round 1 (blind pair) and round 2 (reviewer-high, clean) applied. The sprudel tremolo `range` is
decided (decision 4: not in sprudel yet); the vibrato's `range` moved to `docs/tasks/pitch-pipeline-into-the-tree.md`;
the shared-modulator defect found in review is `docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`.

## Decided at the start (maintainer, 2026-10-06)

1. **The name is `phase`** (not `phaseShift`): `Ignitor.sine(4, x => x.phase(0.25))`.
2. **The tremolo gets its `range` knob in this task; the vibrato's waits** for
   `docs/tasks/pitch-pipeline-into-the-tree.md`. Today the vibrato is built twice (sprudel's strip
   `VibratoRenderer` and the Ignitor's own `vibrato` node); once it is composed from `Ignitor.sine`, `range` comes with
   it, built once.
3. **Branch** `oscillator-phase` on top of `signals-range`, merged after it.
4. **Sprudel's tremolo gets no `range` for now** (maintainer, 2026-10-06: "Not in sprudel yet"). A decided
   asymmetry: sprudel's `tremolo(depth, rate, shape)` is a flat door with no builder layer for a two-value knob, and
   `classic()` places no range slots, so a pattern's tremolo is always the classic dip. The engine is ready (two
   slots at -1 and 0 render the classic bits); a song that wants a swell reopens it. Recorded in the `TremoloSlots`
   KDoc, `.claude/skills/dsl-design/door-shapes.md` and the sprudel reference.

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
3. **Vibrato and other LFOs** (`docs/tasks/pitch-pipeline-into-the-tree.md`): composing the vibrato from
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

## Decided 2026-10-05 (maintainer): the swing's position is `range`, on every surface

The guitar case (maintainer): "you cannot really vibrato the frequency down when playing a string on the guitar";
a guitar's vibrato goes UP from the fretted note. The concept (first discussed as a `bias` knob, then condensed,
2026-10-05: "we condense the entire bias knob to one range() function on all three dsl") is ONE word, `range`:
- **A signal you build yourself** (Ignitor, Katalyst, sprudel): `x.range(from, to)` lays the signal's swing onto from..to.
  The other words go (`unipolar` / `bipolar` on the Ignitor side, the sprudel twins and helpers): that cleanup is its
  own task, `docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`.
- **The vibrato and tremolo doors** hide their LFO, so `range` is a builder knob that shapes that inner LFO in the same
  -1..1 language, depth staying the musical knob on the door: `vibrato(5, 0.3, x => x.range(0, 1))` swings only upward
  (guitar); the vibrato default is `range(-1, 1)` (today's sound); the tremolo default is `range(-1, 0)` (today's
  tremolo, the gain dipping from 1 to 1 - depth), built so that the default is literally today's computation,
  bit-identical. Raw Motor: no clamp; `range(0, 2)` swings twice the depth upward.
- Per sample: one multiply and one add on the LFO value; a constant range folds per block.
- Not "skew": skew/symmetry in synth language is the horizontal asymmetry (triangle toward saw); not wanted now.

## Open when it starts

- The zero point per shape: what "phase 0" is for each oscillator today (the sine starts at 0 rising; where do the
  trapezoid engine's saw, square and triangle start?). The knob shifts from today's start, whatever it is; record it
  in each builder's KDoc.
- The super oscillators already draw per-voice start phases (or not): the knob shifts all voices together.
- Mutation-checked specs (engine tier): the wrap, the default bit-identity, a constant offset equals a delayed start,
  both doors.

## As built (2026-10-06)

**The zero points (the survey).** Phase 0 is where each shape always started, and every builder's KDoc names it: the
sine at `sin(0)`, rising; the saw and zawtooth at -1, the bottom of the rise (the flyback ends the cycle); the ramp and
zamp at +1, the top of the fall; the square (`Ignitor.square`, the `Pulze` node, and the internal `Square`) at -1, the
foot of its rising edge (rise, high until `duty`, fall, low); the raw pulze (`Ignitor.pulze`, the `RawPulze` node) at
+1, the start of its high plateau (its instant rising edge sits at the wrap); the triangle at -1, its lowest point; the impulse on its spike (the note's first sample). The super
oscillators draw a start phase per voice (`rng.nextDouble()`, or the phase pool's banded entry at note-on); the knob
adds the same fraction to every voice, which leaves the pool's coherence `K` unchanged. The plucks are delay lines
with no accumulator and get no knob; nor do the noises.

**The phase knob.** `phase` is a field on every periodic oscillator node (`Sine`, `Sawtooth`, `Ramp`, `Pulze`,
`RawPulze`, `Square`, `Triangle`, `Zawtooth`, `Zamp`, `Impulse`, the five super nodes), default `Constant(0.0)`, a
builder knob `phase(x)` on all fourteen builders (one function serves both doors). The engine (`PhaseOffset`,
`audio_be/.../ignitor/PhaseOffset.kt`): the literal 0 builds no input, so the default renders as before, bit for bit;
a block-constant input moves the accumulator by its change once per block (the per-sample loops untouched, so a
constant costs one read per block); a signal takes the oscillator's phased loop, which reads the shape at
`accumulator + offset` per sample and advances exactly as the plain loops do. Offsets wrap to `[0, 1)`
(`wrapToUnitCycle`), non-finite reads as 0. The impulse spikes where the shifted phase passes 0 going forward (a
start phase of 0.25 spikes first after three quarters of a cycle; a step back of less than half a cycle spikes
nothing, on both paths: the block path judges a block's first sample by the phased loop's drop rule). On a sine with partial
banks every partial moves by the same fraction of its own cycle (see "Open").

**The tremolo range.** `Tremolo.rangeFrom` / `rangeTo` (default `TREMOLO_RANGE_FROM` / `_TO`, -1 and 0), the script
builder knob `range(from, to)`, the flat Kotlin door `tremolo(rate, depth, shape, rangeFrom, rangeTo)`. Gain
`1 + depth * m`, `m` the LFO laid onto from..to; the depth floor holds. The literal default builds the classic
`range(1 - depth, 1)` itself; the general formula at (-1, 0) through slots renders the same bits (a spec row).

**Proof.** The corpus (18 songs, 256 cycles, raw doubles) is identical before and after. Engagement controls: a
default phase of 0.001 on the sawtooth moves the four saw songs; a node default `rangeTo` of 0.001 moves the two
tremolo songs (Kokon, Drunken Synthlor). Specs: `OscillatorPhaseSpec`, the range rows of `TremoloCompositionSpec`,
`KlangScriptIgnitorPhaseDoorParitySpec`, codec and walk rows; every new row mutation-checked.

**Measured: the Schmetterling thud** (13 sines, the song's ratios and weights, at 210 Hz, rendered through the knob).
Crest = peak over RMS. On the 50 ms hit: all start phases 0, 17.7 dB; the song's sign pattern, 9.1 dB (under this
measure one of the 8192 sign patterns is 0.04 dB calmer); Schroeder `pi k^2 / N`, 10.5 dB as sines, 10.7 dB as
cosines; Schroeder weighted by the partials' power, 10.3 / 9.8 dB; free phases searched on the hit itself
(3000 random sets, then coordinate descent), 8.96 dB, found next to the sign pattern. Steady state (1 s, no
envelope): 11.8, 11.0, 12.8 / 11.5, 10.6 / 10.3 dB. Schroeder's answer is for harmonic tones; the thud's partials are
inharmonic, and its sign pattern is already within 0.15 dB of the best free phases found. No song change.

## Recorded asymmetries

- **The tremolo's `range` is on the Ignitor doors only** (decision 4 above).
- **Sprudel cannot reach an oscillator's `phase`.** The field defaults to the literal 0, not a slot (as `analog` and
  `duty` do), so the built-in sounds read no `phase` slot: the default costs nothing and is bit-identical. The
  per-note route is an instrument author's `x.phase(Ignitor.param("phase", 0))` with the pattern's
  `ignp("phase", ...)`. Whether a `Slots.phase` default is wanted was raised by the coordinator in round 1;
  answered 2026-10-06 by the maintainer: no, sprudel sounds get no `phase` slot.

## Open

- **A modulator shared by oscillators at different pitches** runs twice per block (pre-existing for `duty`, inherited
  by `phase`): parked as `docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`; the reference tells authors to build one LFO per
  layer meanwhile.
- **The partial banks' phase semantics.** As built, every partial moves by the same fraction of its OWN cycle (so 1 is
  0 for every partial and the stack rule holds); a time shift of the summed wave would move a partial at `m f` by
  `m` times as much and break the wrap for the sub-octaves. A maintainer call if a song ever wants the other one. A
  partial that joins mid-note at a phase other than 0 enters with a step (documented in the `phase` and `harmonics`
  KDoc; the bank has no entry ramp to absorb it, raw Motor).
