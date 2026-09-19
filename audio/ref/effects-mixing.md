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
on the summed mix: `[body, vowel, delay, reverb, phaser, compressor, gain]` (+ ducking, separate pass).
The `gain` stage is the orbit's group fader, at unity on the classic chain and bit-transparent there;
a pattern moves it with `katp("gain.gain", x)`.

**Every knob of every stage comes from ONE place since Katalyst step 5b-1 (2026-09-19): the orbit's
param state, which is the `katalystParams` map of the voice holding the orbit's lease.** The bus
doors write those slots (a door and its `katp` slot are the same knob), the chain re-resolves only
when the map instance changes, and the voice's bus fields are not a knob source any more (the
delay, reverb, compressor and duck fields left the wire in step 5b-3). Ownership is the
`VoiceLease`'s **first-writer-wins**, so all voices on an orbit SHARE these; put voices on different
orbits for independent bus effects. Everything else (`lpf`/`hpf`/`bandf`/`notch` + envelopes,
`distort`, `crush`, `coarse`, `adsr`, `vibrato`, `tremolo`, `fm`, pitch env, `gain`/`pan`,
`unison`/`spread`, `analog`) is **per-voice** in the voice strip.

| Katalyst effect            | Class           | Applied when                                                     |
|----------------------------|-----------------|------------------------------------------------------------------|
| `KatalystBodyEffect`       | `ResonatorBank` (`bodyBand`) | `body.material` names a material                                 |
| `KatalystFormantEffect`    | `ResonatorBank` (`vowelBand`) | `vowel.vowel` names a vowel                                      |
| `KatalystDelayEffect`      | `DelayLine`     | `delay.wet` above 0 and `delay.time` >= 0.01 s (default 0.25)    |
| `KatalystReverbEffect`     | `Reverb`        | `reverb.wet` above 0 and `reverb.size` >= 0.1 authored (default 5) |
| `KatalystPhaserEffect`     | `Phaser`        | `phaser.wet` at or above the engage depth                        |
| `KatalystCompressorEffect` | `Compressor`    | any of the five `compressor.*` slots set                         |
| `KatalystDuckEffect`       | `Ducking`       | `duck.orbit` names a source and `duck.depth` above 0             |

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

- Configured by the orbit owner's `duck.orbit` slot (the source orbit N)
- Reduces this orbit's gain whenever orbit N plays, by `duck.depth`, recovering over `duck.attack`
- Recovery is automatic after the triggering voice ends

| Slot          | Meaning                                    |
|---------------|--------------------------------------------|
| `duck.depth`  | Depth of ducking (0 = none, 1 = full mute) |
| `duck.attack` | Recovery time (s) after the trigger stops  |

### DelayLine

Fixed-size circular buffer delay with feedback and multi-tap mixing.

| Slot             | Meaning                                           |
|------------------|---------------------------------------------------|
| `delay.time`     | Delay time in seconds                             |
| `delay.feedback` | Feedback coefficient (0-1)                        |
| `delay.cap`      | Ceiling the feedback saturates toward             |
| `delay.wet`      | How much of the orbit mix feeds the line (insert) |

### Reverb

Freeverb-style algorithmic reverb (no impulse-response path).

| Slot             | Meaning                                        |
|-------------|------------------------------------------------|
| `reverb.wet`     | How much of the orbit mix feeds the room (insert, the owner's one amount) |
| `reverb.size`    | Tail length, authored ~0..10, normalized by `Reverb.normalizeSize` |
| `reverb.lowpass` | Tail damping cutoff in Hz (unset: fixed default damping)        |

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
| `ResonatorBank`  | Parallel SVF bandpass bank (body, vowel) |

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
