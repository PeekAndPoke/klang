# Audio — Data Model

All types live in `audio_bridge/src/commonMain/kotlin/` and are `@Serializable`.

## ScheduledVoice

The scheduling message sent via `Cmd.ScheduleVoice`. Enqueued in the voice min-heap keyed by `startTime`.

```kotlin
data class ScheduledVoice(
    val playbackId: String,       // unique ID for deduplication / replace
    val data: VoiceData,          // all synthesis parameters
    val startTime: Double,        // engine-relative ms — when to activate
    val gateEndTime: Double,      // engine-relative ms — when to release (ADSR gate off)
    val playbackStartTime: Double // engine-relative ms — origin of the playback cycle
)
```

## VoiceData — All Parameters

`VoiceData.kt` — nullable fields; omitting = use engine default.

### Pitch & Tuning

| Field        | Type         | Meaning                                   |
|--------------|--------------|-------------------------------------------|
| `note`       | `Double?`    | MIDI note number (e.g. 60 = C4)           |
| `freqHz`     | `Double?`    | Direct frequency in Hz (overrides `note`) |
| `scale`      | `List<Int>?` | Scale degrees for quantization            |
| `accelerate` | `Double?`    | Pitch ramp rate (semitones/cycle)         |

### Gain & Dynamics

| Field        | Type       | Meaning                                |
|--------------|------------|----------------------------------------|
| `gain`       | `Double?`  | Output gain multiplier (1.0 = unity)   |
| `velocity`   | `Double?`  | MIDI-style velocity 0–1                |
| `postGain`   | `Double?`  | Post-effects gain multiplier           |
| `legato`     | `Boolean?` | If true, don't re-trigger on same note |
| `solo`       | `Boolean?` | If true, mute all other voices         |
| `compressor` | `Double?`  | Per-voice compression amount           |

### Sample Selection

| Field        | Type      | Meaning                                  |
|--------------|-----------|------------------------------------------|
| `bank`       | `String?` | Sample bank name (e.g. `"mdk"`)          |
| `sound`      | `String?` | Sound name within the bank (e.g. `"bd"`) |
| `soundIndex` | `Int?`    | Variant index within the sound           |

### Oscillator (SynthVoice only)

| Field        | Type      | Meaning                                     |
|--------------|-----------|---------------------------------------------|
| `density`    | `Int?`    | Number of unison voices (supersaw / detune) |
| `panSpread`  | `Double?` | Stereo spread of unison voices              |
| `freqSpread` | `Double?` | Frequency detune amount for unison          |
| `voices`     | `Int?`    | Alias for `density`                         |
| `warmth`     | `Double?` | Subtle pitch drift per unison voice         |

### Filters

| Field       | Type          | Meaning                                          |
|-------------|---------------|--------------------------------------------------|
| `filters`   | `FilterDefs?` | Structured filter collection (see below)         |
| `cutoff`    | `Double?`     | Low-pass cutoff frequency (Hz or 0–1 normalised) |
| `hcutoff`   | `Double?`     | High-pass cutoff frequency                       |
| `bandf`     | `Double?`     | Band-pass center frequency                       |
| `resonance` | `Double?`     | Filter resonance / Q                             |

### ADSR Envelope

| Field  | Via            | Meaning                            |
|--------|----------------|------------------------------------|
| `adsr` | `AdsrEnvelope` | Attack / Decay / Sustain / Release |

### Pitch Modulation

| Field        | Type      | Meaning                          |
|--------------|-----------|----------------------------------|
| `vibrato`    | `Double?` | Vibrato depth (semitones)        |
| `vibratoMod` | `Double?` | Vibrato rate                     |
| `pAttack`    | `Double?` | Pitch envelope attack (s)        |
| `pDecay`     | `Double?` | Pitch envelope decay (s)         |
| `pRelease`   | `Double?` | Pitch envelope release (s)       |
| `pEnv`       | `Double?` | Pitch envelope depth (semitones) |
| `pCurve`     | `Double?` | Pitch envelope curve shape       |
| `pAnchor`    | `Double?` | Pitch envelope start value       |

### FM Synthesis

| Field       | Type      | Meaning                                      |
|-------------|-----------|----------------------------------------------|
| `fmh`       | `Double?` | FM modulator ratio (multiplier of base freq) |
| `fmAttack`  | `Double?` | FM envelope attack (s)                       |
| `fmDecay`   | `Double?` | FM envelope decay (s)                        |
| `fmSustain` | `Double?` | FM envelope sustain level                    |
| `fmEnv`     | `Double?` | FM modulation depth                          |

