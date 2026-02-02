# Audio Backend (`audio_be`)

Audio processing engine for the Klang live coding system.

## Overview

The audio backend implements a professional-grade voice architecture with modular effect processing, following standard
audio signal flow practices. Each voice (synth or sample) processes audio through a well-defined pipeline from source
generation to final mixing.

## Architecture

### Voice System

The system uses a **Voice** interface that defines the complete audio processing contract. There are two main
implementations:

- **SynthVoice** - Generates audio from oscillators with FM synthesis support
- **SampleVoice** - Plays back pre-recorded audio samples with looping and time-stretching

### Audio Processing Pipeline

Every voice follows this exact processing order:

```
┌─────────────────────────────────────────────────────────────────┐
│                     AUDIO PROCESSING PIPELINE                   │
└─────────────────────────────────────────────────────────────────┘

    ┌──────────────────────────────────────┐
    │   PHASE 0: SOURCE GENERATION         │
    │                                      │
    │   • Oscillator (SynthVoice)          │
    │     - Waveform generation            │
    │     - FM modulation                  │
    │     - Pitch modulation (vibrato,     │
    │       accelerate, pitch envelope)    │
    │                                      │
    │   • Sample Playback (SampleVoice)    │
    │     - PCM reading with interpolation │
    │     - Looping                        │
    │     - Pitch shifting                 │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   PHASE 1: PRE-FILTERS               │
    │   (Destructive / Lo-Fi)              │
    │                                      │
    │   1. BitCrush                        │
    │      └─ Quantizes amplitude          │
    │                                      │
    │   2. Sample Rate Reducer (Coarse)    │
    │      └─ Quantizes time               │
    │                                      │
    │   Why first?                         │
    │   These add digital artifacts that   │
    │   the main filter can smooth out.    │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   PHASE 2: MAIN FILTER               │
    │   (Subtractive Synthesis)            │
    │                                      │
    │   1. Filter Modulation               │
    │      └─ Calculate envelope/LFO       │
    │         and update cutoff            │
    │                                      │
    │   2. Filter Processing               │
    │      • LowPass / HighPass            │
    │      • BandPass / Notch              │
    │      • Formant                       │
    │                                      │
    │   This is the "sculpting" stage      │
    │   that shapes the tone.              │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   PHASE 3: VCA / ENVELOPE            │
    │   (Dynamics)                         │
    │                                      │
    │   • ADSR Envelope                    │
    │     - Attack                         │
    │     - Decay                          │
    │     - Sustain                        │
    │     - Release                        │
    │                                      │
    │   This defines the note's lifecycle  │
    │   and volume shape.                  │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   PHASE 4: POST-FILTERS              │
    │   (Color & Modulation)               │
    │                                      │
    │   1. Distortion                      │
    │      └─ Reacts to volume dynamics    │
    │         (loud = more distorted)      │
    │                                      │
    │   2. Phaser                          │
    │      └─ Needs harmonics from         │
    │         distortion to sweep          │
    │                                      │
    │   3. Tremolo                         │
    │      └─ Clean rhythmic volume        │
    │         modulation                   │
    │                                      │
    │   Applied after envelope so they     │
    │   work on the "finished" sound.      │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   PHASE 5: MIXER                     │
    │   (Routing & Summing)                │
    │                                      │
    │   • Post-Gain                        │
    │   • Equal-Power Panning (L/R)        │
    │   • Sum to Orbit Mix Buffer          │
    │   • Delay Send                       │
    │   • Reverb Send                      │
    │                                      │
    │   Final stage before orbit effects.  │
    └──────────────────────────────────────┘
                    ↓
    ┌──────────────────────────────────────┐
    │   ORBIT-LEVEL EFFECTS                │
    │   (Applied to mixed output)          │
    │                                      │
    │   • Delay / Echo                     │
    │   • Reverb                           │
    │   • Compressor / Limiter             │
    │                                      │
    │   Processed after all voices mixed.  │
    └──────────────────────────────────────┘
```

## Key Design Principles

### 1. Modular Effect Pipeline

Effects are implemented as separate `AudioFilter` classes:

