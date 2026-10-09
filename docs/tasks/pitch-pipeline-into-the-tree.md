# The pitch pipeline moves into the Ignitor tree

Status: **V1, high priority (maintainer, 2026-10-07).** Next after the voice lifecycle (`../tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`, done) and
`code-style-named-args-pass.md`. Was: future, not planned. Named "its own later item" by the phase 3 spike (2026-09-20); opened as a file
2026-09-28 when the phase 3 record was archived (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`,
section 5, first bullet). Plan context: `docs/plans/signal-flow-redesign.md` section 5.
**Planned 2026-10-07** (design worker, read-only on code): "The plan" below, six commits (steps 0 to 5) plus the
composition block; eleven decisions for the maintainer in plan section 8.

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
doors stay. The vibrato's `range` knob lands here (maintainer, 2026-10-06, deferred from the phase knob so it is
built once): a builder knob that places the inner LFO's swing in the -1..1 language of the Ignitor `range`, default
`range(-1, 1)` (today's sound, bit-identical), `range(0, 1)` for a guitar's upward-only vibrato, no clamp; the
tremolo's `range` (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`) is the model. Open before building:
the start phase (the `phase` knob exists since 2026-10-06, same archive file), whether accelerate
(progress over the note's length) needs a new building block or stays, FM last (modulator at a multiple of the note,
depth in Hz, its own envelope), sample voices, and a listening pair wherever the composed form is not bit-identical.

## The plan (2026-10-07)

Read for it: `IgnitorDslClassic.kt`, `IgnitorDslRuntime.kt` (the pitch-mod arms, `combineMods`, the gate),
`PitchModFactories.kt`, `ModApplyingIgnitor`, `ModBlockingIgnitor`, the four strip renderers,
`PitchPipelineBuilder`, `EnvelopeCalc`, `IgniteRenderer`, `IgniteContext`, `VoiceFactory`, `VoiceData`, sprudel's
`_classic_slot_params.kt` and the pitch doors, the block-framing plan (P4, E1, E11, W13), the NaN task, the
shared-modulator record (`../tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`) and the realtime note-off
record. Every claim below was checked against the code on `engine-pass-1` that day.

### 1. How the strip and the tree compose today

- **The strip writes one ratio buffer per voice.** Vibrato, accelerate, pitch envelope and FM run in that order; the
  first one WRITES `BlockContext.freqModBuffer`, every later one MULTIPLIES into it, so the voice's ratio is
  `((V * A) * P) * F` (left to right, ratio space, 1.0 = no change). `IgniteRenderer` hands the buffer to the tree as
  `IgniteContext.phaseMod`, for the WHOLE tree, at the root.
- **The tree's own pitch nodes do not wrap; they bubble.** The `Vibrato`, `Accelerate`, `PitchEnvelope`, `PitchMod`
  and `Fm` arms of `buildIgnitor` build a ratio-space mod and pass it DOWN as `accumulatedMod`
  (`combineMods(existing, newMod)` = `existing * newMod`, outer node first, behind one per-block memo). Every pitched
  source applies it through `ModApplyingIgnitor`, which computes `ratio[i] = treeMod[i] * ctx.phaseMod[i]` (the
  strip's buffer when there is one) and sets that as `ctx.phaseMod` for the source's own `generate`.
- **So the composition is multiplicative in ratio space, applied per pitched source**, as
  `(own1 * own2 * ...) * strip`. Floating-point multiplication is commutative bit for bit, not associative, so the
  ORDER of two factors never matters and the GROUPING of three or more does.
- **Where the strip reaches that the tree does not.** The strip's `phaseMod` sits at the root, so it also bends every
  musically pitched oscillator that renders OUTSIDE a source's `ModApplyingIgnitor` scope: an `fm` node's MODULATOR
  (the arm builds it with no `accumulatedMod`, and `ModApplyingIgnitor` renders the mod chain before it swaps the
  context), and a musical oscillator in a parameter position of a non-source node (an audio-rate `lowpass(freq =
  Ignitor.sine()...)`). The tree's own pitch nodes never bent those. Absolute-frequency sources are shielded on both
  paths by `ModBlockingIgnitor` (W13), noise ignores `phaseMod` on both.
- **Every voice gets the strip today, `classic()` or not**: `buildVoice` builds the pitch pipeline for every voice,
  the sample voices included (with `freqHz` = the sample's recorded pitch, which the tree's `Freq` leaf also answers).

### 2. Where the four doors become stages

The four stages go at the FRONT of `classic()`, directly on the instrument, nested FM innermost and vibrato outermost,
each at its final place from its own step on, so no later step reorders an earlier one:

```
this -> fm -> pitchEnvelope -> accelerate -> vibrato -> onepole -> crush -> ... -> tremolo -> adsr
```

Why exactly this:

- **The mods bubble**, so where a pitch stage sits among the amplitude stages does not change the sound. Two things
  do depend on the place. The root must stay `classic()`'s `Adsr` (`endsInClassic()` reads the root's `adsr.on`
  slot), so the pitch stages go inside it. And the nesting decides the grouping of the product.
- **The grouping is the strip's.** The outermost node is combined first, so vibrato outermost, then accelerate,
  pitch envelope, FM gives `((V * A) * P) * F`, the strip's product, bit for bit (V and A may swap, the rest may not).
- **The tree's own pitch nodes compose as today wherever a path holds at most one of them**: today `own * S`, after
  the move `S * own`, the same bits. With two or more of the instrument's own pitch nodes nested on one path AND a
  door written, the grouping moves from `(own1 * own2) * S` to `(S * own1) * own2`: at most one rounding per ratio
  sample, about 1e-16 relative, far below any audible or 16-bit level, but not bit-identical. No corpus song has that
  shape under a door (checked: Kokon's instruments carry one `pitchEnvelope` per pitched path; the other door users
  play built-ins).
- **What composes differently, by structure**: the strip's root `phaseMod` reached an `fm` node's modulator and
  musical oscillators in parameter positions; the classic stages, like every tree pitch node, do not (decision D1).
  No corpus song plays such an instrument under a door (`sgbell` is in no song).

A sketch of the new head of `classic()` (named arguments, `/code-style` section 24; the slot groups are the new ones
of steps 1 to 4):

```kotlin
fun IgnitorDsl.classic(): IgnitorDsl {
    val s = IgnitorDsl.Slots

    // The pitch stages, FM innermost: the product the sources read groups as the retired strip's,
    // ((vibrato * accelerate) * penv) * fm. Mods bubble to the pitched sources, so their place among the
    // amplitude stages does not change the sound; the root stays the envelope (endsInClassic).
    val fmed = IgnitorDsl.Fm(
        carrier = this,
        modulator = IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.0)), // the strip's modulator never drifted
        ratio = s.fm.ratio,
        depth = s.fm.depth,
        attack = s.fm.attack,
        decay = s.fm.decay,
        sustain = s.fm.sustain,
        release = s.fm.release, // decision D3: sprudel's fm gets a release
    )
    val pitchEnveloped = IgnitorDsl.PitchEnvelope(
        inner = fmed,
        semitones = s.penv.semitones,
        attack = s.penv.attack,
        decay = s.penv.decay,
        sustain = s.penv.sustain,
        release = s.penv.release,
        attackCurve = s.penvCurves.attack,
        decayCurve = s.penvCurves.decay,
        releaseCurve = s.penvCurves.release,
    )
    val accelerated = IgnitorDsl.Accelerate(inner = pitchEnveloped, semitones = s.accelerate)
    val vibratoed = IgnitorDsl.Vibrato(inner = accelerated, rate = s.vibrato.rate, semitones = s.vibrato.semitones)
    val onepoled = IgnitorDsl.OnePoleLowpass(inner = vibratoed, freq = s.onepole)
    // ... crush, coarse, distort, the filters, tremolo, adsr as today
}
```

**The slot names follow the decided rule (D4, Q21; `/dsl-design` section 4)**, the one `classic()` keeps:
`<door>.<param>`, the namespace the sprudel door's name, the param part the engine door's word, a single-knob door
flat like `onepole`: `vibrato.rate`, `vibrato.semitones`; `accelerate`; `penv.semitones`, `penv.attack`, `penv.decay`,
`penv.sustain`, `penv.release`; `penvCurves.attack|decay|release` (curve INDEX, default `MOD_ENV_CURVE`); `fm.ratio`,
`fm.depth`, `fm.attack`, `fm.decay`, `fm.sustain` (and `fm.release`, D3). Defaults are the strip's, from the same
constants: the switches (`vibrato.semitones`, `accelerate`, `penv.semitones`, `fm.depth`) default to 0.0, which the gate reads
as off; `vibrato.rate` is `VIBRATO_RATE_HZ`, `fm.ratio` is `FM_RATIO`, the pitch-envelope stages are
`PitchEnvelopeDefaults.kt`, the FM stages 0, 0, 1.0 as `VoiceFactory` reads them today.

### 3. Bit-identity, door by door

The rule of `audio/ref/verification.md` ("a decision that replaces an expression lists every clause of the old
one") applied; every clause not named here is kept.

| door | old expression (strip) | new (classic stage) | identity |
|---|---|---|---|
| pitch envelope | built when `pEnv` finite and `!= 0`; stages `(p* ?: PITCH_ENV_*) * sampleRate`; sustain non-finite reads unset; curves `?: MOD_ENV_CURVE`; gate from the voice's limits per block; `renderPitchEnvelopeRatios` multiplying into the buffer | gate off at a leaf amount `== 0` or non-finite; the node's arm with the same constants; the same `renderPitchEnvelopeRatios` writing, combined by `Times` | **bit-identical** (one law, one mapping, `x * p == p * x`). Only a non-finite stage TIME differs (NaN and ±Infinity): the strip passed it to `EnvelopeCore` (NaN a zero-length stage, +Infinity a stage that never ends, -Infinity zero-length), while sprudel drops every non-finite value at the wire boundary, so the slot reads it as unset (the default time; review round 1 of step 1, B MINOR 2) |
| vibrato | built when `vibratoMod > 0`; rate `vibrato ?: VIBRATO_RATE_HZ`; phase from 0, `(TWO_PI * rate) / sampleRate`, the one-subtract or full wrap; `fastExp2(fastSin(phase) * depth / 12)` | gate off at a FINITE leaf depth `<= 0` (a non-finite depth stays built and renders the default, step 0); `VibratoModIgnitor`: the same accumulator, increment, wrap pair and ratio, plus `safeOut` (the identity on a ratio up to `SAFE_MAX`, 1e15) | **bit-identical**, except at the raw edges (step 2, review round 1, B MINOR 1, measured on the full engine): a depth above about 598 semitones (`log2(1e15) * 12`) is clamped by `safeOut` where the strip ran raw (597 identical, 598 on differ); a non-finite rate (NaN, ±Infinity) played NO vibrato on the strip (the wrap pinned the phase) and now plays the slot's default 5 Hz, because sprudel drops it and the slot reads it as unset; a +Infinity depth built the strip's vibrato and silenced the voice (NaN, scrubbed), while sprudel now drops it and builds no vibrato. All of it is the house rule (a non-finite value reads as unset) or a raw-Motor extreme |
| accelerate | built when `accelerate != 0` and `end > onset`; base = `endFrame - startFrame` (scheduled end, the release tail INCLUDED, a Double); per block `2^(octaves * rel / total)`, then `ratio *= 2^(octaves / total)` per sample | today's node: base = `voiceDurationFrames` (the GATE length, an Int) and `2^(octaves * (rel / total))` | **not identical as the node stands**: a different base and a different rounding. D2 decided for the GATE (2026-10-08): the sprudel door moves to the node's base, a sound change for Kokon's `strike` (step 3). The per-block seed keeps the known float-reassociation class (P4: 5.3e-15 across onsets, 1.7e-13 across block sizes, bounded at 1e-11) on both **Done in step 3, with the hold (D2):** the node writes `2^(octaves * (rel / total))` up to the gate and the target `2^(octaves)` from the gate frame on; the frames before the gate keep the node's bits; a gate of 0 frames (`legato(0)`) holds the target from the first frame (Q27; the strip glided over the release tail there). The hostile amounts, sprudel door, measured on the full engine (step 3 review round 1, reviewer B, against a HEAD export whose strip runs the same law, so only the clamp and the boundary differ): NaN, +Infinity and -Infinity FROZE the strip's oscillator (the wire carried them raw: a DC pulse per note shaped by its envelope, RMS -15.5 dB against the control) and now play the bare voice, bit for bit the no-door control (sprudel drops a non-finite value at the boundary, the slot reads it as unset, off); from about 598 semitones (`12 * log2(1e15)`), where the RATIO passes `SAFE_MAX`, `safeOut` clamps it at 1e15 where the strip ran raw (597 identical, 599 differs from frame 11,542, 1000 from frame 7,010); past about 12,288 semitones `2^x` overflowed on the strip and silenced the voice after its first sample, where the stage runs on at the clamped, meaningless pitch (a raw-Motor extreme, no clamp added; the sample playhead leaves its PCM after one frame on both, identical); -1e16 rounds the ratio to 0 on both and freezes the oscillator, identical |
| FM | built when `fmh` set or `fmEnv != 0`, rendered when depth `!= 0`; modulator phase in radians, `fastSin`; depth envelope evaluated ONCE per block at the block's first frame and held (ledger E11, Class 2), release always 0; `1 + sin * ((depth * env) / freq)`; divides by the raw note, no bypass at freq 0 | the `Fm` node: depth envelope PER SAMPLE (E1's fix), `1 + (mod * depth) / safeDiv(freq)` (with an envelope `(mod * (depth * env)) / freq`), bypass at `freq <= 0`, `safeOut`; the modulator a `Sine` whose drift lane seeds from the voice rng on its first block | **not bit-identical.** See below |

