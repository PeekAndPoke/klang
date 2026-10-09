# Audio — Voice Synthesis

All types in `audio_be/src/commonMain/kotlin/voices/`.

## Voice

`Voice.kt`: one class for every voice; the instrument is its Ignitor tree.

### Processing Order

Since phase 3 step 9 (2026-09-27) every voice is an Ignitor tree, and the tree IS the instrument,
envelope and filters included. `Voice` runs its stages in sequence: **Pitch → Ignite → (teardown
fade) → Send**. Each stage is a list of `BlockRenderer`s (the interface and the pitch, ignite and send renderers
live in `voices/strip/`, `TeardownFadeRenderer` in `voices/`); `Voice.render()` iterates
the composed pipeline.

```
Pitch stage     (PitchPipelineBuilder → writes freqModBuffer)
  1. FM                 : frequency modulation
  (sprudel's pitch envelope, vibrato and accelerate left this stage in pitch pipeline steps 1 to 3: classic()'s
   pitch stages)

Ignite stage    (IgniteRenderer → writes audioBuffer)
  2. the instrument's Ignitor tree (oscillator or sample, and everything the tree holds)

Teardown fade   (TeardownFadeRenderer, unless the tree's root is a built amplitude envelope with a
                 static release; the one home of the rule is BuiltIgnitor.endsInEnvelope. adsrOff
                 switches classic()'s envelope off, and the node hands on its inner's answer: the
                 instrument's own static-release envelope ends the voice only when nothing built
                 sits over it, neither a stage of the instrument's own nor a classic() stage the
                 pattern wrote; otherwise the fade does)

Send stage      (SendRenderer → mixes to cylinder)
  4. pan + gain → cylinder mix
```

**The voice chain is `classic()`** (`audio_bridge/.../IgnitorDslClassic.kt`), a tail of slotted
Ignitor stages in the classic subtractive order:

```
this -> pitchEnvelope -> vibrato -> onepole -> crush -> coarse -> distort -> highpass -> bandpass -> notch -> lowpass -> tremolo -> adsr
```

