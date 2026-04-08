# Audio — Voice Synthesis

All types in `audio_be/src/commonMain/kotlin/voices/`.

## Voice sealed interface

`Voice.kt` — defines the full contract for all voice types.

### Processing Order (from `Voice.render()` docs)

```
1.  generateSignal()       — oscillator / sample playback → voiceBuffer
2.  Accelerate             — pitch ramp modulation
3.  Vibrato                — LFO pitch modulation (post-signal)
4.  PitchEnvelope          — one-shot pitch curve
5.  FM                     — frequency modulation (applied to pitch)
6.  Envelope (ADSR)        — amplitude envelope (before waveshaping = dynamic distortion)
7.  Crush / Coarse / Distort — waveshaping effects (responds to envelope dynamics)
8.  Filters (LP/HP/BP/Notch) — subtractive filtering (final say on spectrum)
9.  Compressor             — per-voice dynamic range
10. Gain + Pan             — volume + stereo positioning
11. PostGain               — final gain stage
12. Tremolo / Phaser       — per-voice modulation effects
13. Ducking                — sidechain signal written to target cylinder
14. Mix into Cylinder      — voiceBuffer → orbits[orbitId]
```

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

`osci/Oscillators.kt` — waveform factory (builder pattern).

| Waveform   | OscFn key    | Notes                                    |
|------------|--------------|------------------------------------------|
| `sine`     | `"sine"`     | Pure sinusoid                            |
| `saw`      | `"saw"`      | Rising sawtooth                          |
| `square`   | `"square"`   | ±1 square wave with optional pulse-width |
| `triangle` | `"triangle"` | Symmetric triangle wave                  |
| `zawtooth` | `"zawtooth"` | Falling sawtooth                         |
| `supersaw` | `"supersaw"` | Detuned multi-voice saw (uses `density`) |
| `noise`    | `"noise"`    | White noise                              |

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