**What makes FM non-identical, with the expected size:**

1. **The envelope rate** (the audible part). The strip holds the depth flat for 128 frames from each block's first
   frame, so a note whose FM attack is shorter than a block (sgbell-style 1 ms) has ZERO FM for its whole first block,
   and every sweep steps at about 345 Hz. The node is smooth and reaches the peak. Audible as a brighter, cleaner
   onset; a listening pair.
2. **The rounding order**: `(mod * depth) / freq` against `sin * (depth / freq)`, one rounding in the deviation, about
   1e-16 relative per sample; through the carrier's phase sum that is around 1e-13 at the output over a note.
   Inaudible; a tolerance row (1e-11, the accelerate row's bound) shows it for the envelope-free case.
3. **The rng.** `AnalogDrift` draws from the voice's stream when it is constructed, even at `analog` 0 (a Gaussian
   seed and an int), and the modulator `Sine` constructs one on its first block. Every later generate-time draw of
   that voice shifts: a voice with a noise layer or analog drift gets other random values (the same statistics).
   Only when the stage is built (`fm.depth != 0`).
4. **A `detune` layer** (the built-in `sgpad`, or an authored overlay): the FM node's `freq` reads `Freq`, so its mod
   keeps the memo's freq key and a detuned layer renders it again at its own pitch, advancing the one modulator twice
   per block (residue 2 of the shared-modulator record). The strip ran ONE FM at the note for every layer.

None of this reaches the corpus: no song writes sprudel's `fm`. The FM step is proven by listening pairs plus the
tolerance row, and the corpus as the control that nothing else moved (decision D3).

**And across steps**: while some doors still run on the strip, a source reads `treeMods * stripRest`, which regroups
the product when three or more doors are written on one voice (or two plus an own pitch node). One rounding, as above;
no corpus voice writes more than one door. The final state (step 4) has the strip's grouping again.

### 4. The steps, one reviewable commit each

Order: the gate, then the pitch envelope, vibrato, accelerate, FM, and the strip's shell. Every step that moves a door
does the whole door: the slot group, `classic()`, sprudel's translation, the script slot surface, the renderer and its
`Voice.*` class deleted, the door's wire fields cut (decision D5), its specs moved, the docs (`audio/ref/data-model.md`,
`audio/ref/voice-synthesis.md`, `audio/MEMORY.md` in place plus one History line).

