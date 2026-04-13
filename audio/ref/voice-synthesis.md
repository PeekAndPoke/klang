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
