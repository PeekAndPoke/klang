# Audio — Effects & Mixing

## KlangAudioRenderer

`audio_be/src/commonMain/kotlin/KlangAudioRenderer.kt`

The top-level render loop driver. Called once per audio block by the platform backend.

```
renderBlock(cursorFrame: Double, out: StereoBuffer)
  1. Clear mix buffers + orbits
  2. VoiceScheduler.processBlock()        → voices write to voiceBuffer + orbits
  3. Cylinders.processAndMix()               → cylinder effects + mix to StereoBuffer
  4. Master limiter (lookahead 5 ms, anticipating) (−1 dB threshold)     → gain reduction applied to StereoBuffer
  5. Clip ±1.0 (no quantisation)        → StereoBuffer out (floating point; 16-bit only at the JVM/WAV edge)
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

### offer(orbitId, voice, blockStart) and checkIn(orbitId, blockStart)

The voice's send routes into its orbit's `Cylinder`, rented on first use. A voice that claims the orbit
(`Voice.claimsOrbit`) offers itself as the owner; any other rendering voice only checks in, which keeps the orbit
in use. `processAndMix` commits the block's newest offer once (`Cylinder.commitOwner`) before the orbit processes.

## Cylinder

`audio_be/src/commonMain/kotlin/cylinders/Cylinder.kt`

One effect bus. Holds its own stereo accumulation buffer and effect instances.

**PER-ORBIT (bus) vs PER-VOICE — which effects run where.** The Katalyst pipeline runs **once per orbit**
on the summed mix: `[body, vowel, delay, reverb, phaser, compressor, gain]` (+ ducking, separate pass).
The `gain` stage is the orbit's group fader, at unity on the classic chain and bit-transparent there;
a pattern moves it with `katp("gain.gain", x)`.

**Every knob of every stage comes from ONE place since Katalyst step 5b-1 (2026-09-19): the orbit's
param state, which is the `katalystParams` map of the voice that owns the orbit.** The bus
doors write those slots (a door and its `katp` slot are the same knob), the chain re-resolves only
when the map instance changes, and the voice's bus fields are not a knob source any more (the
delay, reverb, compressor and duck fields left the wire in step 5b-3). Ownership:
the orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate closes or it is cut (lifecycle step 5, `Cylinder.offer`), so all voices on an orbit SHARE these; put voices on different
orbits for independent bus effects. Everything else is **per-voice**: `lpf`/`hpf`/`bpf`/`notch` +
envelopes, `distort`, `crush`, `coarse`, `adsr`, `tremolo` as `classic()`'s slots in the instrument's
Ignitor tree; `unison`/`spread`, `analog` as the oscillator's slots; the pitch envelope as `classic()`'s `penv.*`
slots (pitch pipeline step 1), the vibrato as its `vibrato.*` slots (step 2), `accelerate` as its flat
`accelerate` slot (step 3) and `fm` as its `fm.*` slots (step 4; the voice has no pitch stage of its own since step 5); `gain`/`pan` in its send stage.

| Katalyst effect            | Class           | Applied when                                                     |
|----------------------------|-----------------|------------------------------------------------------------------|
| `KatalystResonatorEffect` (`BODY`)  | `ResonatorBank` (`bodyGain`)  | `body.material` names a material                        |
| `KatalystResonatorEffect` (`VOWEL`) | `ResonatorBank` (`vowelGain`) | `vowel.vowel` names a vowel                             |
| `KatalystDelayEffect`      | `DelayLine`     | `delay.wet` above 0 and `delay.time` >= 0.01 s (default 0.25)    |
| `KatalystReverbEffect`     | `Reverb`        | `reverb.wet` above 0 and `reverb.size` >= 0.1 authored (default 5) |
| `KatalystPhaserEffect`     | `Phaser`        | `phaser.wet` at or above the engage depth                        |
| `KatalystCompressorEffect` | `Compressor`    | any of the five `compressor.*` slots set                         |
| `KatalystDuckEffect`       | `Ducking`       | `duck.orbit` names a source and `duck.depth` above 0             |
| `KatalystDistortEffect`    | `DistortionCore` (one per channel, the house DC pole) | `distort.amount` finite and above 0 (not in the classic chain; declared with `k.distort(...)`) |

How each stage switches and glides, when an orbit may deactivate, the chain swap and the output host (`MasterBus`):
`audio/ref/katalyst.md`.

`body`/`vowel` moved from the per-voice filter chain to the orbit bus (2026-07-03) — an 8-band SVF bank
per voice became one per orbit; see `docs/tasks/body-vowel-to-orbit-katalyst.md`.

## Effect Classes

All in `audio_be/src/commonMain/kotlin/effects/`.

### Compressor

Peak-detecting compressor (`max(|L|, |R|)`). Applied per cylinder (the orbit's `compressor` stage).

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

Freeverb-style algorithmic reverb (no impulse-response path). Both sides' combs are fed `(L + R) / 2`, one room
for both ears (`Reverb.CROSS_FEED`, 2026-09-30). How long each `size` rings, measured and from the design:
the table in `.claude/skills/klang-music-writing/ref/sprudel-reference.md` ("Reverb size: how long the room rings").

| Slot             | Meaning                                        |
|-------------|------------------------------------------------|
| `reverb.wet`     | How much of the orbit mix feeds the room (insert, the owner's one amount) |
| `reverb.size`    | Tail length, authored ~0..10, normalized by `Reverb.normalizeSize` |
| `reverb.lowpass` | Tail damping cutoff in Hz (unset: fixed default damping)        |

### Phaser

All-pass cascade with LFO modulation.

| Slot             | Meaning                                |
|------------------|----------------------------------------|
| `phaser.wet`     | Wet amount (the door's first knob)     |
| `phaser.rate`    | LFO rate in Hz                         |
| `phaser.center`  | Center frequency of the notch          |
| `phaser.sweep`   | LFO sweep range                        |
| `phaser.floor`   | Dry floor                              |

The phaser runs on the cylinder only (`KatalystPhaserEffect`); the per-voice phaser of a custom pipeline
retired with the Pipeline DSL in phase 3 step 9. An Ignitor tree can hold its own `.phaser(...)` node.

## Filters

`audio_be/src/commonMain/kotlin/filters/`

The class-form resonator implements `AudioFilter` (`process(buffer, offset, length)`); `DcBlocker` and
`EqCore` have their own shapes.

| Class                                     | Type                                              |
|-------------------------------------------|---------------------------------------------------|
| `DcBlocker`                               | raw-pole DC blocker (the one-pole lowpass and highpass are Ignitor nodes, `ignitor/IgnitorFilters.kt`) |
| `ResonatorBank`                           | parallel SVF bandpass bank (body, vowel) and its dry/wet blend, mono; reconfigured only while unheard (tidy-up step 12 (a)) |
| `EqCore`                                  | the equalizer's fused sections                    |

The per-voice filters are not class-form any more: `lpf`/`hpf`/`bpf`/`notch` are `classic()` stages, each the
`Ignitor.svf` node (`ignitor/IgnitorFilters.kt`) with its cutoff envelope, filled from the `<door>.<param>` slots.
The strip's `SvfLPF`/`SvfHPF`/`SvfNotch`, `FilterDef.LowPass` ... `Notch` and `FilterModulator` retired in phase 3
step 9.

## StereoBuffer

`audio_be/src/commonMain/kotlin/StereoBuffer.kt`

Holds two `AudioBuffer`s (`DoubleArray`; left, right) of `blockFrames` length.
Used at cylinder level and at master mix level.

```kotlin
class StereoBuffer(blockFrames: Int) {
    val left: AudioBuffer
    val right: AudioBuffer
    fun clear()
    fun fill(value: AudioSample)
    inline fun addFrom(source: StereoBuffer, frames: Int) // left into left, right into right, no gain
}
```