**The proof harness for "bit-identical"** (verification.md): (a) the 18-song corpus plus the frozen songs, raw doubles,
`HEAD` in a worktree against the tree, as the control that nothing unpredicted moved; (b) a door MATRIX rendered on
both sides, because the corpus does not call `penv` or `fm` at all: a scratch jvmTest that writes raw doubles in both
trees and compares them, deleted in the same change (the scaffolding rule). Matrix rows: the built-in `sine` and
`supersaw` at `analog` 0 and 0.5, the sample instrument (the `SampleInstrumentSpec` ramp PCM), onsets 0, 1, 37, 76,
127, a held realtime voice released mid-envelope, the door alone and with each door already moved. Every run carries
an engagement control: a mutant that must move the predicted rows (the slot written under a wrong key in sprudel, or
the gate's off test inverted). The committed spec per door is an ORACLE of the stage's law written in the test, not a
parity spec over the shared core (`audio/ref/verification.md`, "One law, two hosts, two specs").

#### Step 0. The gate on the four pitch arms (S, bit-identical)

The four classic stages would otherwise put a memo and a `ModApplyingIgnitor` on every pitched source of every voice.
The pitch arms in `buildIgnitor` get the gate's existing move: a stage whose gating knob is a `Param` or `Constant` leaf
at its off value, or non-finite, is not built and the walk descends with the UNCHANGED `accumulatedMod`. **Except the
vibrato's non-finite depth** (coordinator, 2026-10-07): the vibrato reads a non-finite depth as its DEFAULT
(`VIBRATO_SEMITONES`, `finiteOr`), not as 0, so it stays built; the other three read a non-finite switch as 0, so
for them non-finite is off and a fold.

```kotlin
is IgnitorDsl.Vibrato -> {
    // GATE ROW `vibrato` (audio/ref/off-values.md): off at a FINITE leaf depth <= 0 only.
    if (semitones.gatedOffWhenFinite(ignitorParams = ignitorParams, cache = cache) { it <= 0.0 }) {
        return inner.buildIgnitor(ignitorParams, cache, accumulatedMod)
    }
    // ... as today
}
```

Rows: vibrato a finite depth `<= 0`; accelerate `== 0`; pitch envelope amount `== 0`; FM depth `== 0` (a negative depth
renders on both hosts, so the off value is exactly 0). Each is a FOLD: a built stage at its off value outputs exactly
1.0 and every reader multiplies by it (`x * 1.0 == x`, also inside the oscillators' `phaseInc * phaseMod[i] * m`, the
sample's `rate * phaseMod[i] * m` and the Karplus `dl / phaseMod[i]`). Three consequences that are not folds, named
like the existing sibling rows of `IgnitorGateSpec`: a drawing sibling knob (a `perlin` rate under a literal-0 depth)
no longer draws; a gated FM no longer counts its modulator's release tail; a gated FM's modulator `Sine` no longer
takes its drift seed. A fourth, found while building it (coordinator, 2026-10-07: accepted and named): a gated
pitch arm's inner SHARES the build of the same node elsewhere, because the walk descends with the unchanged mod
(`s + s.vibrato(5, 0)` is `s + s`, one instance read twice; D13: one `let` is one signal); the ungated build had two
instances (+4.6 dB for a supersaw, +7.0 dB for a pluck, a step at exactly depth 0). A fifth, from review round 1: a
gated outer arm no longer hands its freq key to the pitch mods inside it, so a detuned layer under them renders the
inner mod once per block instead of twice. In both, the gated build equals the tree without the node. One
qualification: the gated arm builds nothing, but a build-time walk over the DSL (the detune fold predicate) still
sees its knobs. Proof: `IgnitorGateSpec` rows (an authored `vibrato(5, 0)`, `accelerate(0)`,
`pitchEnvelope(0, ...)`, `fm(..., depth = 0)` render the same bits as the tree without the node, each with a positive
control at a non-zero value), four rows in `audio/ref/off-values.md`, and the corpus (predicted identical: no song
writes a literal-0 pitch stage).

**What was done (2026-10-07, uncommitted, for review).** The four arms in `IgnitorDslRuntime.buildIgnitor` gate
first: accelerate and the pitch envelope on `gatedOff { it == 0.0 }`, fm on `depth.gatedOff { it == 0.0 }` (returns the
carrier built under the unchanged mod), the vibrato on the new `gatedOffWhenFinite { it <= 0.0 }` (its KDoc says why
the vibrato differs). The `gatedOff` KDoc names the fourth and fifth consequences. `IgnitorGateSpec`, 13 new rows and the
guardrail map extended: a stage row per arm with a positive control and the negative ON values (accelerate, penv,
fm); the vibrato's non-finite depths (NaN, both infinities, an unset-default `Param`) built and rendering
`VIBRATO_SEMITONES`; the four arms UNGATED (a non-leaf knob at the off value) rendering the bare source; that fold
over every source family (sine, sine at analog 0.5, square, tri, supersaw, pluck, the sample playhead), under a
strip `phaseMod`, and as a gated outer arm over a built vibrato; the four named consequences (a perlin vibrato rate
and a crackle fm modulator take no draws; a gated fm counts no modulator tail; a gated fm's modulator takes no drift
seed; the inner shares, `s + s`). The family row pins the seed consequence where it shows: fm ungated at 0 over a
source that draws at render (the analog sine, the supersaw, the pluck) is NOT the source alone, the gated build is.
`IgnitorTailSpec`'s "an FM modulator's envelope still counts" now writes a depth (200): at the default depth 0
the stage is gated and has no modulator to count, the named consequence. 16 mutants, each red on its predicted
rows (report: `tmp/reviews/pitch-step0-report.md`). Review round 1 (2 MAJOR, 6 MINOR): `PitchModSafetyTest`'s
NaN rows for the accelerate amount, the pitch envelope amount and the fm depth write the NaN as a NON-LEAF
(`OptimizerHint(Constant(NaN))`), because a leaf NaN is now gated and never reached the `finiteOr` they guard; a
row per arm gated INSIDE a built vibrato (the outer mod must survive, `classic()`'s nesting); a row for the fifth
consequence; the share row on a supersaw; the measured sizes in `off-values.md`. Docs: four rows and the
fourth and fifth consequences in `audio/ref/off-values.md`, the gate line in `audio/MEMORY.md` plus one History line. The
corpus run is the coordinator's (predicted identical: no song writes a literal-0 pitch stage).

#### Step 1. The pitch envelope (M, bit-identical)

Is it the right first step? Yes. Its law is already ONE copy for both hosts (`renderPitchEnvelopeRatios`, phase 3 step
5b c1), so this commit carries only the plumbing every later door reuses: the slot groups, the first stage in
`classic()`, the sprudel translation, the script slot surface and the test rig. One limit: no corpus song calls `penv`,
so its identity rests on the door matrix, and the corpus engages the new plumbing only from step 2 on.

- `audio_bridge`: `Slots.penv` (a `PitchEnvelopeSlots` group) and `Slots.penvCurves`. The curves group reuses the
  class the filters use; rename it from `FilterCurvesSlots` to a neutral name (`ModEnvelopeCurvesSlots`), since it now
  serves a pitch envelope. `classic()` places `PitchEnvelope(inner = this, ...)`; its KDoc's order line grows.
- `sprudel`: `classicSlotParams` writes the `pitchEnv` group (amount, the four stages, the curves by `AdsrCurves`
  index); the door-less early return also checks `pitchEnv == null`; `toVoiceData` stops writing the eight fields.
  The door's "amount is the switch, a tail-only call never invents it" (`/dsl-design` section 4) holds unchanged: an
  unwritten `penv.semitones` is 0.0, the gate's off value.
- `audio_be`: `VoiceFactory` stops building `Voice.PitchEnvelope`; `PitchEnvelopeRenderer` goes;
  `renderPitchEnvelopeRatios` loses its `multiply` flag (one host left, it always writes).
- `klangscript-libs`: `Ignitor.slot.penv` and `Ignitor.slot.penvCurves` (`KlangScriptIgnitorClassicSlots.kt`) with
  their door-parity rows (two doors, one DSL).
- Specs: `StripPitchEnvelopeParitySpec` retires (one host) into an oracle row; the test rig `DoorFields`
  (`_typed_fields_as_slots.kt`) gains the pitch-envelope fields, as phase 3 step 9 did for the filters; the
  block-framing penv row moves to a `classic()` tree (below, section 5); `LangPitchEnvelopeSpec`,
  `LangPenvCurvesSpec`, `SprudelVoiceDataSpec` assert slot keys instead of fields.
- Proof: bit-identical, the matrix all 0.0; the corpus identical (control).

**What was done (2026-10-09, uncommitted, for review; 52 files, 60 after review round 1).** `audio_bridge`: `PitchEnvelopeSlots` (`penv.semitones`
default 0.0, the switch; `penv.attack|decay|sustain|release` at the `PITCH_ENV_*` constants) and `Slots.penvCurves`;
`FilterCurvesSlots` is `ModEnvelopeCurvesSlots`; `classic()` places `PitchEnvelope(inner = this, ...)` first, its KDoc
names the pitch stages' place; `VoiceData` lost the eight fields (`pAttack` ... `pReleaseCurve`). `sprudel`:
`penv(semitones, ...)` and `pamt(semitones, ...)`, the reader `penv.semitones` (`amount` removed, a row in
`docs/retired-names.md`); `classicSlotParams` writes the `pitchEnv` group (every set field, the switch only when set)
and its door-less early return checks `pitchEnv == null`; `toVoiceData` stops writing the eight fields; `pamt` stays
the door's short name. `audio_be`: `VoiceFactory` builds no `Voice.PitchEnvelope` (gone), `PitchEnvelopeRenderer` gone,
`buildPitchPipeline` lost its parameter, `renderPitchEnvelopeRatios` lost `multiply`. `klangscript-libs`:
`Ignitor.slot.penv` and `Ignitor.slot.penvCurves` (`KlangScriptIgnitorPenvSlots`, `KlangScriptIgnitorPenvCurvesSlots`),
their rows in `KlangScriptClassicDoorParitySpec`. Specs: `StripPitchEnvelopeParitySpec` and
`PitchEnvelopeRendererFastExp2Spec` retired (one host; the node's twin `PitchEnvelopeModFastExp2Spec` stays); the
new `ClassicPitchEnvelopeSpec` is the ORACLE: a real voice through `VoiceFactory`, the frequency ratio read frame by
frame off a ramp sample (`_pitch_ratio_probe.kt`: `pcm[n] = n` at rate 1.0, so the sample instrument's output is its
playhead and `ratio[i] = out[i + 1] - out[i]`), against the law written out (three gate positions, two onsets), the
retired spec's Q3 and Q7 rows, the stage-only row and a sustain-slot row. `DoorFields` gained `DoorPenv`;
`ModEnvelopeDefaultCurveSpec`'s strip row reads the classic stage through the probe and gained a per-stage curve row
(each curve slot shapes its own stage only); `EnvelopeLawSpec`'s strip host retired into the Ignitor pitch host (the
moved gate and the gate on a block's last frame, `renderNode(moveGateTo)`); the block-framing penv row writes the
slots on its `classic()` probe; the realtime gate row of `VoiceLifecycleSpec` holds the pitch envelope in the tree;
`ClassicTailSpec`, `IgnitorRegistryTest`, `ClassicSlotParamsSpec` (the literal map and a pitch-envelope row),
`LangPitchEnvelopeSpec` (slot keys on the wire, a door-parity row: sprudel word = slot = node knob),
`LangPenvCurvesSpec`, `LangControlRestSpec`, `LangFieldAccessorsSpec`, `LangDoorFormsSpec`, `FreqAccessorIntelSpec`,
`WireCodecRoundTripSpec` follow. Docs: `audio/ref/data-model.md`, `voice-synthesis.md`, `off-values.md`, the two
`MEMORY.md` files, `/dsl-design` section 4's penv sentence, the music-writing sprudel reference.

- **Wire.** `WIRE_SCHEMA_HASH` `1326193870` to `-168072926` (eight `VoiceData` fields cut). JS codec green.
- **Door matrix, bit-identical** (scratch `ZzScratchPitchDoorMatrixSpec`, sprudel texts rendered as one voice through
  `VoiceFactory`, raw doubles; HEAD exported with `git archive` against the tree): 390 rows (sine and supersaw at
  analog 0 and 0.5, the sample instrument on a ramp; 13 door rows and the control; onsets 0, 1, 37, 76, 127 and a
  held voice released at 3000 frames), every row identical. Every door row differs from its control except the two
  written to be off (`penv(0, ...)`, `vib(4)`). Engagement mutant (sprudel writes `penv.semitones` under a misspelled
  key): exactly the 180 rows of the six `penv` door kinds moved, the 210 others (control, off rows, vibrato rows) stayed.
- **Corpus, bit-identical** (`tmp/naming/corpus-pp-s1.txt` against `corpus-pp-before.txt`): 17 of 18 rows identical;
  the 18th, live Kokon, moved because the maintainer edited `Kokon.kt` during the run. Kokon and Der Schmetterling
  from HEAD's text, on both sides: Kokon `f0bf378664bc2077`, Der Schmetterling `2cb10d5236015cae`, identical.
- **Mutation checks** (one lock call each, restored, `cmp` clean): `classic()`'s attack wired to the decay slot (red:
  the oracle rows, the door-parity row); its sustain wired to the release slot (red: the oracle rows, the sustain
  row); `penv.semitones` defaulting to 1.0 (red: the stage-only row, the NaN row, `ClassicTailSpec`); the attack curve
  read from `lpfCurves` (SURVIVED the first version of `ModEnvelopeDefaultCurveSpec`'s row; the per-stage row was
  added, then red), the decay curve from the release slot and the release curve from the decay slot (red); the law's
  `/ 12.0` as `/ 12.5` (red: oracle rows); sprudel writing the decay under `penv.attack` (red: `ClassicSlotParamsSpec`
  twice, `LangPitchEnvelopeSpec`); the early return ignoring `pitchEnv` (red: `ClassicSlotParamsSpec`); the reader
  `penv.semitones` reading the attack (red: door-parity row); `Ignitor.slot.penv.decay` the attack slot (red:
  `KlangScriptClassicDoorParitySpec`); the node reading the scheduled gate (red: `EnvelopeLawSpec`'s moved gate,
  `VoiceLifecycleSpec`'s realtime row); the node counting from the block start (red: the block-framing penv row);
  the three non-finite defences removed together (the `Param` leaf's unset rule, the gate's non-finite arm, the
  node's sustain `finiteOr`; red: the NaN row among others). Each defence alone holds that row, so a single-site
  mutant cannot kill it.
- **Found on the way.** `SampleInstrumentSpec`'s premise (the sample at rate 1.0 IS the sine) does not hold under a
  pitch door: a bent playhead interpolates the PCM between frames. The planned per-door row there is replaced by the
  probe, which renders through the sample instrument itself; the spec's KDoc says why.
- **Suites.** `audio_bridge` jvmTest 146 and jsTest 265; `audio_be` jvmTest 2,412 and jsBrowserTest 2,308; `sprudel`
  jvmTest 3,474 (487 skipped, as before); `klangscript-libs` jvmTest 832 and jsTest 609; `BuiltInSongsSmokeTest`,
  `SongBenchmarkCasesCompileSpec`, `DslDocExamplesSpec` green; `compileTestKotlinJs` of `audio_bridge`, `audio_be`,
  `sprudel`, `klangscript-libs` and the root green.
- **What "bit-identical" excludes** (review round 1, A8 and B; every shape is accepted by the plan, sections 2 and 3,
  D1, D6, and no corpus song has one): an `fm` node's MODULATOR is not bent by `penv` until step 3b bends it again
  (D1 (b)): `s("sgbell").penv(...)` differs at -2.9 dB diff RMS, an authored fm instrument at -1.0 dB; a musical
  oscillator in a parameter position (a filter LFO, a modulated cutoff at -13.0 dB) stays unbent for good (plan
  section 2); three pitch factors on one path (two doors still on the strip plus `penv`, or own pitch nodes plus a
  door) regroup the product, at most about 8.6e-13 at the output, about -270 dB, for good where two of the
  instrument's own pitch nodes meet a door (that does not end with the strip); an instrument without `classic()`
  ignores `penv` (D6). Reviewer B measured each on the full
  engine, HEAD against the tree; the matrix held no such row.
- **Cost, the baseline for steps 2 to 4** (reviewer B, production V8 bundle and JVM, against an old-against-old
  control): gated off (no `penv`) the stage is free (+111 bytes per voice build on V8, +56 on the JVM, render inside
  the noise). Gated on, the tree's memo and `ModApplyingIgnitor` cost more than the strip's one buffer: render +13 to
  +16 percent for a settled saw with `penv` and +2 to +12 percent while it sweeps (about +210 to +740 ns per block
  per voice, under 0.03 percent of a 2.67 ms block); build about +3 KB per voice on V8 and +1.7 KB on the JVM. The
  sweep's own V8 boxing (about 2.17 KB per block, HEAD alike) is `docs/tasks/engine-follow-ups.md` item 10a.
- **Review round 1** (`tmp/reviews/pp1-r1-A.md`, 1 MAJOR; `tmp/reviews/pp1-r1-B.md`, 0 MAJOR), applied: the script
  `classic()` door's KDoc lists the pitch envelope stage and says an instrument without `classic()` ignores `penv`
  (D6), and the sprudel `vibrato`, `accelerate` and `fm` KDocs say they still reach every instrument from the strip
  until their step (A1); `PitchEnvelopeDefaults.kt` names its readers (A2); `ignitor-reference.md` and
  `classic-doors-and-velocity.md` (A3); the published pages go to step 5's docs list (A4);
  `ClassicDoorRenderParitySpec` has an engagement row for each of the eight slots (A5); `ClassicPitchEnvelopeSpec`
  renders a realtime note-off through the slot path (the probe's `releaseAtFrame`; its unused `sampleRate` and
  `blockFrames` parameters went, A6); the exceptions and costs above (A8, B); the identity table names ±Infinity
  stage times (B). Left as found, outside this step's files (N4): "`classic()`'s first stage" for the onepole in
  `IgnitorRegistry.kt:171`, `IgnitorDslRuntime.kt:1420`, `VoiceBagGuardSpec.kt:101,342`, `ClassicVoiceRig.kt:266`,
  `OnepoleParitySpec.kt:80,99`, `ClassicTailRenderSpec.kt:51`, `tut_SpaceAndDirt.kt:29` (true for the amplitude
  stages; the pitch stage bubbles). `pamt` waits for the maintainer (A7). Mutation checks of the new rows, each red
  on exactly its own row and restored `cmp` clean: each of the eight `classic()` pitch-envelope wirings pointed at
  another slot (`semitones` at `penv.attack`, `attack` at `penv.decay`, `decay` at `penv.attack`, `sustain` at
  `penv.release`, `release` at `penv.sustain`, each curve at the `lpfCurves` slot of its stage) turns its
  `ClassicDoorRenderParitySpec` engagement row red; the node reading the scheduled gate instead of the moved one turns
  both onsets of the realtime slot-path row red. Review round 2 (`tmp/reviews/pp1-r2.md`, clean): the exception clause
  split (the fm modulator until 3b, the parameter-position oscillator and the own-node regrouping for good),
  `effects-mixing.md`'s per-voice list, the `penv.release` engagement row moved its gate past a short decay so the
  release starts from the sustain (a `classic()` sustain fixed at its default now turns the `penv.sustain`,
  `penv.release` and `penvCurves.release` rows red, restored `cmp` clean).

#### Step 2. Vibrato (S to M, bit-identical)

- `Slots.vibrato` (`vibrato.rate`, `vibrato.semitones`); `classic()` places `Vibrato` OUTSIDE the pitch envelope (its final
  place). Sprudel writes both keys from its `pitchMod` group (`vib(4)` alone writes only the rate, the depth stays 0,
  no vibrato: the strip's behaviour). `VoiceData` loses `vibrato`, `vibratoMod`; `VibratoRenderer` and
  `Voice.Vibrato` go; `Ignitor.slot.vibrato` on the script door.
- **Guard from step 0** (audio review, round 1): the vibrato's gate keeps a NON-FINITE depth built (it renders the
  default 0.25 st), so `vibrato.semitones`'s slot default must be the finite literal 0.0, never `SLOT_UNSET`, or every
  voice of every song gets a vibrato. State it in the slot group's KDoc (the shape of `mul`'s "must default to a
  safe literal"), and add a spec row "an unwritten `vibrato.semitones` slot builds no vibrato" (`shapeOf` equal to the
  bare tree).
- Specs: `VibratoConsistencyTest` (strip against node) becomes an oracle row; the benchmark case
  `sine+vibrato+tremolo` (`IgnitorBenchmark`) now runs through the slot. Measure it: the strip wrote one buffer per
  voice, the tree writes one memo plus one `ModApplyingIgnitor` per pitched source (two scratch buffers and a copy).
- Proof: bit-identical. The corpus ENGAGES here: TetrisRemix (the lead and its brown layer, which ignores pitch on
  both paths), StrangerThings, IrishLamentTechno (built-in `saw`, `supersine`), Kokon (`spin`, `sing`, `soar`, one own
  `pitchEnvelope` per path, so the order flips and the bits do not). Predicted identical; the engagement mutant
  (depth divided by 12.5) must move exactly those songs.

**What was done (2026-10-09, uncommitted, for review; 56 files).** `audio_bridge`: `VibratoSlots` (`vibrato.rate` default
`VIBRATO_RATE_HZ`, `vibrato.semitones` default the finite 0.0, its KDoc says why never `SLOT_UNSET`), `Slots.vibrato`;
`classic()` places `Vibrato(inner = pitchEnveloped, ...)`; `VoiceData` lost `vibrato` and `vibratoMod`. `sprudel`:
`vibrato(rate, semitones)` / `vib(rate, semitones)` and the reader `vibrato.semitones` (`depth` removed, a row in
`docs/retired-names.md`); `classicSlotParams` writes the rate and the depth from the `pitchMod` group (the switch only
when set) and its early return checks `pitchMod == null`; `toVoiceData` stops writing the two fields. `pamt` is retired
(maintainer: "drop pamt"; its forms, the constant, the alias rows of `LangPitchEnvelopeSpec`, `LangDoorFormsSpec`,
`LangFieldAccessorsSpec`, `LangControlRestSpec`, `CallableObjectDocsSpec`, `FreqAccessorIntelSpec`, the skill reference,
a `retired-names.md` row). `audio_be`: `VibratoRenderer` and `Voice.Vibrato` gone, `buildPitchPipeline` lost its
vibrato. `klangscript-libs`: `Ignitor.slot.vibrato` (`KlangScriptIgnitorVibratoSlots`) and its parity rows; the script
`classic()` KDoc and the sprudel `vibrato` KDoc say the vibrato is a `classic()` stage now. Songs: the five `depth =`
arguments of TetrisRemix, IrishLamentTechno (3) and StrangerThings, the frozen Stranger Things (and its header note),
`SongBenchmarkCases`, and the four in `Kokon.kt` (maintainer's permission; only those four arguments) say
`semitones =`. Specs: `VibratoConsistencyTest` retired into the new oracle `ClassicVibratoSpec` (the ramp probe against
`2^(sin(2 pi rate p / sr) * semitones / 12)` with the library's `sin` and `pow`, three rows at two onsets; a rate alone,
a depth of 0 and a negative depth are the bare voice; a non-finite `vibrato.semitones` in the bag reads as the slot's
0; the step 0 guard row: an unwritten `vibrato.semitones` builds no vibrato, the built root's class equal to the bare
source's, with a written depth as the positive control). `PitchModulationTest`'s vibrato rows render the tree
vibrato (with the strip's accelerate and FM); `ModulatorPhaseWrapSpec`'s strip vibrato row retired (the node row
stays); `SynthVoiceTest` drives its strip rows with accelerate; `BlockFramingInvarianceSpec`, `ClassicVoiceRig` and
`SharedScratchSpec` write the slots; `ClassicTailSpec`, `IgnitorRegistryTest`, `ClassicSlotParamsSpec` (the literal
map and a vibrato row), `ClassicDoorRenderParitySpec` (two engagement rows), `SprudelVoiceDataSpec` and
`LangPitchParamNamesSpec` (a door-parity row: sprudel word = slot = node knob) follow. Docs: `data-model.md`,
`voice-synthesis.md`, `off-values.md`, `effects-mixing.md`, `PitchModDefaults.kt`, the two skill references, the two
`MEMORY.md` files, `bugfix-non-finite-pitch-strip-and-signals.md`, `katalyst-master-configure-doors.md`.

- **Wire.** `WIRE_SCHEMA_HASH` `-168072926` to `-1897999924` (`vibrato`, `vibratoMod` cut). JS codec green.
- **Corpus, bit-identical** (`tmp/naming/corpus-pp-s2.txt` against `corpus-pp-before.txt`): 17 of 18 rows identical,
  the songs whose text changed included (Remix: Echo um Echo, Seltsamere Dinge, The Synthsale Piper's Last Rave, frozen
  Stranger Things); live Kokon differs, as in step 1, from the maintainer's own edits. Kokon from HEAD's text: before is
  HEAD's text with `depth` (`f0bf378664bc2077`, the step 1 render), after is the same text with the same four
  `semitones` renames (`$S/songs/kokon-head-s2.ks`): `f0bf378664bc2077`, identical. Der Schmetterling is unmodified in
  the tree, so its live row is HEAD's text: identical.
- **Engagement mutant** (sprudel writes `vibrato.semitones` divided by 12.5): exactly the predicted songs moved, Remix:
  Echo um Echo (TetrisRemix), Seltsamere Dinge (StrangerThings), The Synthsale Piper's Last Rave (IrishLamentTechno),
  frozen Stranger Things and Kokon (live and HEAD's text, `e835aea7145e5a0e`); the 13 others stayed (Die Kirschblüte and
  The Synthsale Piper's Farewell play an authored `vibrato` node, which the door does not touch). Restored, `cmp` clean.
- **What "bit-identical" excludes** (review round 1, A3 and B MINOR 3, measured by reviewer B on the full engine; no
  corpus song has one of these shapes): D6, `vib` is lost on an instrument without `classic()`; D1, the strip's
  vibrato bent an `fm` modulator and parameter-position oscillators, the stage does not: `s("sgbell").vib(...)` -5.0 dB
  diff RMS and an authored fm instrument -2.8 dB until step 3b bends the modulator again, a musical oscillator in a
  parameter position -15.1 dB for good; the regroupings, at most about 7.3e-13, about -270 dB: `vib` + `penv` +
  `accelerate` (6.3e-13), `vib` + `accelerate` + `fm` (6.9e-13, new in this step), all four doors (7.3e-13), an own
  `pitchEnvelope` + `vib` + `accelerate` (5.2e-13), all four ending with step 3, and two own pitch nodes + `vib`
  (5.0e-13, for good, plan section 2); the restorations: `vib` + `penv` + `fm` (the sample instrument too) and an own
  `vibrato` + `vib` + `penv` are back to the bits from before step 1. The raw edges of the vibrato row in section 3
  (a depth past about 598 semitones, non-finite rates and a +Infinity depth) apply.
- **Door matrix** (the scratch spec of step 1 with two more rows, HEAD `5bb3c78f` exported against the tree): 450 rows,
  420 identical; the 30 rows of "vib with penv and accelerate (3 factors)" differ, as section 3 predicts ("across steps":
  tree `(V * P) * A` against step 1's `P * (V * A)`): max abs 7.6e-14 at a peak of 0.46 (supersaw at analog 0.5, onset
  37), about -256 dB, one rounding. Step 3 moves accelerate between them and restores the strip's grouping. Every other
  door row, the two-door rows (vib with penv, accelerate or fm) and the off rows (`vib(4)`, `vib(5, -0.3)`) identical.
- **Mutation checks** (one lock call each, restored, `cmp` clean): `classic()`'s rate fixed at the default (red: the
  7.3 Hz oracle rows, the `vibrato.rate` engagement row, the door-parity row); its depth wired to the rate slot (red:
  every oracle row, the off row, the guard); `vibrato.semitones` defaulting to `SLOT_UNSET` (red: the guard row "an
  unwritten `vibrato.semitones` builds no vibrato", the off row, `ClassicTailSpec`); sprudel writing the rate under
  `vibrato.semitones` (red: `ClassicSlotParamsSpec` twice, the door-parity row); the early return ignoring `pitchMod`
  (red: the vibrato row); the reader reading the rate (red: door parity); `Ignitor.slot.vibrato.semitones` the rate
  slot (red: `KlangScriptClassicDoorParitySpec`); the law's `/ 12.0` as `/ 12.5` (red: the six oracle rows); the
  `Param` leaf reading a non-finite override raw (red: the non-finite row); the vibrato nested inside the pitch
  envelope (red: `ClassicTailSpec`'s order and vocabulary). Review round 1: `PitchModulationTest`'s FM row now checks
  the product against each door alone (red when the strip's FM is not built, restored `cmp` clean); the stale strip
  texts (`ModBlockingIgnitor`, four test KDocs, `PitchModDefaults.kt`), the `vib` sentence in `/dsl-design` section 4
  and the per-step checklist above step 3 were added.
- **Cost, the baseline for steps 3 and 4** (review round 1, B MINOR 2: one voice through `VoiceFactory`, HEAD / tree /
  HEAD again as the control, medians of 3): gated off (rate only, or no `vib`) is free to render. Gated on, V8
  production bundle: a saw + `vib` about +22 percent per block (+544 to +600 ns; pinned 1.216, unpinned 1.231, controls
  1.003 and 1.025), a supersaw +8 to +10 percent, sgpad (two pitched sources) +16 to +19 percent (+700 to +800 ns),
  0.02 to 0.03 percent of a 2.67 ms block; render bytes equal on both sides; build +2.0 to +2.1 KB per voice. JVM: +7 to
  +12 percent render (saw 1810 to 2021 ns), allocation-free, build +1.2 to +1.4 KB per voice. My own
  `sine+vibrato+tremolo` run through `IgnitorBenchmark` (V8 pinned 1.057, unpinned 1.020; JVM 1.068) dilutes the
  vibrato with the tremolo and the harness, so B's isolated numbers are the baseline. The cause: the strip's vibrato
  was one loop writing one buffer; the tree adds the memo, a `ModApplyingIgnitor` per pitched source (two scratch
  buffers, two copies), two `readParam`s and a `safeOut` per sample.
- **Found on the way.** `AccelerateRenderer` is now the strip's first renderer, so its multiply-in branch has no
  caller until step 3 deletes the renderer (`PitchModulationTest` guarded it through the strip vibrato; its row now
  combines a tree vibrato with the strip's accelerate). The per-door `SampleInstrumentSpec` row is again the ramp probe
  (step 1's reason).
- **Suites.** `audio_bridge` jvmTest 146 and jsTest 264; `audio_be` jvmTest 2,417 and jsBrowserTest 2,312; `sprudel`
  jvmTest 3,480 test cases (skipped ones included); `klangscript-libs` jvmTest 832 and jsTest 608;
  `BuiltInSongsSmokeTest` (the live Kokon included), `SongBenchmarkCasesCompileSpec`, `DslDocExamplesSpec` green;
  `compileTestKotlinJs` of `audio_bridge`, `audio_be`, `sprudel`, `klangscript-libs` and the root green.

#### Per-step checklist for steps 3 to 5 (Standard 3: every escape closes one hole)

Steps 1 and 2 both overstated their records (review round 1 of each: "bit-identical" without the exceptions, the
identity table's non-finite rows). So every later step, before its record says "bit-identical" anywhere:

- **(a) Name every shape that is NOT bit-identical**, with measured numbers, in the step record AND in every KDoc and
  `MEMORY.md` claim the step writes: D1 (an `fm` modulator, a parameter-position oscillator), D6 (bare instruments),
  the regroupings (three or more pitch factors on one path; which end and which are for good), the restorations, and
  the non-finite reads.
- **(b) Render NaN, +Infinity, -Infinity and the first amount whose resulting RATIO or value passes `SAFE_MAX`** (for a
  semitone door about 598 semitones, not an amount past `SAFE_MAX`) for EVERY knob of the door being moved, HEAD
  against the tree on the full engine, and **describe what HEAD actually did, measured, not assumed**; state each
  result in the identity table of section 3. Why this wording (step 3 review round 1, B MINOR 1): this is the third
  recurrence of the class (step 2's B MINOR 1 named the same 598-semitone threshold for the vibrato; step 3's record
  took "past `SAFE_MAX`" as an amount and assumed a silenced voice where HEAD froze the oscillator into a DC pulse).
- **(c) Record the per-voice cost** against HEAD as the step's baseline: V8 production bundle pinned and unpinned,
  and the JVM, render and build, with an old-against-old control, the door isolated (not diluted by other stages).

#### Step 3. Accelerate, and one glide base: the gate (M, a sound change for the sprudel door)

Decision D2 (maintainer, 2026-10-08): **the glide spans the gate**, onset to gate close, for both doors: "gliding to
the gate close is the correct behaviour". The glide reaches its target when the note ends and holds it through the
release. The Ignitor node already does this; the sprudel door changes.

- The node keeps its law and its base, `IgniteContext.voiceDurationFrames` (the gate length), with its rounding
  `2^(octaves * (rel / total))`. A held realtime voice keeps its accelerate inert, as today (the decided semantics of
  2026-08-29).
- The slot is flat, `accelerate` (sprudel's reader), default 0.0. `classic()` places `Accelerate` between the pitch
  envelope and the vibrato. `VoiceData` loses `accelerate`; `AccelerateRenderer` and `Voice.Accelerate` go. Correct
  the KDocs that describe the strip's base (`Voice.releaseGate`, `VoiceLimits`, the comment in `VoiceScheduler`).
- Specs: `AccelerateSemitoneLawSpec` moves to the node and pins the gate base (a long release tail, where the two bases
  differ most); the block-framing accelerate row stays at its 1e-11 bound.
- **Proof: a sound change, not bit-identity.** The only corpus user is Kokon's `strike`
  (`.accelerate("0.05".add(perlin(-0.20, 0.20))).ignp("release", 3.0)`): its small detune glide now completes at the
  gate close instead of across the 3 s tail. Every other corpus song stays bit-identical (the corpus run shows it).
  Kokon gets a listening pair (before and after) for the maintainer. The Ignitor door is unchanged.
- **The hold** (maintainer, 2026-10-09, "yes hold"; D2 above): the node as it stood rose on past the gate at the same
  rate, so the Ignitor door changes too: the node writes its target from the gate frame on.

**What was done (2026-10-09, uncommitted, for review; 47 files after review round 1).** `audio_be`: `AccelerateModIgnitor` holds: each
block's loop splits at `voiceDurationFrames - voiceElapsedFrames`, the frames before the gate render the node's law
unchanged (the same seed and step, the same bits), every frame at or past the gate writes `safeOut(2^(octaves))`, with
no per-sample branch and no allocation; a gate of 0 frames holds the target from the first frame (review round 1, A1;
decided by default, maintainer questions Q27: "at gate 0 it has arrived"; the early return that wrote 1.0 went). `AccelerateRenderer` and `Voice.Accelerate`
gone, `buildPitchPipeline` lost `accelerate`, `startFrame` and `endFrame` (FM needs neither); the KDocs of
`Voice.releaseGate`, `VoiceLimits`, `IgniteContext.voiceDurationFrames`, `ModBlockingIgnitor` and the held-voice horizon
in `VoiceScheduler` (it names no strip base; it now says the far gate keeps a held voice's glide inert) say the gate
base and the hold. `audio_bridge`: `Slots.accelerate`, flat, default 0.0 (the switch); `classic()` places
`Accelerate(inner = pitchEnveloped, semitones = s.accelerate)` and the vibrato around it; `VoiceData` lost
`accelerate`; the node's and the door's KDocs say gate and hold. `sprudel`: `classicSlotParams` writes `accelerate` from
the `pitchMod` group (a non-finite value is dropped, the house rule), `toVoiceData` stops writing the field, the door's
KDoc says it fills `classic()`'s stage and glides to the gate. The door's parameter was already `semitones`: no rename,
no `retired-names` row. `klangscript-libs`: `Ignitor.slot.accelerate` (flat, like `onepole`) and its parity row; the
script `classic()` KDoc. Specs: `AccelerateSemitoneLawSpec` moved from the strip to the node (`ignitor/`): 24 and 7
semitones, a gate on a block boundary and one inside a block, the frames before the gate equal to the law written out
bit for bit (the per-block seed, the per-frame step), the target held exactly through seven gates of tail, a gate inside
the first window of an onset 37 frames into its block (the split's `start +` term, review round 1, A2), a gate of 0
frames (the target from the first frame, onset offsets 0 and 37), and a downward row. The new `ClassicAccelerateSpec` is the ORACLE of the sprudel door: a real voice through `VoiceFactory`
and the ramp probe, `2^(semitones / 12 * p / gate)` then the target, over a release tail longer than the gate (three
amounts, Kokon's 0.05 among them, a gate inside a block, two onsets); a held realtime voice released at frame 1000 keeps
its far base and never reaches the hold; 0 semitones and a non-finite slot value are the bare voice; the guard "an
unwritten `accelerate` builds no stage". The block-framing accelerate row writes the slot and keeps its 1e-11 bound.
`PitchModulationTest`'s accelerate row renders the tree node (the strip-accelerate combination row went: the vibrato
and FM row still shows a tree mod times the strip's buffer); `SynthVoiceTest` drives its strip rows with FM, the strip's
last door; `ClassicTailSpec` (order and vocabulary), `IgnitorRegistryTest`, `ClassicSlotParamsSpec` (an accelerate row
and the literal map), `ClassicDoorRenderParitySpec` (an engagement row), `LangPitchParamNamesSpec` (door parity: sprudel
word = slot = node knob), `SprudelVoiceDataSpec`, `KlangScriptClassicDoorParitySpec` and the rig `DoorFields` follow.
Docs: `data-model.md`, `voice-synthesis.md`, `off-values.md`, `effects-mixing.md`, the two skill references, the two
`MEMORY.md` files, the NaN task's accelerate line (dissolved).

- **Wire.** `WIRE_SCHEMA_HASH` `-1897999924` to `-275171334` (`accelerate` cut). JS codec green.
- **Corpus** (`tmp/naming/corpus-pp-s3.txt` against `corpus-pp-before.txt`): 17 of 18 rows identical; the 18th is live
  Kokon (the maintainer's edits and this step). **Kokon from HEAD's text** (`$S/s3/kokon-head.ks`, lines 19 to 550 of
  HEAD's `Kokon.kt`) moved, as predicted: `f0bf378664bc2077` (every step so far) to `6d22988a5668b97f`. Where and how
  much, from the listening pair (16-bit, 202 s): the two files are identical sample for sample except in 105.01 to
  115.13 s and 141.01 to 151.21 s, the two `landing` strikes (line 465, `landing` at line 486 inside `heavyBlock`,
  played twice at lines 518 and 519: cycles 35 and 47, a 3 s gate, a 3 s release, the hall after it). Both strikes
  carry exactly 0.05 semitones (the perlin term is 0 on a whole cycle). On the strip the glide spanned gate plus tail,
  so at the gate close it had reached 2.5 cents; now it reaches 5 cents there and holds: at most 2.5 cents apart, at
  the gate close, shrinking to 0 at the voice's end. The sample difference is large (max 0.78, diff RMS -4.7 and
  -0.3 dB against the mix in the two windows) because an 11-voice unison a few cents apart drifts out of phase.
- **Engagement mutant** (sprudel writes the accelerate slot under a misspelled key): exactly Kokon moved (live, and
  HEAD's text to `a936291275d76bf2`, no glide at all), the 17 others stayed. Restored, `cmp` clean.
- **Door matrix** (the scratch spec of steps 1 and 2 with eleven more door rows, HEAD `ee8903c5` exported against the
  tree): 780 rows. Every row without `accelerate` is identical (the control, the penv, vib and fm rows, `accelerate(0)`).
  Every row with an ordinary non-zero `accelerate` differs: the base moved from gate plus release tail to the gate, the
  sound change (the matrix voices have the 0.05 s default release, the long-release row 1.5 s). The non-finite and
  huge rows: section 3's identity table. A scratch run of the pitch product the source reads (a tracking source under
  `Sample.classic()`) shows `classic()` groups it as the strip did: with vib, accelerate and penv written, every frame
  equals `(V * A) * P` bit for bit (3840 frames), while `V * (A * P)` and `(V * P) * A` differ on 813 and 815 frames; an
  own `pitchEnvelope` under vib and accelerate equals `(V * A) * own`, the strip's `own * (V * A)`. **Only the base and
  the hold changed:** the same matrix against that gate-law HEAD export is bit-identical on every one- and two-door
  `accelerate` row of the sine, supersaw (analog 0 and 0.5) and sample instruments, the long-release row included;
  the three-factor rows differ by at most 1.1e-13 (about -265 dB), the export's step-2 grouping `(V * P) * A` against
  the restored `(V * A) * P` (in the matrix; on the full engine reviewer B measured up to 7.1e-13 for the doors alone
  and 9.7e-13 with an own vibrato, about -262 to -271 dB); `sgbell` differs by D1 (below).
- **What is not the strip's sound** (checklist (a)): the base and the hold above, on every sprudel `accelerate` under a
  release tail; a gate of 0 frames (`legato(0)`): the strip glided over the release tail, the stage holds the target
  from the first frame (Q27; no corpus song writes `legato(0)` with `accelerate`, the corpus rerun below); the Ignitor
  door: an authored `accelerate` under a release tail, from the gate on (no corpus song, the grep of the songs and the
  frozen texts); D6, `accelerate` is lost on an instrument without `classic()`; D1, the
  strip's accelerate bent an `fm` node's modulator and parameter-position oscillators, the stage does not (measured
  on the full engine against a scratch HEAD export whose strip accelerate runs the node's gate law and hold, so only
  the structure differs: `s("sgbell")` with `accelerate(3)`, `accelerate(-5)`, `accelerate(4)` with a 1.5 s release,
  and with `penv` or `vib` beside it, -6.3 to -10.1 dB diff RMS over a 9000-frame gate, -71 to -78 dB on a held voice
  whose far gate keeps the glide nearly inert; step 3b bends the modulator again);
  the non-finite and huge amounts of section 3 (non-finite now plays the bare voice where the strip froze the
  oscillator). **The regroupings of steps 1 and 2 that end here**: `vib` + `penv` + `accelerate`, all four doors
  (`(V * A) * P` then the strip's F), an own `pitchEnvelope` + `vib` + `accelerate` (verified bit for bit above),
  `vib` + `accelerate` + `fm` (`(V * A) * F`, by the same nesting) and `penv` + `accelerate` + `fm` (step 1's
  `P * (A * F)`, now `(A * P) * F`, the strip's; review round 1, B NIT 1). Their size before, on the full engine:
  up to 7.1e-13 for the doors alone, 9.7e-13 with an own vibrato (about -262 to -271 dB). **Remaining, temporary:** an
  own pitch node + any `classic()` pitch door + sprudel `fm` reads `(doors * own) * F` where the strip gave
  `own * (doors * F)` (one rounding, about 1e-16 relative per ratio sample; new in this step: an own node +
  `accelerate` + `fm`, on HEAD `own * (A * F)`); it ends in step 4, when FM becomes the innermost `classic()` stage
  (review round 1, A4). **Remaining for good:** two own pitch nodes + a door (plan section 2).
- **Mutation checks** (one lock call each, restored, `cmp` clean): the node without the hold (red: every law row, every
  oracle row); the hold's target off by 1e-6 (red: the same); the hold one frame late (red: the law rows on the
  in-block gate; the oracle's 1e-9 tolerance does not see one frame of a 1e-15 difference, the law spec does); the
  seed in the strip's rounding, `octaves * elapsed / gate` (SURVIVED the first law spec, whose 24 semitones made every
  product exact; the 7-semitone rows were added, then red); the step off by one frame of the base (red: the
  block-framing row and the law rows; a seed one frame late per block survived the framing row, being the same at
  every framing, and is red on the law rows); the hold reading the moved gate (red: both held-realtime rows);
  `classic()`'s accelerate reading `penv.semitones` (red: the oracle rows, the door parity, the render-parity
  engagement row); accelerate nested outside the vibrato (red: `ClassicTailSpec`, `IgnitorRegistryTest`); the slot
  defaulting to 1.0 (red: the guard, the bare row, `ClassicTailSpec`); sprudel not writing the slot (red:
  `ClassicSlotParamsSpec`, door parity, `SprudelVoiceDataSpec`); `Ignitor.slot.accelerate` the onepole slot (red:
  `KlangScriptClassicDoorParitySpec`); the rig not writing the slot (red: the oracle rows, the block-framing row); the
  arm never gating (red: the guard; the 0 row stays green, being a fold). Review round 1 (`tmp/reviews/pp3-r1-A.md`,
  `pp3-r1-B.md`, 0 MAJOR): the split counting from index 0 (`else -> framesToGate`, A's mutant A1, which survived the
  whole suite) is red on the new first-window row; a zero gate writing 1.0 again (A's A3) is red on the zero-gate row.
- **Cost** (checklist (c); one voice through `VoiceFactory`, reviewer B's probe of step 2, HEAD / tree / HEAD again as
  the control, medians of 3 rounds; `$S/pp3cost/`). Off (no `accelerate`): render free on both hosts, build +100 B per
  voice on V8 and +56 B on the JVM (one more gated node in `classic()`'s tree). On, V8 production test bundle, render
  per 128-frame block: a saw mid-glide +8 percent pinned (1817 to 1968 ns; control 0.92) and +17 percent unpinned
  (1814 to 2119 ns; control 0.99), about +150 to +300 ns; sgpad (two pitched sources) +21 and +22 percent (about
  +650 to +700 ns; controls 0.99 and 1.02); a supersaw +6 to +14 percent, about +300 to +680 ns (reviewer B's two
  focused runs at load 6.5, controls 0.985 to 1.03; my own run read +2 to +3 percent inside wider controls, 1.05 and
  1.03); a saw past its gate (the hold) +5 and +15 percent against controls of 0.98 and 1.17, so inside the noise
  (reviewer B: inconclusive too); render bytes
  unchanged (the held saw's 2.1 KB per block is the release's, the same on HEAD). Build +1.8 to +1.9 KB per voice.
  JVM: render +9 percent for the saw (987 to 1072 ns), +6 percent sgpad, +2 percent supersaw, -2 percent for the
  hold (the hold writes a constant), allocation-free; build +1.2 KB per voice. All under 0.03 percent of a 2.67 ms
  block. The cause is step 2's: the strip wrote one buffer per voice, the tree adds the memo and a
  `ModApplyingIgnitor` per pitched source.
- **Suites.** `audio_bridge` jvmTest 146 and jsTest 265; `audio_be` jvmTest 2,432 and jsBrowserTest 2,324 (2,428 before the 7-semitone law rows and round 1's two rows); `sprudel`
  jvmTest 3,483 (486 skipped); `klangscript-libs` jvmTest 832 and jsTest 609; `BuiltInSongsSmokeTest`,
  `SongBenchmarkCasesCompileSpec`, `DslDocExamplesSpec` green; `compileTestKotlinJs` of `audio_bridge`, `audio_be`,
  `sprudel`, `klangscript-libs` and the root green.
- **Listening pair:** `tmp/listening/pp-step3/` (`kokon-before.wav`, `kokon-after.wav`, `README.md`).
- **Review round 1** (`tmp/reviews/pp3-r1-A.md`, `tmp/reviews/pp3-r1-B.md`; 0 MAJOR, clean), applied: the zero gate
  holds the target (A1, Q27) and its law row; the first-window row (A2); `AbsoluteFreqPitchModSpec`'s KDocs name `fm`
  as the one strip door (A3); the temporary regrouping with sprudel `fm` and `penv` + `accelerate` + `fm` among the
  ended ones, with the full-engine sizes (A4, B NIT 1); the History lines and the `classic()` KDoc name every
  exception (A5); section 3's accelerate row measured, not assumed, and checklist (b) reworded (B MINOR 1, the third
  recurrence); the supersaw cost (B NIT 2); `engine-follow-ups.md` item 10b, the long-release boxing lead (B NIT 3);
  braces on the node's `for` bodies (N1); `voice-takeover.md`'s strip list (N2). The zero gate is a render change, so
  the corpus ran again (`tmp/naming/corpus-pp-s3b.txt`): every row identical to step 3's, live Kokon included, and
  Kokon from HEAD's text still `6d22988a5668b97f`. `audio_be` and `sprudel` jvmTest green.

#### Step 3b. The FM modulator follows the pitch (decision D1, M, a sound change for some authored trees)

**The rule** (maintainer, 2026-10-08): any pitch modulation that reaches an `fm` node's carrier also reaches its
modulator, so the operator moves as one and the ratio stays exact. The first candidate is option (b) of D1: the `Fm`
arm of the DSL runtime builds its modulator under the outer `accumulatedMod`. It is a candidate, not a given: the
scenario matrix below decides whether it gives the rule in every shape. Its own commit, before step 4, so step 4's
classic FM stage is built on the settled rule.

**The invariant every scenario checks:** under any pitch modulation, the modulator's instantaneous frequency divided by
the carrier's equals `ratio`, sample for sample. Read it from the two oscillators' phase increments in a probe seam,
not by ear. A pitch node that does NOT reach the carrier must not reach the modulator either.

**The scenario matrix, written as specs before the change** (they fail on today's tree where the rule does not hold
yet, which is the point):

1. **Sprudel**, each door alone and in combination (`vib`, `penv`, `accelerate`, and two or three together):
   - over the sprudel `fm` door on a built-in (`s("sine").fm(env = 300, h = 1.4)`);
   - over an authored fm instrument that ends in `classic()` (the `bell` of the question);
   - over an authored fm instrument that has its own `vibrato` or `pitchEnvelope` inside, plus a sprudel `vib` on top;
   - the realtime path: a held note with `vib`, then note-off mid-vibrato.
2. **Authored Ignitors**, the orders and combinations of `pitchMod`, `vibrato`, `pitchEnvelope` and `accelerate`:
   - a pitch node ABOVE the fm: `Ign.sine().fm(m, 3.5, 400).vibrato(6, 0.5)`, the same with `pitchMod`,
     `pitchEnvelope` and `accelerate`;
   - two and three pitch nodes nested above it, in different orders (`vibrato` inside `pitchMod`, and the reverse);
   - a pitch node INSIDE the carrier only: `Ign.sine().vibrato(6, 0.5).fm(m, 3.5, 400)`. Does the modulator follow?
     The structure says the vibrato wraps only the carrier. This shape needs the maintainer's ear and word before the
     spec is fixed; build it as a listening pair (follows, does not follow);
   - a pitch node inside the MODULATOR only: it must stay the modulator's own (the ratio then moves, by design);
   - an fm whose modulator is itself an fm (stacked operators): the inner modulator follows too;
   - two fm nodes summed (`plus`) under one vibrato: both follow, each keeps its ratio;
   - an fm under `superSaw`-style stacks and under a `detune`: the detune is pitch too.
3. **The classic stage:** `Ign.sine().classic()` with the `fm.*` slots and the `vibrato.*` slots written: the classic
   FM stage's modulator follows the classic vibrato (the sound change for sprudel `fm` plus `vib`).

**Proof.** The invariant specs green after the change; the corpus bit-identical (no corpus song has an fm under a pitch
node; the corpus run confirms it); listening pairs for the bell under `vib`, for scenario 2's "inside the carrier"
shape, and for sprudel `fm` plus `vib`.

**Stopped for a decision (2026-10-09, worker; no code in the tree).** The matrix is written
(`FmModulatorFollowsPitchSpec`, 43 rows; report `tmp/reviews/pp-step3b-report.md`). HEAD fails 37 rows. Candidate (b)
fails 3: an outer mod that reads `Freq` (a vibrato rate on the note, an audio-rate `pitchMod`, an outer fm) keeps its
memo's freq key, and the modulator asks for it at `f x ratio`. It then renders twice per block and breaks the
CARRIER's own modulation too (measured: off by up to 0.054, 0.020 and 1.73 in the ratio, where HEAD reads 0.0). The
proposal, a wrapper through which the modulator reads the outer mod at the carrier's frequency (pinned by the fm each
block), passes all 43 rows and the whole `audio_be` jvmTest. It waits for the maintainer: the mechanism (a runtime
handshake, the stone rule), and whether an fm over an fm's carrier counts as pitch modulation (two modulators on one
carrier: "follows" is the rule as written and keeps step 4's `s("sgbell").fm(...)` as the strip plays it today;
"parallel" is HEAD). Listening: `tmp/listening/pp-step3b/` (the bell under `vib`, the inside-the-carrier pair, the
two-modulator pair). Patches: `3b-PROPOSAL-carrier-freq-mod.patch` and `3b-candidate-b-as-written.patch` in the
session scratchpad's `patches-pp/`.

#### Queued beside the pipeline: sprudel's `analog(amount)` becomes `analog(character)` (Q25)

The maintainer, 2026-10-09: "yes rename to character". `amount` is the distort drive only (Q21), and `analog` is a
character scale (Q22). A small rename of the parameter on every `analog` door (parameter parity): sprudel's `analog(amount)`, the
KlangScript filter builders' `analog(amount)` (`EffectBuilders.kt`) and the oscillator builders' `analog(analog)`
(`IgnitorBuilders.kt`) all become `analog(character)`, with KDoc and a row in `docs/retired-names.md`; positional
`analog(4)` is unchanged. Done between two pipeline steps, so it never shares a
tree with a running worker.

#### Queued beside the pipeline: `variants` accepts plain numbers (Q23)

The maintainer, 2026-10-09: "yes make numbers constants". `Ign.variants(400, 1200, 3000)` and
`Ign.variants(1, Ign.sine())` become valid: a number child is converted to a `Constant` at the door, the same strict
conversion with one more accepted type (D10's rule: a plain number wherever a constant value is accepted). Both doors
(KlangScript and Kotlin), a door-parity row; `StrictArgumentConversionSpec`'s "type error" row becomes "a number child
is a constant"; anything else that is neither a number nor an Ignitor stays a type error. Done between two pipeline
steps, with the `analog(character)` rename.

#### Queued beside the pipeline: the PolyBLEP texts (Q16)

The maintainer, 2026-10-09: "keep PolyBLEP in the credits, as once we used it. Clean up the other references where
not needed". Done now: `/code-style` section 9 and 16, the music-writing ignitor reference. Queued (they sit in
`audio_bridge` / `audio_be`, built by a running worker): the Zawtooth KDoc (`IgnitorDsl.kt:464`, "an instant reset,
no flyback") and the three `IgnitorsTest` row names and comments (222, 225, 262, 268, 410) that credit PolyBLEP for
softened peaks. `CREDITS.MD` and the in-app Credits page keep the entry.

#### Queued beside the pipeline: the warehouse panel shows the reverb counters (Q17)

The maintainer, 2026-10-09: "add them to the panel". `WarehouseStats.reverbFailures` and `reverbDropped` cross the
wire already; `src/jsMain/kotlin/comp/PlayerWarehouseStats.kt` shows only their ring twins (`ringFailures` at :143).
Add the two reverb counters beside them, with the same hover texts ("allocations that failed (out of memory)", and the
dropped one's). UI only.

#### Step 4. FM (M to L, a listening pair)

- `Slots.fm` (`fm.ratio`, `fm.depth`, `fm.attack`, `fm.decay`, `fm.sustain`, and `fm.release` with sprudel's
  `fm(release = ...)`, decided by D3, default 0.0, today's sound; on both doors with a door-parity row); `classic()` places `Fm` innermost with a
  `Sine(analog = Constant(0.0))` modulator (an unset `Sine` reads the `analog` slot and would drift). `VoiceData` loses
  `fmh`, `fmAttack`, `fmDecay`, `fmSustain`, `fmEnv`; `FmRenderer`, `Voice.Fm`, `Voice.Envelope` and `EnvelopeCalc.kt`
  go (`calculateControlRateEnvelope`, `controlRatePos`, `prepareControlRateEnvelope` have no caller left).
- Specs: `MidBlockOnsetControlRateSpec` loses its last subject (its F3 clamp lived in `controlRatePos`) and retires;
  `ModulatorPhaseWrapSpec`'s strip rows move to the node; the block-framing ledger's E11 closes and the classic FM door
  joins the bit-identical list (per-sample envelope, like the Ignitor `fm with envelope` row).
- Proof: not bit-identical (section 3, four causes). Listening pairs: an enveloped bell
  (`s("sine").fm(env = 300, h = 1.4, attack = 0.001, decay = 0.5, sustain = 0)`), a slow attack, the same door on a
  noisy or `analog > 0` instrument (the rng shift), and on `sgpad` (the detune residue). A tolerance row for the
  envelope-free case. Corpus identical (control: no song writes `fm`).

#### Step 5. The strip's shell, and the package (M, bit-identical)

`PitchPipelineBuilder.kt` goes; `BlockContext.freqModBuffer` and `freqModBufferWritten`, `Voice.RenderContext
.freqModBuffer`, its allocation in `VoiceScheduler` and the `VoiceFactory` parameter go; `IgniteRenderer` stops bridging
(`phaseMod` is null at the root from now on; `ModApplyingIgnitor` is its one writer, and the `IgniteContext` KDoc says
so); `ModBlockingIgnitor`'s KDoc names one door instead of two; `Voice` runs Ignite, (teardown fade), Send. The package
`voices/strip/` dissolves (D7). Docs: `audio/ref/voice-synthesis.md` "Processing Order", `audio/CLAUDE.md`'s key-files
row, `audio/MEMORY.md` ("The pitch stage stays outside the tree" goes), `docs/audio-backend-file-map.md`, a line in
`docs/plans/signal-flow-redesign.md` section 5 and in the block-framing plan's header. The published pages, in ONE edit
at the end of the pipeline and in the public voice (`/public-voice`; review round 1 of step 1, A4): the whitepaper
`src/jsMain/resources/klang-whitepaper.html:1733-1735` ("the engine runs the pitch calls ... in front of every
instrument"), `whitepaper/fig-three-owners.html:202`, `whitepaper/fig-classic-stages.html:166` and its `STAGES` list
at `:195` (no pitch stage), `whitepaper/fig-two-senders.html:374-378` (`VoiceData`'s fields in declaration order, the
cut fields included; line numbers as of step 1). And the planning texts that still name the strip as live:
`docs/tasks/voice-takeover.md:259-261` (`VibratoRenderer` as a sibling) and `docs/plans/aaa-production-tricks.md:65`
("vibrato, accelerate, pitch envelope, fm ... still outside the tree"). Proof: pure removal, the corpus
identical; about 38 files name `freqModBuffer`, most of them specs building a `RenderContext`.

### 5. The edges

- **Sample voices.** They get the strip today (with `freqHz` = the sample's recorded pitch). The sample instrument is
  `builtInVoice(IgnitorDsl.Sample)` = `Sample.pregain().classic()`, so it gets the four stages with no extra work; the
  `Sample` arm applies the mod (`applyMod`) and `SampleIgnitor` reads `rate * phaseMod[i]` as before. `Freq` answers
  the recorded pitch on both paths, so FM on a sample keeps running at the sample's pitch, not the note's (a quirk
  kept, recorded). Each door step adds a `SampleInstrumentSpec` row (a sample at rate 1.0 with the door equals the
  built-in `sine` with the door).
- **Instruments without `classic()`.** They get the strip today and will ignore the pitch doors, like every other door
  (signal-flow plan section 5: a pattern fills slots, never adds structure). The migration for an author is to place
  the slot-fed stage in the instrument, `x.vibrato(Ignitor.slot.vibrato.rate, Ignitor.slot.vibrato.semitones)`, and the
  editor's "declares no slot" diagnostic names it. Corpus: every door user plays a built-in or a Kokon instrument that
  ends in `classic()`; the corpus run of each step confirms it (D6).
- **The realtime path.** Unchanged in behaviour: the pitch envelope and the FM envelope read the gate per block
  (`IgniteContext.gateEndFrame`, derived from `VoiceLimits` by `IgniteRenderer`), as the strip read the limits per
  block, so a note-off moves their release; the vibrato has no gate; accelerate's base is a `val` that `releaseGate`
  never touches, so a held voice stays inert. A `RealtimeVoiceSpec` row per enveloped door (note-off mid-envelope).
- **The block-framing invariance specs.** `BlockFramingInvarianceSpec.renderVoice` renders a BARE `IgnitorDsl.Sine()`
  with `dataMod`; after the move a bare tree ignores the doors and the P4 rows would compare silence with silence (the
  positive-control row turns red, as it should). The rows move to `IgnitorDsl.Sine().classic()` and write slots: the
  vibrato and penv rows stay bit-identical, accelerate stays at 1e-11, FM joins the bit-identical list (E11 closed).
  `MidBlockOnsetControlRateSpec` retires with `EnvelopeCalc` (step 4).
- **The NaN task** (`bugfix-non-finite-pitch-strip-and-signals.md`). Its section 1 DISSOLVES: the four raw amounts
  become slots, sprudel's `classicSlotParams` drops a non-finite value at the boundary, the engine's `Param` leaf reads
  a non-finite override as unset (the slot default: off for `accelerate` and `fm.depth`, `VIBRATO_RATE_HZ`, `FM_RATIO`),
  and the nodes keep their own `finiteOr`. Close it with step 4, with one NaN row per slot (mandatory tier). Its
  section 2 (the `pitchMod` deviation signal and FM's modulator output carrying a NaN sample) is about SIGNALS and stays
  open; sprudel's FM modulator is a sine, so the FM half is not reachable from sprudel.

### 6. The wire fields, and who reads them

Sixteen fields of `audio_bridge/.../VoiceData.kt` (and its `empty`): `accelerate`; `vibrato`, `vibratoMod`;
`pAttack`, `pDecay`, `pSustain`, `pRelease`, `pEnv`, `pAttackCurve`, `pDecayCurve`, `pReleaseCurve`; `fmh`, `fmAttack`,
`fmDecay`, `fmSustain`, `fmEnv`.

- **Writer**: sprudel's `SprudelVoiceData.toVoiceData()` only. The sprudel-side fields and groups (`SvdPitchMod`,
  `SvdPitchEnv`, `SvdFm` in `SvdGroups.kt`) STAY: they are the query path's typed fields; only the wire boundary
  changes, in `_classic_slot_params.kt`. Their accessors take the decided names with the door (D4: `penv.semitones`,
  `fm.ratio`, ...).
- **Engine reader**: `VoiceFactory.makeVoice` only.
- **Frontend**: no UI reads them (no editor tool for these doors; `klang-worklet.js` is a build artifact).
- **Mentions to correct**: `constants/PitchModDefaults.kt` (comment), `IgnitorDsl.kt` (the vibrato KDoc's
  `vibratoMod()` unit line), `audio/ref/data-model.md`.
- **Specs**: `audio_be`: `BlockFramingInvarianceSpec`, `StripPitchEnvelopeParitySpec`, `VibratoConsistencyTest`,
  `PitchModFactoriesSpec`, `ModEnvelopeDefaultCurveSpec`, `FastExp2Spec`, `ClassicVoiceRig`, `SharedScratchSpec`,
  `FmSynthesisTest`, `PitchModulationTest`, `AccelerateSemitoneLawSpec`, `ModulatorPhaseWrapSpec`,
  `MidBlockOnsetControlRateSpec`, `RealtimeVoiceSpec`, `VoiceBagGuardSpec`. `sprudel`: `SprudelVoiceDataSpec`,
  `LangPitchEnvelopeSpec`, `LangPenvCurvesSpec`, `LangNewFeaturesIntegrationSpec`, `LangFieldAccessorsSpec`,
  `VoiceDataAliasingSpec`, `LangPitchParamNamesSpec`, `LangDoorFormsSpec`. JS: `WireCodecRoundTripSpec`
  (`audio_bridge`), `WorkletWireCodecRoundTripSpec` (`sprudel`); compile `compileTestKotlinJs` of every module before
  calling a step migrated (verification.md). Benchmarks: `VoiceDataCopyBenchmark`, `IgnitorBenchmark` (both use
  sprudel's builder, so they compile unchanged and now measure the slot path).

### 7. The second step: composing the tree's own nodes from primitives

After step 5. The doors and the nodes stay as descriptions; the runtime arms compose, the tremolo's pattern
(`../tasks-archive/2026-10/20261002-tremolo-as-composition.md`).

- **7a. One exponential pitch primitive, in semitones** (ratio `2^(x/12)`), a new node on both doors with a door-parity
  spec and an oracle law spec (M). The existing `pitchMod` stays: it is LINEAR (deviation, `value + 1`), which is FM's
  natural law, so the two are two concepts, not two words for one (D8 names it).
- **7b. Vibrato composed** (M): `Ignitors.sine(rate, analog = 0)` times the depth into the primitive; the gate stays
  (depth leaf `<= 0`); `VibratoModIgnitor` goes. Not bit-identical, for two reasons: the LFO sine draws its drift seed
  from the voice rng on its first block, the first time a pitched source renders the mod (other random values in a
  voice that also draws at generate time: a noise layer beside a pitched one, drift lanes at `analog > 0`; a
  noise-only voice never renders the mod and is untouched), and `(sin * depth) / 12` rounds once differently from
  `sin * (depth / 12)` (about 1e-16 in the ratio). A listening pair plus a tolerance row; the corpus attributes each
  moved song to one of the two causes.
- **7c. The vibrato `range`** (S, with 7b or right after; maintainer, 2026-10-06): the script door becomes
  `vibrato(rate, semitones, v => v.range(from, to))`, the flat Kotlin door takes `rangeFrom`, `rangeTo`, the node two
  appended fields with defaults -1 and 1 (constants in `audio_bridge/constants/`, the wire golden regenerated), no
  clamp. The literal default builds NO range node (a built `range(-1, 1)` is not the identity in floating point), so
  the default renders 7b's bits. The door-shapes table row changes from `rate, semitones | none`.
- **7d. Pitch envelope composed** (S to M, after a spike): `constant(amount)` shaped by the envelope law into the
  primitive. The chain `adsr` is the AMPLITUDE host (the level floors at 0), the pitch law is raw; the product
  `amount * level` and the `/ 12` match today's bits, so the spike checks whether a negative sustain is the only
  difference, and whether the amplitude host's shortcuts keep `renderPitchEnvelopeRatios`' settled-block cost (D10).
- **7f. `progress()` as a public signal, and curves on it** (maintainer, 2026-10-09; design open). The primitive under
  the composed accelerate is offered to authors, and it does NOT stop at 1: 0 at the onset, 1.0 at the gate close,
  growing on after it ("then one can build effects that only sound in the release tail"); accelerate clamps it at 1
  internally for its hold. The maintainer: "it also opens the door to tweening-like functions or general curves,
  other than adsr curves". A tween is a 0-to-1 ramp shaped by a curve, the same object as the signal-graph plan's
  tween (`../plans/future/signal-graph-engine.md` 6.8: one automation write with a duration and a curve), so one
  curve vocabulary should serve the `adsr` stages (`AdsrCurves`: linear, square, cube, scurve, invsquare,
  exponential), `progress()` and later tweens. Open before it is designed (`_maintainer-questions.md` Q13):
  - after the gate, does progress count in gate lengths (as asked) and/or is there a seconds-based `sinceGate()`
    beside it (the provisional units rule);
  - live MIDI notes: today a held note's base is a far horizon (progress about 0, never moving at note-off, decided
    2026-08-29); release-tail effects need an answer there;
  - how a curve is applied to a signal (`progress().curve("scurve")`, `.ease(...)`), on both doors.
- **7e. Accelerate composed, FM stays a node** (D11, revised 2026-10-09: accelerate from `progress()` and
  `pitchModSemitones`; FM keeps its node for D1). The original reasoning: Accelerate's law is a per-block seed with per-sample stepping and one
  reader; a `progress` primitive would be a node for one use. FM is already a composition (any modulator tree), and its
  law (linear deviation, the index envelope, the freq bypass) has no second reader.
- **The start phase**: no `phase` knob on the vibrato for now (D9).

### 8. Decisions for the maintainer

- **D1. A pitch door and the instrument's `fm` modulator.** **DECIDED (maintainer, 2026-10-08): option (b), the whole
  operator moves.** "The user would expect the whole bell to wobble ... whatever the solution is, the fm must move
  along with the general pitch shift like vib or pitchMod. I think we need extensive test scenarios here: one set for
  sprudel, and a second for how different combinations and orders of pitchMod and vib behave on authored Ignitors."
  The rule: any pitch modulation that reaches an `fm` node's carrier also reaches its modulator, so the ratio stays
  exact. That includes the classic FM stage's own modulator under the classic vibrato (the strip never bent it, a sound
  change for sprudel `fm` plus `vib`; no corpus song writes `fm`). Done as its own step, 3b, with the scenario matrix
  there. The question as it was put: today the strip's root `phaseMod` also bends an `fm` node's
  modulator (and musical oscillators in parameter positions); a tree pitch node never did, and the classic stages will
  not. Options: (a) accept the tree's semantics, record it; (b) the `Fm` arm builds its modulator under the outer
  `accumulatedMod` (one line), so any pitch modulation above an `fm` bends the whole operator and keeps the ratio
  harmonic; for a sprudel door over an fm instrument that is today's sound for the modulator, but it also changes
  authored trees with their own pitch node above an `fm`, and it would bend the classic FM stage's own modulator under
  vibrato (the strip never did) unless FM moved outermost, which costs the grouping; (c) a root scope node that sets
  `phaseMod` for the whole subtree: bit-identical everywhere, a second mechanism, against "complexity is the enemy".
  **Recommendation: (a) now** (no corpus song is affected), (b) as its own small item for the ear later.
- **D2. One accelerate base for both doors.** **DECIDED (maintainer, 2026-10-08): the gate, for both doors** ("gliding
  to the gate close is the correct behaviour"); step 3 is rewritten for it. **A zero-length gate holds the target from the first frame** (Q27, confirmed by the maintainer 2026-10-09: "yes
  this is fine"). **And the hold (maintainer, 2026-10-09):** the node
  kept rising past the gate at the same rate (+12 st over a 1280-frame gate read 2.0 at the gate, 4.0 at 2560, 8.0 at
  3839; `tmp/reviews/pp-step3-report.md`), so step 3 adds the hold: from the gate on the node writes its target,
  `2^(semitones / 12)`, through the release. Frames before the gate keep their bits. The Ignitor door's sound changes
  only for an authored `accelerate` under a release tail, after the gate (no corpus song). **Sound steps are committed
  on the `pitch-pipeline` branch after review; the branch is merged only after the maintainer's listening round**
  (maintainer, 2026-10-09). The question as it was put: the strip glides over onset to scheduled end (release tail included); the
  Ignitor node over the gate. **Recommendation: the strip's base for both** (one law; every song hears it today; the
  glide reaches its target exactly where the voice ends). The Ignitor door changes sound; no corpus song uses it. The
  alternative (the gate for both) changes every song's `accelerate`, Kokon's `strike` (a long release) the most.
- **D3. FM moves onto the node's law** **DECIDED (maintainer, 2026-10-08): yes to both** (the node's law with listening
  pairs, and sprudel's `fm` gets a `release`, slot `fm.release`, default 0.0). (per-sample envelope, closing E11; the rounding order; the modulator's rng draw;
  the detune residue) as a sound change proven by listening pairs, rather than a strip-faithful copy first.
  **Recommendation: yes.** With it: give sprudel's `fm` a `release` (and the `fm.release` slot, default 0.0, today's
  sound), the surface half of E11; the Ignitor door already has it, so this closes a parity gap. Recommendation: yes.
- **D4. Slot names.** **DECIDED (maintainer, 2026-10-08): align the names; keep the namespacing.** "I like the
  namespacing idea of `fm.xxx`, so the xxx parts must match the names on the Ignitors. Probably the Ignitors already
  have the more stable and better names, but worth a check for each param, so we get good and concise names." This
  turns the `classic()` rule "`<door>.<param>` after sprudel's readers" around for the parameter part. Before step 1
  places a slot, a per-parameter naming check proposes one name per concept (the table is in
  `_maintainer-questions.md`, Q9a, for the maintainer's word); steps 1 to 4 then use those names.
  **The names (maintainer, 2026-10-08, Q9a):** `vibrato.rate`, `vibrato.semitones`; `accelerate` (flat);
  `penv.semitones`, `penv.attack|decay|sustain|release`, `penvCurves.attack|decay|release`; `fm.ratio`, `fm.depth`,
  `fm.attack|decay|sustain|release`. The namespace stays the sprudel door's name (`penv`, as `lpf`). **The sprudel
  doors follow** (the parity rule): `vib(rate, semitones)`, `penv(semitones, attack, decay, sustain, release)`,
  `fm(depth, ratio, attack, decay, sustain, release)`; the old parameter names (`vib`'s `depth`, `penv`'s `amount`,
  `fm`'s `h` and `env`) are removed, not aliased, each in the step that moves its door, with a door-parity row and an
  entry in `docs/retired-names.md`. Positional calls are unchanged. `pamt`, `penv`'s short alias, is retired too (maintainer,
  2026-10-09: "drop pamt"; it abbreviates the retired "amount"). **The older `classic()` slots** (`lpf.freq`,
  `tremolo.depth`, `crush.amount`, ...) get the same check as its own step AFTER this pipeline, because a renamed slot
  also changes `ignp("...")` calls in songs (`../tasks-archive/2026-10/20261009-classic-slot-names-check.md`, done 2026-10-09, pulled ahead of this pipeline). The question as it
  was put: slot names follow sprudel's readers, so the FM slots say `h` and `env` where the node says `ratio` and
  `depth`, and the vibrato slot says `depth` where the node says `semitones`. A naming asymmetry that already exists
  between the doors. **Recommendation: keep the readers' names (the `classic()` rule) and record the asymmetry**; a
  rename is its own task.
- **D5. Cut each door's wire fields in its own step**, not all at the end. **DECIDED (maintainer, 2026-10-08): yes.** **Recommendation: per door**: once its
  renderer is gone a field has no reader, and a dead field invites a second producer. (The task text said "when the
  last one moves"; this is the one deviation from it.)
- **D6. Bare instruments lose the pitch doors.** **DECIDED (maintainer, 2026-10-08): accepted.** "Sprudel is but one
  driver of the engine, and it needs a little knowledge to author instruments that can be used in sprudel:
  `.classic()` is all it takes. Might also be a thing once we introduce other patterns, but this is ok." The original
  recommendation: accept (one rule for every door), migrate by
  placing the slot-fed stage; no corpus song is affected (each step's corpus run confirms it). The alternative, a
  helper that places the four stages alone (`pitchDoors()`), is a new surface on two doors for no current user.
- **D7. The `voices/strip/` package.** **DECIDED (maintainer, 2026-10-08): dissolve it into `voices/`.** After step 5 it holds `BlockContext`, `BlockRenderer`, `IgniteRenderer`,
  `SendRenderer`. **Recommendation: dissolve it into `voices/`** (flat directories, `/code-style` section 3), rather than
  rename it.
- **D8. (step 2) The name of the semitone pitch primitive** **DECIDED (maintainer, 2026-10-09): `pitchModSemitones`
  is added; `pitchMod(mod)` stays as it is**, the linear one (`frequency x (1 + mod)`: 1.0 is an octave up, -1.0 stops
  the oscillator; no song or page uses it today). Both doors, a door-parity row, the KDoc of each naming its unit. The
  question as it was put: the name of the semitone pitch primitive, and that `pitchMod` stays as the linear one.
  Recommendation: keep `pitchMod`; name the new one for its unit (for example `pitchSemitones`), the maintainer's word.
- **D9. (step 2)** **DECIDED (maintainer, 2026-10-09): add the vibrato `range` and `phase` to sprudel too, if it stays
  small** (two `classic()` slots each, the door parameters, parity rows), right after 7c. The question as it was put: **No vibrato `range` on sprudel and no slot for it in `classic()`** (the tremolo's precedent, "not in
  sprudel yet"); **no `phase` knob on the vibrato for now**. Recommendation: both as stated.
- **D10. (step 2)** **DECIDED (maintainer, 2026-10-09): compose it** ("let us use the composition approach again"), the
  tremolo's way; the spike confirms the same sound and cost first, and if a negative sustain is the only difference,
  the `adsr` gets what it needs to stay unclamped for pitch. The question as it was put: **Compose the pitch envelope through the amplitude `adsr`** only if the spike shows the negative-sustain
  floor is the only difference; otherwise keep `PitchEnvelopeModIgnitor`. Recommendation: spike first.
- **D11. (step 2)** **Maintainer, 2026-10-09: "if we can represent them through other primitives, they should leave,
  same as the tremolo node did."** So accelerate leaves: `pitchModSemitones(constant(semitones) * progress())`, with a
  new note-progress primitive (0 to 1 over the gate, then held at 1), useful beyond pitch (a filter that opens over the
  note). FM stays a node, because D1 (the modulator follows the pitch) needs the node to know which subtree is its
  modulator; a free composition would lose that. The primitive's name (`progress()`) awaits the maintainer's word
  (`_maintainer-questions.md` Q13). The question as it was put: **Accelerate and FM stay nodes.** Recommendation: yes (taste is also what we do not do).

### 9. Sizing

| step | what | size | proof |
|---|---|---|---|
| 0 | the gate on the four pitch arms | S | bit-identical (fold rows, corpus) |
| 1 | pitch envelope into `classic()` (and the shared plumbing) | M | bit-identical (door matrix, corpus control) |
| 2 | vibrato | S to M | bit-identical (corpus engages: 4 songs) |
| 3 | accelerate and one glide base | M | the sprudel door changes to the gate base (D2, decided): Kokon gets a listening pair; the Ignitor door unchanged |
| 3b | the FM modulator follows the pitch (D1, decided) | M | the invariant scenario matrix (sprudel and authored); listening pairs; corpus control |
| 4 | FM | M to L | listening pairs plus a tolerance row; corpus control |
| 5 | the strip's shell, the package | M | bit-identical (removal) |
| 7a | the semitone pitch primitive | M | oracle law spec, door parity |
| 7b, 7c | vibrato composed, its `range` | M | listening pair (rng, one rounding); `range` default bit-identical to 7b |
| 7d | pitch envelope composed | S to M | after a spike; bit-identical if only the negative-sustain floor differs |

### 10. Found while planning

- `IgnitorDsl.Fm.collectParams` collects `freq` twice (harmless while `freq` is the `Freq` leaf; a duplicate slot entry
  if it ever became a `Param`).
- The docs disagree on accelerate's base: `IgniteContext`'s KDoc and the realtime note-off record say the gate length,
  the strip renders over onset to scheduled end. D2 settles it.
- `AnalogDrift` draws twice from the voice rng when it is constructed, even at `analog` 0, so every oscillator added
  to a voice by a composition shifts the voice's later draws. Worth a line in `audio/ref/voice-synthesis.md` ("The
  voice rng"); making the lane lazy at `analog` 0 would move every song's draws and is its own decision.