The pitch stages sit at the front, directly on the instrument (`docs/tasks/in-progress/pitch-pipeline-into-the-tree.md`
section 2): their mods bubble down to every pitched source, so their place among the amplitude stages does not change
the sound, and their nesting is the retired pitch strip's grouping of the ratio product. Three pitch factors on one
path can regroup it (one rounding, about -270 dB): while some doors still run on the strip, and for good where two
of the instrument's own pitch nodes meet a door. A classic pitch stage bends an `fm` node's modulator with its
carrier, as every pitch node above an fm does (decision D1, pitch pipeline step 3b; see "FM: a pitch node means what
it wraps" below). Unlike the strip, it never bends a musical oscillator in a parameter position, such as a filter LFO
(plan section 2). The pitch envelope is the
Ignitor `pitchEnvelope` node, filled by the `penv.*` and `penvCurves.*` slots (sprudel's `penv`, `penvCurves`); the
vibrato is the Ignitor `vibrato` node, filled by `vibrato.rate` and `vibrato.semitones` (sprudel's `vib`).

Every knob is a slot (`<door>.<param>`) that the pattern fills through `VoiceData.ignitorParams`, and a stage
whose slot is at its off value is not built. Every built-in sound is `source.pregain().classic()`, every
sample voice is the same shape over `IgnitorDsl.Sample` (`IgnitorRegistry.builtInVoice`), and an authored
instrument gets the voice doors by ending in `.classic()`. A tree without `classic()` plays as it is: no
doors, no default envelope.

Compressor, ducking, phaser, delay, reverb, body and vowel are **cylinder-level** (Katalyst) effects,
not per-voice: applied on the orbit bus after all voices mix into the cylinder.

### Voice construction

`VoiceFactory` builds each `Voice` from `VoiceData`: the instrument's tree (`IgnitorRegistry.createExciter`,
or the sample instrument for a sample), the pitch pipeline from the typed pitch fields that are left (FM), and the stages
after the tree. `Voice` itself holds the lifecycle frames, `cylinderId`, `gain`, `pan`,
`katalystParams`, `cut`, the cull window and the pipeline.

**`Voice.gain` is stored, not guarded.** `Voice` keeps whatever it is constructed with, including
a NaN. The substitution of a non-finite wire value by 1.0 happens in `VoiceFactory`, which is the
only production path that builds a voice. Anything that adds a second construction path has to do
it there too, or `Voice.heard` (which compares against 0) and `SendRenderer.measurePeak` (which
scales by `abs(gain)`) go back to being wrong for a NaN.

### RenderContext

Passed to `Voice.render()` every block:

```kotlin
class RenderContext(
    val voiceBuffer: FloatArray,     // write output here (length = blockFrames)
    val freqModBuffer: FloatArray,   // shared per-block frequency modulation accumulator
    val orbits: Cylinders,              // reference for mixing and ducking
    val sampleRate: Int,
    val blockFrames: Int,
)
```

## Samples

A sample voice runs the sample instrument (`IgnitorRegistry.SAMPLE_INSTRUMENT`, phase 3 step 7):
`builtInVoice(IgnitorDsl.Sample)`, playing the voice's `MonoSamplePcm` through `ignitor/SampleIgnitor.kt`.
Its playback slots are `begin`, `end`, `speed` and `loop` (`IgnitorDsl.Slots.sample`), read where the
playhead is built; a loop region is sprudel's `loop().begin(x).end(y)`. A sample's own envelope
(`SampleMetadata.adsr`) fills the `adsr.*` slots the pattern left unset (`withSampleEnvelopeDefaults`).
Cut groups: a new voice in the same `cut` group ends the ones still sounding: each fades over 4 ms from the new
voice's onset (`Voice.cutOff`, the `Fading` state, `CUT_FADE_SECONDS`); one not yet started or culled ends at once.

## VoiceScheduler

`VoiceScheduler.kt` — manages voice lifecycle.

- Stores pending voices in a `KlangMinHeap` (priority queue by `startTime`)
- On each block: activates due voices, removes finished voices
- Sample resolution: when a sample voice is due but the PCM isn't loaded yet,
  sends `Feedback.RequestSample` to frontend and delays activation
- Solo: `SoloTracker` records "source soloed at amount a until t" from any event (control events included); a
  voice of a soloed source plays at `Voice.gainMultiplier` 1.0, every other voice at `1 - amount` of the strongest
  live solo, reached on a 1.5 s ramp (0 only at `solo(1.0)`); a change is ramped across one block in `SendRenderer`.
  The rules: `audio/MEMORY.md`, and `docs/tasks-archive/2026-10/20261009-bugfix-solo-rests-and-amount.md`

## Oscillators

`ignitor/Ignitors.kt` — oscillator + signal-generator factories (the `Ignitor` DSL). Sound names are
registered in `ignitor/IgnitorDefaults.kt` / `IgnitorRegistry.kt`. (There is no `osci/Oscillators.kt`.)

| Family           | Names                                                       | Notes                                                                                                                                                           |
|------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sine             | `sine`                                                      | Pure sinusoid (inherently band-limited)                                                                                                                         |
| Trapezoid shapes | `saw` `ramp` `square` `pulze` `tri`                         | ONE `waveTrapezoid` / `WaveVoiceState` engine (`WaveIgnitor`); finite-slope edges, no PolyBLEP, softens with pitch. `pulze` duty is audio-rate (PWM)            |
| Raw shapes       | `zaw`/`zawtooth` `zamp`                                     | `flankSamples = 0` → instant / aliased edges                                                                                                                    |
| Super (unison)   | `supersaw` `superramp` `supersquare` `supertri` `supersine` | ONE `UnisonStackIgnitor` (a `StackKind` per shape): detuned voice stack, center-dominant gains, per-voice drift, centroid-anchored tuning. `voices` / `freqSpread` / `analog` ignitorParams |
| Noise            | `noise` `pink` `dust` …                                     | White / pink / impulse noise                                                                                                                                    |

Per-oscillator character constants live in `ignitor/OscillatorTuning.kt`
(`SAW_*`, `PULSE_*`, `SUPERSAW_*`, `SUPER{RAMP,SQUARE,TRI,SINE}_*`).

## Modulation Sub-objects (inside Voice)

### Envelope (ADSR)

```kotlin
class Envelope(attack: Double, decay: Double, sustain: Double, release: Double)
// .process(frame, gateEndFrame) → Float gain multiplier
// Returns 0.0 when release is complete (voice done signal)
```

### Fm

```kotlin
class Fm(ratio: Double, attack: Double, decay: Double, sustain: Double, env: Double)
// ratio: modulator freq = carrier * ratio
// env: modulation depth in semitones (scaled by envelope)
```

## FM: a pitch node means what it wraps

Pitch pipeline step 3b (decision D1, the placement rule of 2026-10-09). A pitch modulation (a `vibrato`, `pitchMod`,
`pitchEnvelope`, `accelerate`, an outer `fm`, a `classic()` pitch stage) above an `fm` node moves the whole operator,
the note's pitch: the modulator follows the carrier and the ratio stays exact (a modulator with an absolute `freq`,
`Ign.sine(330)`, stays at its frequency, shielded like every absolute oscillator). On the modulator it moves the modulator
alone; on the carrier (`x.vibrato(...).fm(m, ...)`) the carrier alone. Sprudel's own `fm` door still runs on the
voice strip until pitch pipeline step 4: its modulator is a sine inside `FmRenderer` that no tree pitch stage reaches.

The mechanism: the `Fm` arm builds the modulator under the outer mod, read through a `CarrierFreqMod` that asks it at
the CARRIER's frequency, which the fm pins every block before it renders the modulator. Asked at the modulator's own
frequency, a mod whose knobs read `Freq` (a vibrato rate on the note, an audio-rate `pitchMod`, an outer fm) would
render twice per block and break the carrier's own modulation; pinned, the mod above the fm renders once per block for
every carrier pitch, and a chain of N fms costs N + 1 oscillator renders. The wrapper ignores the frequency it is called
with, so a pitch node INSIDE the modulator drops its freq key and renders once per block too, also over a forking
detune. One wrapper per fm node and outer mod, so `let f = x.fm(m); f + f` keeps `m` one instance. Guards: `FmModulatorFollowsPitchSpec` (the matrix) and `FmModulatorTopologySpec` (chains, nesting, sums,
parameter positions, shared lets, the render counts).

One shape the engine does not process: one fm whose carrier holds two pitches (`(x + x.detune(7)).fm(m)`). Its one
modulator serves both pitches and advances once per pitch per block; if the mod above the fm reads the note and the
modulator holds a pitch node whose knobs read no `Freq`, the second pitch's render even reads the first pitch's outer
mod (that node's memo was filled under the first pin). The author rule: give each layer its own fm,
inside its detune, and sum them, `x.fm(m1) + x.fm(m2).detune(7)`. A build-time diagnostic is its own task
(`docs/tasks/fm-above-forking-detune-diagnostic.md`).

## Envelope / voice-lifetime semantics

> **The longest amplitude tail wins. Modulator envelopes are best-effort within.**

Two classes of envelope, with different roles in deciding when a voice ends:

- **Amplitude envelopes** determine voice lifetime. The longest one wins.
    - The `IgnitorDsl.Adsr` nodes on the tree's spine, `classic()`'s `adsr` included (its
      release is the `adsr.release` slot). They wrap the audio signal directly, so cutting one
      off mid-decay would click.
    - Voice end frame = `gateEndFrame + releaseTailSec`, the tree's own release tail
      (`BuiltIgnitor.releaseTailSec`, floored at 0); a tree with no static answer takes `VOICE_ADSR_RELEASE_SEC`
      (0.05 s).

- **Modulator envelopes** do **not** extend voice lifetime:
    - Filter modulator envelope (the `attack, decay, sustain, release` slots of `lpf` / `hpf` / `bpf` / `notch`)
      — modulates filter cutoff at control rate.
    - FM envelope — modulates FM depth.
    - Pitch envelope — modulates pitch.
    - These run their attack/decay/sustain during the gate phase and start
      their release at gate-off, but get cut off when the voice ends if
      their release is longer than the amp release. No clicks result —
      they only modulate parameters, never the audio amplitude directly.

**To make a long filter sweep audible after gate-off, set an amp release
≥ the filter envelope's release.** Same applies to FM and pitch envelopes.
This keeps the API behaviour predictable: the amp envelope IS the voice
length — no implicit extension by modulators.

**Why this rule:** amplitude tails cut short produce audible clicks, so
the engine extends voice lifetime to cover them. Modulator tails cut short
just stop modulating — no artefact, just less colour. So the engine doesn't
spend CPU keeping a silent voice alive purely to finish a filter sweep
nobody can hear.

**Implementation reference:** `treeLifetime` in `voices/VoiceFactory.kt` (the tree's
`releaseTailSec`, found by the build); the one envelope law is `EnvelopeCore`.

### Silence culling (2026-09-15)

The scheduled lifetime above is an upper bound. A voice also ends itself EARLY once it is in
its release and its own output has stayed under the audibility floor for the cull window:

- **Measure:** `SendRenderer`, the last voice stage, keeps the block's peak `|output|`
  (the tree's output times `gain`; BEFORE the solo/mute multiplier, so a voice a solo faded out is not taken for
  a dead one) in `BlockContext.voiceOutputPeak`. A separate pass, run only on the blocks that
  read it (`BlockContext.measurePeak`): until the voice has been heard, then in the release; a
  heard voice pays nothing for the rest of its gate, `noCull()` voices pay nothing at all.
- **Decide:** `Voice.render`, after the stage loop, only when `blockStart >= gateEndFrame`.
  Silent frames accumulate (frames, not blocks, so the window has the same length at any block
  size and the cut lands within one block of the same frame); an audible block resets them; when
  they cover the window, `Voice.culled` is set, the voice is `Done` at that block and the scheduler counts it
  (`VoiceScheduler.culledVoicesTotal`) and removes it, keeping the list's order.
- **A culled voice ends at once** (lifecycle step 5, 2026-10-07). Until then it stayed listed as a ZOMBIE until
  its scheduled `endFrame`, because the list order decided who took an orbit next (measured 2026-09-15 on Der
  Schmetterling: -32 dBFS). Ownership now goes by onset (the newest `Sounding` voice), and every removal keeps the
  list's order, so nothing needs the zombie. One order effect remains, the unison phase-pool take on a voice's
  first block (open: `docs/tasks/engine-follow-ups.md`, item 14).
- **Never in the gate, and never before the voice has sounded.** The held part of a note may be
  silent on purpose (a slow attack, a silent lead-in). Only the release, which has been told to
  stop, is culled, and only once at least one block has been audible (`Voice.heard`): a sample
  with leading silence pitched down, or an ignitor attack outliving a short gate, is silent at
  gate end and sounds later; the gate says "told to stop", the latch says "has started". A release that goes
  silent and comes back is cut at its first gap: the factory excludes voices whose tree holds a
  tremolo on the output (`BuiltIgnitor.gatesOutput`; a square shape at full depth is exact silence
  for half a cycle) unless `cull` is set explicitly; a sparse source inside an ignitor is the author's call (`noCull()`).
- **Tails on the orbit are untouched:** reverb and delay live on the cylinder buses; culling
  only stops future ~zero sends. A culled voice is past its gate and owns nothing, so culling
  does not move the orbit's ownership either.
- **Knobs:** `cull(seconds)` sets the window per voice (`VoiceData.cull`, default
  `VOICE_CULL_SECONDS` = 50 ms), `noCull()` writes `VOICE_CULL_NEVER` (negative = never). Floor
  `VOICE_CULL_FLOOR` = `SILENCE_FLOOR` = 1e-5 (-100 dBFS; one constant shared with the
  cylinder's silence test, so per voice and per bus a culled voice is already below what keeps an
  orbit alive; the known exceptions, summed sub-floor tails and a feedback delay's tail ceiling,
  are on the constant's KDoc). Constants in `audio_bridge/constants/VoiceCullingDefaults.kt`.
- Guard: `VoiceCullingSpec`. Record: `docs/tasks-archive/2026-09/20260915-voice-culling.md`.

## The envelope law and its knobs

- **One law, `EnvelopeCore`** (`audio_be/.../EnvelopeCore.kt`; its KDoc is the rule's home): every ADSR-shaped
  envelope is a thin host of it (the chain `adsr`, the filter cutoff envelope, the FM index envelope, the Ignitor
  pitch envelope, which sprudel's `penv` fills through `classic()`, the voice FM envelope). Attack and decay count fractional frames, the release
  `floor(N)` frames ending on an exact 0.0, a non-positive or NaN time is a zero-length stage, the sustain is raw.
  A gate at or before the onset releases from 0 (the stateless law evaluated there would extrapolate the attack).
  Guard: `EnvelopeLawSpec`.
- **Per destination**: amplitude floors at 0, filter and FM depth clamp to [0, 1], pitch is raw.
- **Curves are INDEX knobs** through `AdsrCurves` (audio_bridge), read once at build and leaf-only
  (`adsrCurveKnob`). There is no single fallback: an unknown name or index takes the READER's default, the chain's
  `AdsrCurve.Default` or the modulation envelopes' `MOD_ENV_CURVE` (both Exponential). Every exponential stage bends
  at `ADSR_EXP_K` (3.0); `expK` is gone from node, slot and wire. Guard: `AdsrCurvesSpec`.
- **One `adsr` call is the whole envelope** inside a builder: a later call replaces an earlier one, curves included.
- **`Adsr.on`** is OFF at exactly 0.0 (either sign); unset, any other number and a non-leaf are ON. OFF builds
  nothing but still reports a LEAF release tail (the voice keeps its lifetime).
- **Defaults**: `classic()`'s envelope uses the `VOICE_ADSR_*` constants (attack 0.01, decay 0.1, sustain 1.0,
  release 0.05; `audio_bridge/constants/EnvelopeDefaults.kt`). The bare Ignitor `adsr` node's sustain default is
  `ADSR_SUSTAIN_LEVEL` (0.7), and `AdsrIgnitor` substitutes it for a non-finite sustain (`finiteOr`, so the
  infinities read as unset too; a finite sustain passes raw, no clamp). A sample's own envelope fills only the `adsr.*` slots a pattern
  left unset (`withSampleEnvelopeDefaults`).
- **De-click**: `EnvelopeDeclick` is the one smoother. `classic()`'s envelope de-clicks with the constant
  `ENV_DECLICK_SECONDS` (1 ms, not a slot); the chain `adsr` builder's `declick` writes the node's `declick`
  (default 0, off). Modulation envelopes are not de-clicked.
- **An instrument whose own tail outlasts the envelope** writes `adsr(release = <tail>)`; the engine does not
  stretch a release to an instrument's tail (maintainer, 2026-09-26).
- The filter envelope's slot-layer fill (a written stage knob switches an unset depth on at
  `FILTER_ENV_DEPTH_SEMITONES`): the rule's text is `/dsl-design` section 4, the code `slotLayerDepth` in
  `IgnitorDslRuntime.kt`.

## The voice rng and its draw order

- **`IgnitorBuildCache` never spans two voices.** The stage gate decides per voice (an unwritten slot builds no
  stage), and that is only sound because a build cache lives for one voice build. `IgnitorGateSpec`'s
  different-graphs row is the tripwire if a cache is ever shared across voices.

Each voice deals ONE rng stream at the top of `VoiceFactory.makeVoice` (`voiceRandom`) and feeds everything it
owns from it: the tree build (`IgnitorBuildCache.random`), the sample playhead and the render context. The ORDER
of the draws is part of the sound: a moved draw shifts every later consumer's values, so a refactor that only
reorders the build changes songs with `analog > 0`.

- **The sample playhead is built before the tree**, so its drift lane draws first; the tree then draws per node
  in build order.
- **A filter's humanization** (`humanize`, at `analog > 0`) has one home, `ignitor/FilterHumanization.kt`: one
  `nextDouble()` for the cutoff tolerance, then the drift lane's three draws (two doubles, one int). At `analog`
  at or below 0, or non-finite, nothing is drawn. A `passes` cascade draws once and shares the result. A second
  copy of a draw is a second reader of the stream, so `perVoiceCutoffOffsetMul` is one shared function.
- **Construction-time drawers**: `perlin`, `berlin`, `crackle` and the sample's `AnalogDrift` lane draw in a
  property initialiser; every other source captures the stream and draws when it renders. So build order reaches
  only these, and a gated-off stage (not built, `audio/ref/off-values.md`) also stops its sibling knob subtrees
  from drawing: chosen deliberately, pinned by `IgnitorGateSpec`.
- **Build-time knobs are read leaf-only** (`buildTimeKnobValue` and its siblings in `IgnitorDslRuntime.kt`: shapes,
  oversample factor, passes, curves, tremolo shape and phase): a `Param` or `Constant` leaf is read, a non-leaf
  takes the knob's default and is NOT built, so asking never moves a draw.
- **`DriftLanes`** (`ignitor/DriftLanes.kt`): one int for the shared lane's seed at construction, then an own lane
  whenever `ensureLanes` first reaches its index; the shared lane takes nothing further, so lowering
  `analogSpread` later cannot shift another consumer.
- The analog drift itself steps once per block and ramps across it (`analogDriftStepRate`, `AnalogDrift.beginBlock`),
  so the block size is part of its sound.
