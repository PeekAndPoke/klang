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
  1. Vibrato            : LFO pitch modulation
  2. Accelerate         : pitch ramp modulation
  3. PitchEnvelope      — one-shot pitch curve
  4. FM                 — frequency modulation

Ignite stage    (IgniteRenderer → writes audioBuffer)
  5. the instrument's Ignitor tree (oscillator or sample, and everything the tree holds)

Teardown fade   (TeardownFadeRenderer, unless the tree's root is a built amplitude envelope with a
                 static release; the one home of the rule is BuiltIgnitor.endsInEnvelope. adsrOff
                 switches classic()'s envelope off, and the node hands on its inner's answer: the
                 instrument's own static-release envelope ends the voice only when nothing built
                 sits over it, neither a stage of the instrument's own nor a classic() stage the
                 pattern wrote; otherwise the fade does)

Send stage      (SendRenderer → mixes to cylinder)
  6. pan + gain → cylinder mix
```

**The voice chain is `classic()`** (`audio_bridge/.../IgnitorDslClassic.kt`), a tail of slotted
Ignitor stages in the classic subtractive order:

```
onepole -> crush -> coarse -> distort -> highpass -> bandpass -> notch -> lowpass -> tremolo -> adsr
```

Every knob is a slot (`<door>.<param>`) that the pattern fills through `VoiceData.oscParams`, and a stage
whose slot is at its off value is not built. Every built-in sound is `source.pregain().classic()`, every
sample voice is the same shape over `IgnitorDsl.Sample` (`IgnitorRegistry.builtInVoice`), and an authored
instrument gets the voice doors by ending in `.classic()`. A tree without `classic()` plays as it is: no
doors, no default envelope.

Compressor, ducking, phaser, delay, reverb, body and vowel are **cylinder-level** (Katalyst) effects,
not per-voice: applied on the orbit bus after all voices mix into the cylinder.

### Voice construction

`VoiceFactory` builds each `Voice` from `VoiceData`: the instrument's tree (`IgnitorRegistry.createExciter`,
or the sample instrument for a sample), the pitch pipeline from the typed pitch fields, and the stages
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
Cut groups: a new voice in the same `cut` group ends the ones still sounding.

## VoiceScheduler

`VoiceScheduler.kt` — manages voice lifecycle.

- Stores pending voices in a `KlangMinHeap` (priority queue by `startTime`)
- On each block: activates due voices, removes finished voices
- Sample resolution: when a sample voice is due but the PCM isn't loaded yet,
  sends `Feedback.RequestSample` to frontend and delays activation
- Solo/mute: `Voice.gainMultiplier` set to 0 for muted voices

## Oscillators

`ignitor/Ignitors.kt` — oscillator + signal-generator factories (the `Ignitor` DSL). Sound names are
registered in `ignitor/IgnitorDefaults.kt` / `IgnitorRegistry.kt`. (There is no `osci/Oscillators.kt`.)

| Family           | Names                                                       | Notes                                                                                                                                                           |
|------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sine             | `sine`                                                      | Pure sinusoid (inherently band-limited)                                                                                                                         |
| Trapezoid shapes | `saw` `ramp` `square` `pulze` `triangle`                    | ONE `waveTrapezoid` / `WaveVoiceState` engine (`WaveIgnitor`); finite-slope edges, no PolyBLEP, softens with pitch. `pulze` duty is audio-rate (PWM)            |
| Raw shapes       | `zaw`/`zawtooth` `zamp`                                     | `flankSamples = 0` → instant / aliased edges                                                                                                                    |
| Super (unison)   | `supersaw` `superramp` `supersquare` `supertri` `supersine` | ONE `DetunedStackIgnitor` — detuned voice stack, center-dominant gains, per-voice drift, centroid-anchored tuning. `voices` / `freqSpread` / `analog` oscParams |
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

### Vibrato

```kotlin
class Vibrato(depth: Double, rate: Double)
// Writes into freqModBuffer as a slow sinusoidal pitch deviation
```

### PitchEnvelope

```kotlin
class PitchEnvelope(semitones: Double, envelope: Envelope)
// An ADSR in frames (Voice.Envelope, with its three curves) scaling a pitch offset of `semitones`
```

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
  they cover the window, `Voice.culled` is set and the scheduler counts it
  (`VoiceScheduler.culledVoicesTotal`, `renderingVoiceCount`).
- **A culled voice is a ZOMBIE, not a removal.** It runs no stage any more, but it keeps its slot
  in the scheduler's active list and renews its orbit lease every block until its scheduled
  `endFrame`, where it expires like any voice. Removing it early was measured to change the
  MIX: the orbit lease passes to whichever voice renders first after an owner dies, that order
  is the active list, and a swap-remove reorders it, so a culled hat on orbit 7 changed which of
  guitar 3 and the bass owned orbit 3 (-32 dBFS difference on Der Schmetterling). With the zombie
  the null-diff against no culling is at the floor: only the sub-floor tails are gone.
- **Never in the gate, and never before the voice has sounded.** The held part of a note may be
  silent on purpose (a slow attack, a silent lead-in). Only the release, which has been told to
  stop, is culled, and only once at least one block has been audible (`Voice.heard`): a sample
  with leading silence pitched down, or an ignitor attack outliving a short gate, is silent at
  gate end and sounds later; the gate says "told to stop", the latch says "has started". A release that goes
  silent and comes back is cut at its first gap: the factory excludes voices whose tree holds a
  tremolo on the output (`BuiltIgnitor.gatesOutput`; a square shape at full depth is exact silence
  for half a cycle) unless `cull` is set explicitly; a sparse source inside an ignitor is the author's call (`noCull()`).
- **Tails on the orbit are untouched:** reverb and delay live on the cylinder buses; culling
  only stops future ~zero sends. The orbit lease does not move either (the zombie renews it), so
  the handover sequence on an orbit with mixed bus configs is the same as without culling.
- **Knobs:** `cull(seconds)` sets the window per voice (`VoiceData.cull`, default
  `VOICE_CULL_SECONDS` = 50 ms), `noCull()` writes `VOICE_CULL_NEVER` (negative = never). Floor
  `VOICE_CULL_FLOOR` = `ORBIT_SILENCE_FLOOR` = 1e-5 (-100 dBFS; one constant shared with the
  cylinder's silence test, so per voice and per bus a culled voice is already below what keeps an
  orbit alive; the known exceptions, summed sub-floor tails and a feedback delay's tail ceiling,
  are on the constant's KDoc). Constants in `audio_bridge/constants/VoiceCullingDefaults.kt`.
- Guard: `VoiceCullingSpec`. Record: `docs/tasks-archive/2026-09/20260915-voice-culling.md`.