- `BitCrushFilter` - Bit depth reduction
- `SampleRateReducerFilter` - Downsampling
- `DistortionFilter` - Soft clipping/saturation
- `TremoloFilter` - Amplitude modulation
- `PhaserFilter` - All-pass filter sweep

### 2. Separation of Concerns

- **Voice** - Defines the interface and processing contract
- **SynthVoice / SampleVoice** - Implement source generation
- **AudioFilter** - Independent, reusable effect modules
- **VoiceScheduler** - Manages voice lifecycle and routing
- **Orbit** - Handles bus-level effects and mixing

### 3. Correct Signal Flow

The pipeline follows professional audio standards:

- **Source → Sculpt → Dynamics → Color**
- Destructive effects before filtering (allows cleanup)
- Distortion after envelope (reacts to dynamics)
- Phaser after distortion (needs harmonic content)
- Tremolo last (clean volume modulation)

### 4. Efficiency

- **Pre-computed filters** - Effect filters instantiated once
- **Lazy initialization** - Filters needing sampleRate created on first render
- **Shared buffers** - Voice buffer and freq mod buffer reused across voices
- **Early returns** - Effects with zero amount skip processing

## File Structure

```
audio_be/src/commonMain/kotlin/
├── voices/
│   ├── Voice.kt              # Interface + inner classes
│   ├── SynthVoice.kt         # Oscillator-based voices
│   ├── SampleVoice.kt        # Sample playback voices
│   ├── VoiceScheduler.kt     # Voice lifecycle management
│   └── common.kt             # Shared helpers (mixToOrbit, etc.)
│
├── filters/
│   ├── AudioFilter.kt        # Filter interface
│   ├── LowPassHighPassFilters.kt
│   ├── FormantFilter.kt
│   └── effects/
│       ├── BitCrushFilter.kt
│       ├── SampleRateReducerFilter.kt
│       ├── DistortionFilter.kt
│       ├── TremoloFilter.kt
│       └── PhaserFilter.kt
│
├── osci/
│   └── OscFn.kt              # Oscillator functions
│
├── orbits/
│   ├── Orbit.kt              # Per-orbit mixing and effects
│   └── Orbits.kt             # Orbit management
│
└── effects/
    ├── Delay.kt              # Time-based effects
    ├── Reverb.kt
    ├── Compressor.kt
    └── Phaser.kt             # Orbit-level phaser
```

## Adding New Effects

To add a new effect to the voice pipeline:

1. **Create the filter class** in `filters/effects/`:
   ```kotlin
   class MyEffectFilter(
       private val param: Double
   ) : AudioFilter {
       override fun process(buffer: DoubleArray, offset: Int, length: Int) {
           // Process audio in-place
       }
   }
   ```

2. **Add parameter to Voice.kt**:
   ```kotlin
   class MyEffect(
       val param: Double,
   )
   ```

3. **Add property to Voice interface**:
   ```kotlin
   val myEffect: MyEffect
   ```

4. **Initialize in SynthVoice/SampleVoice**:
   ```kotlin
   private val fxMyEffect = if (myEffect.param > 0) MyEffectFilter(myEffect.param) else null
   ```

5. **Add to pipeline** (preFilters or postFilters as appropriate):
   ```kotlin
   override val postFilters = listOfNotNull(
       fxDistortion,
       fxMyEffect,  // Add here
       // ...
   )
   ```

## Testing

Run the audio backend tests:

```bash
./gradlew :audio_be:jvmTest
```

## Performance Considerations

- **Block-based processing** - Audio processed in blocks (typically 128 frames)
- **Minimal allocations** - Filters reuse provided buffers
- **Control rate** - Filter modulation calculated once per block
- **Audio rate** - Sample generation and effects per-sample
- **Early bailout** - Inactive voices return immediately

## Future Enhancements

- [ ] Configurable effect chains (user-defined order)
- [ ] Parallel effect processing (multiple paths)
- [ ] Effect presets/snapshots
- [ ] Per-voice compression (currently orbit-level)
- [ ] Dynamic filter instantiation (runtime effect changes)
- [ ] SIMD optimizations for effect processing
