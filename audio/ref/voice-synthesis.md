# Audio — Voice Synthesis

All types in `audio_be/src/commonMain/kotlin/voices/`.

## Voice sealed interface

`Voice.kt` — defines the full contract for all voice types.

### Processing Order

Voice strip runs four stages in sequence: **Pitch → Ignite → Filter → Send**.
Each stage is a list of `BlockRenderer`s; `Voice.render()` just iterates the
composed pipeline.

```
Pitch stage     (PitchPipelineBuilder → writes freqModBuffer)
  1. Accelerate         — pitch ramp modulation
  2. Vibrato            — LFO pitch modulation
  3. PitchEnvelope      — one-shot pitch curve
  4. FM                 — frequency modulation

Ignite stage    (IgniteRenderer → writes audioBuffer)
  5. Ignitor            — oscillator / sample playback

Filter stage    (FilterPipelineBuilder → reads/writes audioBuffer)
  6. FilterMod          — control-rate cutoff modulation
  7. Crush              — waveshaper (bit-depth reduce)
  8. Coarse             — waveshaper (sample-rate reduce)
  9. Distort            — waveshaper (various shapes)
  10. AudioFilter       — subtractive LP/HP/BP/Notch
  11. Tremolo           — amplitude LFO
  12. StripPhaser       — 4-stage allpass + LFO
  13. Envelope (ADSR)   — VCA, last in the tonal stage

Send stage      (SendRenderer → mixes to cylinder)
  14. postGain → pan → gain → cylinder mix + delay/reverb sends
```

**Classic subtractive ordering**: `osc → waveshaper → VCF → VCA`. The ADSR
sits at the end of the tonal stage so the filter, phaser and waveshapers all
see steady-state amplitude and don't smear the attack.

Compressor and ducking are **cylinder-level** (katalyst) effects, not per-voice
— applied in `Cylinder.processBusEffects()` after all voices mix into the
cylinder (`Delay → Reverb → Phaser → Compressor`).

### Voice Properties (key fields)

```kotlin
sealed interface Voice {
    // Lifecycle
    val startFrame: Int              // absolute frame when voice becomes active
    val endFrame: Int?               // absolute frame when voice finishes (null = until envelope done)
    val gateEndFrame: Int            // absolute frame when gate closes (ADSR release starts)
  val orbitId: Int                 // target Cylinder index

    // Synthesis modulation
    val fm: Fm?                      // FM modulator (ratio + ADSR depth)
    val accelerate: Accelerate?      // pitch ramp
    val vibrato: Vibrato?            // LFO pitch modulation
    val pitchEnvelope: PitchEnvelope?// one-shot pitch curve

    // Dynamics
    val gain: Double
    val pan: Double
    val postGain: Double
    val envelope: Envelope           // ADSR
    val compressor: Compressor?

    // Filters
    val filter: AudioFilter?         // primary filter
    val filterModulators: List<FilterModulator>
    val preFilters: List<AudioFilter>
    val postFilters: List<AudioFilter>

    // Effects
    val delay: Delay?
    val reverb: Reverb?
    val phaser: Phaser?
    val tremolo: Tremolo?
    val distort: Distort?
    val crush: Crush?
    val coarse: Coarse?

    // Control
    var gainMultiplier: Float        // applied by VoiceScheduler for solo/mute

    // Render
    fun render(ctx: RenderContext): Boolean  // returns false when voice is done
}
```

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

## AbstractVoice

`AbstractVoice.kt` — implements `Voice`; handles steps 2–15 above.
Subclasses only implement `generateSignal(ctx: RenderContext)`.

## SynthVoice

`SynthVoice.kt` — oscillator-based voice.

- `generateSignal()` calls the selected `OscFn` (oscillator function) in a sample loop
- Phase accumulation: `phase += (freqHz / sampleRate) * TWO_PI` per sample
- Unison: multiple phase accumulators for `density` voices with detuning

## SampleVoice

`SampleVoice.kt` — sample playback voice.

- `generateSignal()` reads from `MonoSamplePcm.pcm` with rate modulation
- Rate: `(sample.sampleRate / engineSampleRate) * speed * pitchRatio`
- Looping: respects `loopBegin`/`loopEnd` range from `VoiceData`
- Cut groups: on activation, sends a cut signal to other `SampleVoice`s with same `cut` ID

## VoiceScheduler

`VoiceScheduler.kt` — manages voice lifecycle.

- Stores pending voices in a `KlangMinHeap` (priority queue by `startTime`)
- On each block: activates due voices, removes finished voices
- Sample resolution: when a `SampleVoice` is due but the PCM isn't loaded yet,
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
class PitchEnvelope(attack: Double, decay: Double, release: Double, env: Double, curve: Double, anchor: Double)
// One-shot pitch curve: anchor → peak → sustain → release
```

### FilterModulator

```kotlin
class FilterModulator(filter: AudioFilter, envelope: FilterEnvelope)
// Applies FilterEnvelope to filter cutoff dynamically
```

## Envelope / voice-lifetime semantics

> **The longest amplitude tail wins. Modulator envelopes are best-effort within.**

Two classes of envelope, with different roles in deciding when a voice ends:

- **Amplitude envelopes** determine voice lifetime. The longest one wins.
    - The voice amp ADSR (`data.adsr` → `Voice.Envelope`) — the VCA stage.
    - Any `IgnitorDsl.Adsr` node inside the ignitor tree — wraps an audio
      signal directly, so cutting it off mid-decay would click.
    - Voice end frame = `gateEndFrame + max(amp.release, ignitorAmpRelease)`.

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

**Implementation reference:** `voices/VoiceFactory.kt:205-212` (oscillator
path — extends amp ADSR to cover ignitor-internal `Adsr` nodes via
`IgnitorDsl.maxReleaseSec()` at `audio_bridge/.../IgnitorDsl.kt:1304`).
Per-block envelope math at `voices/strip/EnvelopeCalc.kt:14-34`.

### Silence culling (2026-09-15)

The scheduled lifetime above is an upper bound. A voice also ends itself EARLY once it is in
its release and its own output has stayed under the audibility floor for the cull window:

- **Measure:** `SendRenderer`, the last strip stage, keeps the block's peak `|output|`
  (post-VCA, times `postGain`, `gain` and the largest send amount, so it bounds the mix bus AND
  the send buses; BEFORE the solo/mute multiplier, so a voice a solo faded out is not taken for
  a dead one) in `BlockContext.voiceOutputPeak`. A separate pass, run only on the blocks that
  read it (`BlockContext.measurePeak`): until the voice has been heard, then in the release; a
  heard voice pays nothing for the rest of its gate, `noCull()` voices pay nothing at all.
- **Decide:** `Voice.render`, after the strip loop, only when `blockStart >= gateEndFrame`.
  Silent frames accumulate (frames, not blocks, so the window has the same length at any block
  size and the cut lands within one block of the same frame); an audible block resets them; when
  they cover the window, `Voice.culled` is set and the scheduler counts it
  (`VoiceScheduler.culledVoicesTotal`, `renderingVoiceCount`).
- **A culled voice is a ZOMBIE, not a removal.** It runs no strip any more, but it keeps its slot
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
  silent and comes back is cut at its first gap: the factory excludes voices with a `tremolo`
  (a square shape at full depth is exact silence for half a cycle) unless `cull` is set
  explicitly; a sparse source inside an ignitor is the author's call (`noCull()`).
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
