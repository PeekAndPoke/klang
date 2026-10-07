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
        ratio = s.fm.h,
        depth = s.fm.env,
        envAttackSec = s.fm.attack,
        envDecaySec = s.fm.decay,
        envSustainLevel = s.fm.sustain,
        envReleaseSec = s.fm.release, // or Constant(0.0), decision D3
    )
    val pitchEnveloped = IgnitorDsl.PitchEnvelope(
        inner = fmed,
        semitones = s.penv.amount,
        attackSec = s.penv.attack,
        decaySec = s.penv.decay,
        sustainLevel = s.penv.sustain,
        releaseSec = s.penv.release,
        attackCurve = s.penvCurves.attack,
        decayCurve = s.penvCurves.decay,
        releaseCurve = s.penvCurves.release,
    )
    val accelerated = IgnitorDsl.Accelerate(inner = pitchEnveloped, semitones = s.accelerate)
    val vibratoed = IgnitorDsl.Vibrato(inner = accelerated, rate = s.vibrato.rate, semitones = s.vibrato.depth)
    val onepoled = IgnitorDsl.OnePoleLowpass(inner = vibratoed, freq = s.onepole)
    // ... crush, coarse, distort, the filters, tremolo, adsr as today
}
```

**The slot names follow sprudel's readers**, the rule `classic()` already keeps (`<door>.<param>`, a single-knob door
flat like `onepole`): `vibrato.rate`, `vibrato.depth`; `accelerate`; `penv.amount`, `penv.attack`, `penv.decay`,
`penv.sustain`, `penv.release`; `penvCurves.attack|decay|release` (curve INDEX, default `MOD_ENV_CURVE`); `fm.h`,
`fm.env`, `fm.attack`, `fm.decay`, `fm.sustain` (and `fm.release`, D3). Defaults are the strip's, from the same
constants: the switches (`vibrato.depth`, `accelerate`, `penv.amount`, `fm.env`) default to 0.0, which the gate reads
as off; `vibrato.rate` is `VIBRATO_RATE_HZ`, `fm.h` is `FM_RATIO`, the pitch-envelope stages are
`PitchEnvelopeDefaults.kt`, the FM stages 0, 0, 1.0 as `VoiceFactory` reads them today.

### 3. Bit-identity, door by door

The rule of `audio/ref/verification.md` ("a decision that replaces an expression lists every clause of the old
one") applied; every clause not named here is kept.

| door | old expression (strip) | new (classic stage) | identity |
|---|---|---|---|
| pitch envelope | built when `pEnv` finite and `!= 0`; stages `(p* ?: PITCH_ENV_*) * sampleRate`; sustain non-finite reads unset; curves `?: MOD_ENV_CURVE`; gate from the voice's limits per block; `renderPitchEnvelopeRatios` multiplying into the buffer | gate off at a leaf amount `== 0` or non-finite; the node's arm with the same constants; the same `renderPitchEnvelopeRatios` writing, combined by `Times` | **bit-identical** (one law, one mapping, `x * p == p * x`). Only a NaN stage TIME differs: the strip passed it to `EnvelopeCore` (a zero-length stage), a slot reads it as unset (the default time) |
| vibrato | built when `vibratoMod > 0`; rate `vibrato ?: VIBRATO_RATE_HZ`; phase from 0, `(TWO_PI * rate) / sampleRate`, the one-subtract or full wrap; `fastExp2(fastSin(phase) * depth / 12)` | gate off at a leaf depth `<= 0`; `VibratoModIgnitor`: the same accumulator, increment, wrap pair and ratio, plus `safeOut` (the identity on a finite ratio) | **bit-identical**. A NaN rate poisoned the strip's phase; the slot reads it as unset |
| accelerate | built when `accelerate != 0` and `end > onset`; base = `endFrame - startFrame` (scheduled end, the release tail INCLUDED, a Double); per block `2^(octaves * rel / total)`, then `ratio *= 2^(octaves / total)` per sample | today's node: base = `voiceDurationFrames` (the GATE length, an Int) and `2^(octaves * (rel / total))` | **not identical as the node stands**: a different base and a different rounding. Bit-identical once the node takes the strip's base and expression order (step 3, decision D2). The per-block seed keeps the known float-reassociation class (P4: 5.3e-15 across onsets, 1.7e-13 across block sizes, bounded at 1e-11) on both |
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
   Only when the stage is built (`fm.env != 0`).
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
at its off value, or non-finite, is not built and the walk descends with the UNCHANGED `accumulatedMod`.

```kotlin
is IgnitorDsl.Vibrato -> {
    // Gate row "vibrato" (audio/ref/off-values.md): off at a leaf depth <= 0.
    if (semitones.gatedOff(ignitorParams = ignitorParams, cache = cache) { it <= 0.0 }) {
        return inner.buildIgnitor(ignitorParams, cache, accumulatedMod)
    }
    // ... as today
}
```

Rows: vibrato depth `<= 0`; accelerate `== 0`; pitch envelope amount `== 0`; FM depth `== 0` (a negative depth
renders on both hosts, so the off value is exactly 0). Each is a FOLD: a built stage at its off value outputs exactly
1.0 and every reader multiplies by it (`x * 1.0 == x`, also inside the oscillators' `phaseInc * phaseMod[i] * m`, the
sample's `rate * phaseMod[i] * m` and the Karplus `dl / phaseMod[i]`). Three consequences that are not folds, named
like the existing sibling rows of `IgnitorGateSpec`: a drawing sibling knob (a `perlin` rate under a literal-0 depth)
no longer draws; a gated FM no longer counts its modulator's release tail; a gated FM's modulator `Sine` no longer
takes its drift seed. Proof: `IgnitorGateSpec` rows (an authored `vibrato(5, 0)`, `accelerate(0)`,
`pitchEnvelope(0, ...)`, `fm(..., depth = 0)` render the same bits as the tree without the node, each with a positive
control at a non-zero value), four rows in `audio/ref/off-values.md`, and the corpus (predicted identical: no song
writes a literal-0 pitch stage).

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
  unwritten `penv.amount` is 0.0, the gate's off value.
- `audio_be`: `VoiceFactory` stops building `Voice.PitchEnvelope`; `PitchEnvelopeRenderer` goes;
  `renderPitchEnvelopeRatios` loses its `multiply` flag (one host left, it always writes).
- `klangscript-libs`: `Ignitor.slot.penv` and `Ignitor.slot.penvCurves` (`KlangScriptIgnitorClassicSlots.kt`) with
  their door-parity rows (two doors, one DSL).
- Specs: `StripPitchEnvelopeParitySpec` retires (one host) into an oracle row; the test rig `DoorFields`
  (`_typed_fields_as_slots.kt`) gains the pitch-envelope fields, as phase 3 step 9 did for the filters; the
  block-framing penv row moves to a `classic()` tree (below, section 5); `LangPitchEnvelopeSpec`,
  `LangPenvCurvesSpec`, `SprudelVoiceDataSpec` assert slot keys instead of fields.
- Proof: bit-identical, the matrix all 0.0; the corpus identical (control).

#### Step 2. Vibrato (S to M, bit-identical)

- `Slots.vibrato` (`vibrato.rate`, `vibrato.depth`); `classic()` places `Vibrato` OUTSIDE the pitch envelope (its final
  place). Sprudel writes both keys from its `pitchMod` group (`vib(4)` alone writes only the rate, the depth stays 0,
  no vibrato: the strip's behaviour). `VoiceData` loses `vibrato`, `vibratoMod`; `VibratoRenderer` and
  `Voice.Vibrato` go; `Ignitor.slot.vibrato` on the script door.
- Specs: `VibratoConsistencyTest` (strip against node) becomes an oracle row; the benchmark case
  `sine+vibrato+tremolo` (`IgnitorBenchmark`) now runs through the slot. Measure it: the strip wrote one buffer per
  voice, the tree writes one memo plus one `ModApplyingIgnitor` per pitched source (two scratch buffers and a copy).
- Proof: bit-identical. The corpus ENGAGES here: TetrisRemix (the lead and its brown layer, which ignores pitch on
  both paths), StrangerThings, IrishLamentTechno (built-in `saw`, `supersine`), Kokon (`spin`, `sing`, `soar`, one own
  `pitchEnvelope` per path, so the order flips and the bits do not). Predicted identical; the engagement mutant
  (depth divided by 12.5) must move exactly those songs.

#### Step 3. Accelerate, and one glide base (M, bit-identical for sprudel)

- `IgniteContext.voiceDurationFrames: Int` (its only DSP reader is the accelerate node) is REPLACED by
  `scheduledLengthFrames: Double`, the voice's onset to its scheduled end with the release tail included, set once in
  `VoiceFactory.buildVoice` and never moved by `releaseGate` (so a held realtime voice keeps its accelerate inert, the
  decided semantics of 2026-08-29).
- The node's law takes the strip's base and expression order (decision D2):

```kotlin
// One law for both doors: the glide spans the voice's scheduled length (onset to scheduled end, the release
// tail included), the base the strip used. Fixed at construction; a realtime note-off never moves it.
val octaves = finiteOr(Ignitors.readParam(semitones, freqHz, ctx), 0.0) / 12.0 // NaN-guard: non-finite reads as unset
val totalFrames = ctx.scheduledLengthFrames
// ... bypass when octaves == 0.0 or totalFrames <= 0.0, as today
var ratio = 2.0.pow(octaves * ctx.voiceElapsedFrames / totalFrames) // the strip's order: (octaves * rel) / total
val step = 2.0.pow(octaves / totalFrames)
```

- The slot is flat, `accelerate` (sprudel's reader), default 0.0; `classic()` places `Accelerate` between the pitch
  envelope and the vibrato. `VoiceData` loses `accelerate`; `AccelerateRenderer` and `Voice.Accelerate` go. KDocs to
  correct: `IgniteContext`, `Voice.releaseGate`, `VoiceLimits`, the comment in `VoiceScheduler`.
- Specs: `AccelerateSemitoneLawSpec` moves to the node; the block-framing accelerate row stays at its 1e-11 bound (the
  same per-block seed); about 97 `IgniteContext(` call sites take the new field (mechanical).
- Proof: the sprudel door bit-identical (corpus: Kokon's `strike`, which writes `ignp("release", 3.0)`, so the
  release tail is a large share of the base, a strong row; matrix: a long tail, a held realtime voice). The Ignitor door is a sound change
  for an authored `accelerate` (the base grows by the release tail, plus one rounding): no corpus song uses it
  (`WarmupVocabulary` only).

#### Step 4. FM (M to L, a listening pair)

- `Slots.fm` (`fm.h`, `fm.env`, `fm.attack`, `fm.decay`, `fm.sustain`, and `fm.release` with sprudel's
  `fm(release = ...)` if D3 says so, default 0.0, today's sound); `classic()` places `Fm` innermost with a
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
`docs/plans/signal-flow-redesign.md` section 5 and in the block-framing plan's header. Proof: pure removal, the corpus
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
  the slot-fed stage in the instrument, `x.vibrato(Ignitor.slot.vibrato.rate, Ignitor.slot.vibrato.depth)`, and the
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
  a non-finite override as unset (the slot default: off for `accelerate` and `fm.env`, `VIBRATO_RATE_HZ`, `FM_RATIO`),
  and the nodes keep their own `finiteOr`. Close it with step 4, with one NaN row per slot (mandatory tier). Its
  section 2 (the `pitchMod` deviation signal and FM's modulator output carrying a NaN sample) is about SIGNALS and stays
  open; sprudel's FM modulator is a sine, so the FM half is not reachable from sprudel.

### 6. The wire fields, and who reads them

Sixteen fields of `audio_bridge/.../VoiceData.kt` (and its `empty`): `accelerate`; `vibrato`, `vibratoMod`;
`pAttack`, `pDecay`, `pSustain`, `pRelease`, `pEnv`, `pAttackCurve`, `pDecayCurve`, `pReleaseCurve`; `fmh`, `fmAttack`,
`fmDecay`, `fmSustain`, `fmEnv`.

- **Writer**: sprudel's `SprudelVoiceData.toVoiceData()` only. The sprudel-side fields and groups (`SvdPitchMod`,
  `SvdPitchEnv`, `SvdFm` in `SvdGroups.kt`, the accessors `vibrato.rate`, `penv.amount`, `fm.h`, ...) STAY: they are the
  query path's typed fields; only the wire boundary changes, in `_classic_slot_params.kt`.
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
- **7e. Accelerate and FM stay nodes** (D11). Accelerate's law is a per-block seed with per-sample stepping and one
  reader; a `progress` primitive would be a node for one use. FM is already a composition (any modulator tree), and its
  law (linear deviation, the index envelope, the freq bypass) has no second reader.
- **The start phase**: no `phase` knob on the vibrato for now (D9).

### 8. Decisions for the maintainer

- **D1. A pitch door and the instrument's `fm` modulator.** Today the strip's root `phaseMod` also bends an `fm` node's
  modulator (and musical oscillators in parameter positions); a tree pitch node never did, and the classic stages will
  not. Options: (a) accept the tree's semantics, record it; (b) the `Fm` arm builds its modulator under the outer
  `accumulatedMod` (one line), so any pitch modulation above an `fm` bends the whole operator and keeps the ratio
  harmonic; for a sprudel door over an fm instrument that is today's sound for the modulator, but it also changes
  authored trees with their own pitch node above an `fm`, and it would bend the classic FM stage's own modulator under
  vibrato (the strip never did) unless FM moved outermost, which costs the grouping; (c) a root scope node that sets
  `phaseMod` for the whole subtree: bit-identical everywhere, a second mechanism, against "complexity is the enemy".
  **Recommendation: (a) now** (no corpus song is affected), (b) as its own small item for the ear later.
- **D2. One accelerate base for both doors.** The strip glides over onset to scheduled end (release tail included); the
  Ignitor node over the gate. **Recommendation: the strip's base for both** (one law; every song hears it today; the
  glide reaches its target exactly where the voice ends). The Ignitor door changes sound; no corpus song uses it. The
  alternative (the gate for both) changes every song's `accelerate`, Kokon's `strike` (a long release) the most.
- **D3. FM moves onto the node's law** (per-sample envelope, closing E11; the rounding order; the modulator's rng draw;
  the detune residue) as a sound change proven by listening pairs, rather than a strip-faithful copy first.
  **Recommendation: yes.** With it: give sprudel's `fm` a `release` (and the `fm.release` slot, default 0.0, today's
  sound), the surface half of E11; the Ignitor door already has it, so this closes a parity gap. Recommendation: yes.
- **D4. Slot names follow sprudel's readers**, so the FM slots say `h` and `env` where the node says `ratio` and
  `depth`, and the vibrato slot says `depth` where the node says `semitones`. A naming asymmetry that already exists
  between the doors. **Recommendation: keep the readers' names (the `classic()` rule) and record the asymmetry**; a
  rename is its own task.
- **D5. Cut each door's wire fields in its own step**, not all at the end. **Recommendation: per door**: once its
  renderer is gone a field has no reader, and a dead field invites a second producer. (The task text said "when the
  last one moves"; this is the one deviation from it.)
- **D6. Bare instruments lose the pitch doors.** **Recommendation: accept** (one rule for every door), migrate by
  placing the slot-fed stage; no corpus song is affected (each step's corpus run confirms it). The alternative, a
  helper that places the four stages alone (`pitchDoors()`), is a new surface on two doors for no current user.
- **D7. The `voices/strip/` package.** After step 5 it holds `BlockContext`, `BlockRenderer`, `IgniteRenderer`,
  `SendRenderer`. **Recommendation: dissolve it into `voices/`** (flat directories, `/code-style` section 3), rather than
  rename it.
- **D8. (step 2) The name of the semitone pitch primitive**, and that `pitchMod` stays as the linear one.
  Recommendation: keep `pitchMod`; name the new one for its unit (for example `pitchSemitones`), the maintainer's word.
- **D9. (step 2) No vibrato `range` on sprudel and no slot for it in `classic()`** (the tremolo's precedent, "not in
  sprudel yet"); **no `phase` knob on the vibrato for now**. Recommendation: both as stated.
- **D10. (step 2) Compose the pitch envelope through the amplitude `adsr`** only if the spike shows the negative-sustain
  floor is the only difference; otherwise keep `PitchEnvelopeModIgnitor`. Recommendation: spike first.
- **D11. (step 2) Accelerate and FM stay nodes.** Recommendation: yes (taste is also what we do not do).

### 9. Sizing

| step | what | size | proof |
|---|---|---|---|
| 0 | the gate on the four pitch arms | S | bit-identical (fold rows, corpus) |
| 1 | pitch envelope into `classic()` (and the shared plumbing) | M | bit-identical (door matrix, corpus control) |
| 2 | vibrato | S to M | bit-identical (corpus engages: 4 songs) |
| 3 | accelerate and one glide base | M | bit-identical for sprudel (Kokon); the Ignitor door changes (D2) |
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
