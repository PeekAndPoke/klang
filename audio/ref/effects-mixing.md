# Audio — Effects & Mixing

## KlangAudioRenderer

`audio_be/src/commonMain/kotlin/KlangAudioRenderer.kt`

The top-level render loop driver. Called once per audio block by the platform backend.

```
renderBlock(outputBuffer: ShortArray, blockFrames: Int)
  1. Clear mix buffers + orbits
  2. VoiceScheduler.processBlock()        → voices write to voiceBuffer + orbits
  3. Cylinders.processAndMix()               → cylinder effects + mix to StereoBuffer
  4. Master limiter (lookahead 5 ms, anticipating) (−1 dB threshold)     → gain reduction applied to StereoBuffer
  5. Clip ±1.0, interleave L/R, scale     → ShortArray (16-bit PCM)
```

### Master Limiter Settings

| Parameter | Value                                                                        |
|-----------|------------------------------------------------------------------------------|
| Threshold | −1 dB                                                                        |
| Ratio     | 20:1                                                                         |
| Attack    | 5 ms — the gain-SMOOTHING length, not a one-pole (= the lookahead window)    |
| Lookahead | 5 ms — anticipating: holds the ceiling on transients instead of chasing them |
| Release   | 100 ms                                                                       |

## Cylinders

`audio_be/src/commonMain/kotlin/cylinders/Cylinders.kt`

Manages up to 16 effect buses (cylinder IDs 0–15). Each voice is routed to one cylinder.

### processAndMix() order

```
1. For each active cylinder:
     cylinder.processEffects()           → delay, reverb, phaser applied in-place
2. For each active cylinder:
     ducking.applySidechain(...)      → cross-cylinder sidechain compression
3. For each active cylinder:
     mix cylinder stereo buffer → master StereoBuffer (with cylinder gain)
4. Round-robin: deactivate one stale cylinder per block
```

### getOrInit(orbitId, voice)

Returns the existing `Cylinder` or creates a new one, copying effect parameters from the voice's `VoiceData`.

## Cylinder

`audio_be/src/commonMain/kotlin/cylinders/Cylinder.kt`

One effect bus. Holds its own stereo accumulation buffer and effect instances.

**PER-ORBIT (bus) vs PER-VOICE — which effects run where.** The Katalyst pipeline runs **once per orbit**
on the summed mix: `[body, vowel, delay, reverb, phaser, compressor]` (+ ducking, separate pass). Config is
copied from voices in `updateFromVoice` **last-writer-wins**, so all voices on an orbit SHARE these; put
voices on different orbits for independent bus effects. Everything else (`lpf`/`hpf`/`bandf`/`notchf` +
envelopes, `distort`, `crush`, `coarse`, `adsr`, `vibrato`, `tremolo`, `fm`, pitch env, `gain`/`pan`/
`postgain`, `unison`/`spread`, `analog`) is **per-voice** in the voice strip.

| Katalyst effect            | Class           | Applied when                           |
|----------------------------|-----------------|----------------------------------------|
| `KatalystBodyEffect`       | `BodyFilter`    | any voice on the orbit sets `body(…)`  |
| `KatalystFormantEffect`    | `FormantFilter` | any voice on the orbit sets `vowel(…)` |
| `KatalystDelayEffect`      | `DelayLine`     | `VoiceData.delay > 0`                  |
| `KatalystReverbEffect`     | `Reverb`        | `VoiceData.room > 0`                   |
| `KatalystPhaserEffect`     | `Phaser`        | cylinder-level phaser LFO              |
| `KatalystCompressorEffect` | `Compressor`    | cylinder-level dynamic range           |
| `KatalystDuckingEffect`    | `Ducking`       | sidechain from duckCylinder voice      |

`body`/`vowel` moved from the per-voice filter chain to the orbit bus (2026-07-03) — an 8-band SVF bank
per voice became one per orbit; see `docs/tasks/body-vowel-to-orbit-katalyst.md`.

## Effect Classes

All in `audio_be/src/commonMain/kotlin/effects/`.

### Compressor

RMS-based compressor. Applied per-cylinder or per-voice.

| Parameter   | Meaning                                 |
|-------------|-----------------------------------------|
| `threshold` | Level above which gain reduction starts |
| `ratio`     | Compression ratio (e.g. 4 = 4:1)        |
| `knee`      | Soft knee width (dB)                    |
| `attack`    | Gain reduction onset time (ms)          |
| `release`   | Gain recovery time (ms)                 |

### Ducking

Sidechain-triggered gain reduction across orbits.

- Triggered when a voice with `duckCylinder = N` is activated
- Reduces gain of cylinder N according to `duckAttack`/`duckDepth`
- Recovery is automatic after the triggering voice ends

| Parameter    | Meaning                                    |
|--------------|--------------------------------------------|
| `duckDepth`  | Depth of ducking (0 = full mute, 1 = none) |
| `duckAttack` | Attack time (s) before full ducking        |

### DelayLine

Fixed-size circular buffer delay with feedback and multi-tap mixing.

| Parameter       | Meaning                             |
|-----------------|-------------------------------------|
| `delayTime`     | Delay time in seconds               |
| `delayFeedback` | Feedback coefficient (0–1)          |
| `delay`         | Dry/wet mix (0 = dry, 1 = full wet) |

### Reverb

Freeverb-style algorithmic reverb with optional impulse response.

| Parameter   | Meaning                                        |
|-------------|------------------------------------------------|
| `room`      | Dry/wet mix                                    |
| `roomSize`  | Room size (controls comb filter lengths)       |
| `roomFade`  | Tail length / decay                            |
| `roomLp`    | Internal low-pass cutoff (brightness)          |
| `roomDim`   | Diffusion / dimension                          |
| `iResponse` | Impulse response selector (convolution reverb) |

### Phaser

All-pass cascade with LFO modulation.

| Parameter | Meaning                                |
|-----------|----------------------------------------|
| `depth`   | Modulation depth of the all-pass stage |
| `center`  | Center frequency of the notch          |
| `sweep`   | LFO sweep range                        |

Phaser can be applied per-voice (from `VoiceData.phaser`) or per-cylinder (cylinder-level).

## Filters

`audio_be/src/commonMain/kotlin/filters/`

All implement `AudioFilter` interface with `process(buffer: FloatArray)`.

| Class            | Type                      |
|------------------|---------------------------|
| `LowPassFilter`  | 2-pole Biquad             |
| `HighPassFilter` | 2-pole Biquad             |
| `BandPassFilter` | 2-pole Biquad             |
| `FormantFilter`  | Multi-band formant filter |

Constructed from `FilterDef` sealed types. Cutoff can be modulated by `FilterEnvelope` via `FilterModulator`.

## StereoBuffer

`audio_be/src/commonMain/kotlin/StereoBuffer.kt`

Holds two `FloatArray`s (left, right) of `blockFrames` length.
Used at cylinder level and at master mix level.

```kotlin
class StereoBuffer(val blockFrames: Int) {
    val left: FloatArray
    val right: FloatArray
    fun clear()
    fun addFrom(other: StereoBuffer, gain: Float = 1f)
    fun limit(threshold: Float, ratio: Float, attack: Float, release: Float)
}
```
