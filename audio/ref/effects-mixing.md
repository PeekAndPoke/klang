# Audio — Effects & Mixing

## KlangAudioRenderer

`audio_be/src/commonMain/kotlin/KlangAudioRenderer.kt`

A single-engine render loop: the engine alone, for the benchmarks and the engine specs. What is heard, live and in an
offline render (KLANG-00399 step 4), renders through `PlaybackEngineDispatcher`, which runs the same chain.

```
renderBlock(cursorFrame: Double, out: StereoBuffer)
  1. Clear mix buffers + cylinders
  2. VoiceScheduler.processBlock()        → voices write to voiceBuffer + cylinders
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

### offer(cylinderId, voice, blockStart) and checkIn(cylinderId, blockStart)

The voice's send routes into the `Cylinder` for its cylinder number, rented on first use. A voice that claims the cylinder
(`Voice.claimsCylinder`) offers itself as the owner; any other rendering voice only checks in, which keeps the cylinder
in use. `processAndMix` commits the block's newest offer once (`Cylinder.commitOwner`) before the cylinder processes.

## Cylinder

`audio_be/src/commonMain/kotlin/cylinders/Cylinder.kt`

One effect bus. Holds its own stereo accumulation buffer and effect instances.

**PER-CYLINDER (bus) vs PER-VOICE, which effects run where.** The Katalyst pipeline runs **once per cylinder**
on the summed mix: `[body, vowel, delay, reverb, phaser, compressor, gain]` (+ ducking, separate pass).
The `gain` stage is the cylinder's group fader, at unity on the classic chain and bit-transparent there;
a pattern moves it with `katp("gain.gain", x)`.

**Every knob of every stage comes from ONE place since Katalyst step 5b-1 (2026-09-19): the cylinder's
param state, which is the `katalystParams` map of the voice that owns the cylinder.** The bus
doors write those slots (a door and its `katp` slot are the same knob), the chain re-resolves only
when the map instance changes, and the voice's bus fields are not a knob source any more (the
delay, reverb, compressor and duck fields left the wire in step 5b-3). Ownership:
the cylinder's bus settings are owned by the newest `Sounding` voice; a voice gives the cylinder up when its gate closes or it is cut (lifecycle step 5, `Cylinder.offer`), so all voices on a cylinder SHARE these; put voices on different
cylinders for independent bus effects. Everything else is **per-voice**: `lpf`/`hpf`/`bpf`/`notch` +
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
| `KatalystDuckEffect`       | `Ducking`       | `duck.cylinder` names a source and `duck.depth` above 0             |
| `KatalystDistortEffect`    | `DistortionCore` (one per channel, the house DC pole) | `distort.amount` finite and above 0 (not in the classic chain; declared with `k.distort(...)`) |

How each stage switches and glides, when a cylinder may deactivate, the chain swap and the output host (`MasterBus`):
`audio/ref/katalyst.md`.

`body`/`vowel` moved from the per-voice filter chain to the cylinder bus (2026-07-03), an 8-band SVF bank
per voice became one per cylinder; see KLANG-00128 (body vowel to orbit katalyst).

## Effect Classes

All in `audio_be/src/commonMain/kotlin/effects/`.

### Compressor

Peak-detecting compressor (`max(|L|, |R|)`). Applied per cylinder (the cylinder's `compressor` stage). Without a
lookahead the gain curve reads each sample's level in dB, and the attack and release smooth its output, the gain
reduction in dB: the smooth decoupled peak detector of Giannoulis, Massberg and Reiss (JAES 2012), since KLANG-00428.
An onset is reduced from its first sample, and the release runs on the reduction in force whatever follows under the
knee (before, the level was smoothed: a dead time of 211 frames after silence at the defaults). With a lookahead the
other topology runs, unchanged (`Compressor` KDoc).

| Parameter   | Meaning                                 |
|-------------|-----------------------------------------|
| `threshold` | Level above which gain reduction starts |
| `ratio`     | Compression ratio (e.g. 4 = 4:1)        |
| `knee`      | Soft knee width (dB)                    |
| `attack`    | Gain reduction onset time constant (s)  |
| `release`   | Gain recovery time constant (s)         |

### Ducking

Sidechain-triggered gain reduction across cylinders.

- Configured by the cylinder owner's `duck.cylinder` slot (the source cylinder N)
- Reduces this cylinder's gain whenever cylinder N plays, by `duck.depth`, recovering over `duck.release`
- Recovery is automatic after the triggering voice ends
- Every duck reads its source cylinder's mix as that cylinder's chain left it, BEFORE any duck writes
  (`Cylinders.processAndMix`, KLANG-00428): the order the cylinders were first used does not matter, and two
  cylinders that duck each other duck alike. A source that is itself ducked is copied once per block into its
  `Cylinder.preDuckMix`; a source nobody ducks is read live (its mix is the same before and after the pass)

| Slot          | Meaning                                    |
|---------------|--------------------------------------------|
| `duck.depth`  | Depth of ducking (0 = none, 1 = full mute) |
| `duck.release` | Recovery time (s) after the trigger stops  |

### DelayLine

Fixed-size circular buffer delay with feedback and multi-tap mixing.

| Slot             | Meaning                                           |
|------------------|---------------------------------------------------|
| `delay.time`     | Delay time in seconds                             |
| `delay.feedback` | Feedback coefficient (0-1)                        |
| `delay.cap`      | Ceiling the feedback saturates toward             |
| `delay.wet`      | How much of the cylinder mix feeds the line (insert) |

### Reverb

Freeverb-style algorithmic reverb (no impulse-response path). Both sides' combs are fed `(L + R) / 2`, one room
for both ears (`Reverb.CROSS_FEED`, 2026-09-30). How long each `size` rings, measured and from the design:
the table in `.claude/skills/klang-music-writing/ref/sprudel-reference.md` ("Reverb size: how long the room rings").

| Slot             | Meaning                                        |
|-------------|------------------------------------------------|
| `reverb.wet`     | How much of the cylinder mix feeds the room (insert, the owner's one amount) |
| `reverb.size`    | Tail length, authored ~0..10, normalized by `Reverb.normalizeSize` |
| `reverb.lowpass` | The -3 dB cutoff in Hz of the one-pole in every comb's feedback (`Reverb.cutoffDamping`, the same corner at every rate), held to 5 Hz .. Nyquist; a dark cutoff lengthens the tail accounting's lap by the one-pole's lag (`Reverb.dampingLag`). Unset: Freeverb's fixed pole 0.2 (16.9 kHz at 48 kHz), bit for bit as before (KLANG-00428) |

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
| `ResonatorBank`                           | parallel SVF bandpass bank (body, vowel) and its dry/wet blend, mono; its bands reconfigured only while unheard (tidy-up step 12 (a)), its blend glided in place (knob expressions step 5c) |
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