### Effects

| Field     | Type          | Meaning                                            |
|-----------|---------------|----------------------------------------------------|
| `distort` | `Double?`     | Distortion/overdrive amount                        |
| `coarse`  | `Double?`     | Bit-crush coarseness (downsampling ratio)          |
| `crush`   | `Double?`     | Bit depth reduction                                |
| `phaser`  | `PhaserDef?`  | Phaser: `depth`, `center`, `sweep`                 |
| `tremolo` | `TremeloDef?` | Tremolo: `sync`, `depth`, `skew`, `phase`, `shape` |

### Ducking (sidechain)

| Field          | Type      | Meaning                                    |
|----------------|-----------|--------------------------------------------|
| `duckCylinder` | `Int?`    | Cylinder ID to duck when this voice plays  |
| `duckAttack`   | `Double?` | Ducking attack time (s)                    |
| `duckDepth`    | `Double?` | Ducking depth (0 = full mute, 1 = no duck) |

### Routing

| Field      | Type      | Meaning                                      |
|------------|-----------|----------------------------------------------|
| `cylinder` | `Int?`    | Cylinder (effect bus) ID for this voice      |
| `pan`      | `Double?` | Stereo pan (−1 = full left, +1 = full right) |

### Time-Based Effects (on cylinder)

| Field           | Type      | Meaning                                 |
|-----------------|-----------|-----------------------------------------|
| `delay`         | `Double?` | Dry/wet mix for cylinder delay          |
| `delayTime`     | `Double?` | Delay time (s)                          |
| `delayFeedback` | `Double?` | Delay feedback (0–1)                    |
| `room`          | `Double?` | Reverb dry/wet mix                      |
| `roomSize`      | `Double?` | Reverb room size                        |
| `roomFade`      | `Double?` | Reverb fade/tail length                 |
| `roomLp`        | `Double?` | Reverb internal low-pass cutoff         |
| `roomDim`       | `Double?` | Reverb dimension / diffusion            |
| `iResponse`     | `Double?` | Impulse response selector (convolution) |

### Sample Manipulation

| Field       | Type       | Meaning                                         |
|-------------|------------|-------------------------------------------------|
| `begin`     | `Double?`  | Sample start offset (0–1)                       |
| `end`       | `Double?`  | Sample end offset (0–1)                         |
| `speed`     | `Double?`  | Playback speed multiplier                       |
| `loop`      | `Boolean?` | Enable looping                                  |
| `cut`       | `Int?`     | Cut group ID (stops other voices in same group) |
| `loopBegin` | `Double?`  | Loop start point (0–1)                          |
| `loopEnd`   | `Double?`  | Loop end point (0–1)                            |

## AdsrEnvelope

```kotlin
data class AdsrEnvelope(
    val attack:  Double?,  // s; default 0.01
    val decay:   Double?,  // s; default 0.1
    val sustain: Double?,  // 0–1 level; default 0.5
    val release: Double?,  // s; default 0.1
)
```

## FilterDefs + FilterDef

```kotlin
// Container — use addOrReplace() to mutate
data class FilterDefs(val filters: List<FilterDef>)

sealed class FilterDef {
    data class LowPass(val cutoffHz: Double, val q: Double, val envelope: FilterEnvelope?)
    data class HighPass(val cutoffHz: Double, val q: Double, val envelope: FilterEnvelope?)
    data class BandPass(val cutoffHz: Double, val q: Double, val envelope: FilterEnvelope?)
    data class Notch(val cutoffHz: Double, val q: Double, val envelope: FilterEnvelope?)
    data class Formant(val cutoffHz: Double, val q: Double, val envelope: FilterEnvelope?)
}

// FilterEnvelope — dynamic filter modulation
data class FilterEnvelope(
    val attack: Double, val decay: Double, val sustain: Double,
    val release: Double, val depth: Double
)
```

Key method: `FilterDefs.addOrReplace(filter: FilterDef)` — replaces existing filter of same type, or appends.

## MonoSamplePcm

```kotlin
data class MonoSamplePcm(
    val sampleRate: Int,           // e.g. 44100
    val pcm: FloatArray,           // interleaved mono samples
    val meta: SampleMetadata,
)

data class SampleMetadata(
    val anchor: Int?,              // playback anchor frame
    val loop: LoopRange?,          // LoopRange(start, end) for looping
    val adsr: AdsrEnvelope?,       // sample-embedded ADSR
)
```

## SampleRequest

```kotlin
data class SampleRequest(
    val bank: String,
    val sound: String,
    val index: Int,
    val note: Double?,
)
```
