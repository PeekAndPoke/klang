# Klang Audio — Dispatcher

Real-time multiplatform DSP engine for live-coding music.
Kotlin Multiplatform (JVM + JS). Block-based audio processing. Thread-safe frontend/backend split.

## Module Map

```
audio_bridge  ←── shared data contracts + message-passing infrastructure
    ↑               depended on by all other audio modules
audio_be      ←── DSP backend: synthesis, effects, voice lifecycle, mixing
audio_fe      ←── frontend: sample loading, decoding, caching
audio_jsworklet ←─ JS AudioWorklet thread entry point
```

**Dependency order**: `common/tones` → `audio_bridge` → `audio_be` / `audio_fe` → `audio_jsworklet`

## Key Files by Module

### audio_bridge — data contracts & IPC

| File                                                   | Role                                           |
|--------------------------------------------------------|------------------------------------------------|
| `src/commonMain/kotlin/VoiceData.kt`                   | Voice event: pitch, gain, routing; door slots in `ignitorParams`, orbit slots in `katalystParams` |
| `src/commonMain/kotlin/IgnitorDslClassic.kt`           | `classic()`, the voice chain, and its slot groups        |
| `src/commonMain/kotlin/AdsrDef.kt`                     | `AdsrCurve`; `AdsrDef` for a sample's own envelope |
| `src/commonMain/kotlin/FilterDef.kt`                   | The orbit resonators' band carriers (`Formant`, `Body`) |
| `src/commonMain/kotlin/ScheduledVoice.kt`              | Voice scheduling message (VoiceData + timing)  |
| `src/commonMain/kotlin/MonoSamplePcm.kt`               | Decoded sample data (FloatArray + metadata)    |
| `src/commonMain/kotlin/infra/KlangCommLink.kt`         | Ring-buffer IPC; Cmd + Feedback sealed classes |
| `src/commonMain/kotlin/KlangTime.kt`                   | expect/actual monotonic clock                  |

### audio_be — DSP backend

| File                                                | Role                                                                                                                |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `src/commonMain/kotlin/PlaybackEngineDispatcher.kt` | Routes each `Cmd` by `playbackId` → its own `PlaybackEngine` (own scheduler + cylinders); see `ref/architecture.md` |
| `src/commonMain/kotlin/KlangAudioRenderer.kt`       | Main render loop driver + master limiter                                                                            |
| `src/commonMain/kotlin/voices/Voice.kt`             | `Voice`: runs Pitch → Ignite → (teardown fade) → Send per note; the Ignitor tree is the whole instrument            |
| `src/commonMain/kotlin/voices/VoiceFactory.kt`      | Builds a `Voice` (the instrument's Ignitor tree, the sample instrument for samples) from `VoiceData`                  |
| `src/commonMain/kotlin/voices/strip/`               | Per-block voice stages: `pitch/`, `ignite/`, `send/` (the old filter/VCA strip retired in phase 3 step 9)           |
| `src/commonMain/kotlin/voices/VoiceScheduler.kt`    | Voice lifecycle management                                                                                          |
| `src/commonMain/kotlin/cylinders/Cylinders.kt`      | Effect bus manager                                                                                                  |
| `src/commonMain/kotlin/cylinders/Cylinder.kt`       | Single effect bus (delay/reverb/phaser/…)                                                                           |
| `src/commonMain/kotlin/cylinders/katalyst/`         | Orbit-level effects: body/vowel resonators, `VoiceLease` ownership, `KatalystFilterSwap` declick                    |
| `src/commonMain/kotlin/filters/`                    | SVF kernels (`BaseSvf`, `SvfBPF`), OnePoles, resonator bank (`ResonatorBank`/`ParallelMixFilter`)     |
| `src/commonMain/kotlin/ignitor/Ignitors.kt`         | Oscillator + signal-gen factories (Ignitor DSL); samples via `ignitor/SampleIgnitor.kt`                             |
| `src/jvmMain/kotlin/JvmAudioBackend.kt`             | JVM: javax.sound.sampled output                                                                                     |
| `src/jsMain/kotlin/JsAudioBackend.kt`               | JS: Web Audio API AudioContext output                                                                               |

### audio_fe — sample frontend

| File                                                | Role                                 |
|-----------------------------------------------------|--------------------------------------|
| `src/commonMain/kotlin/samples/Samples.kt`          | Registry: Index + SoundProviders     |
| `src/commonMain/kotlin/decoders/AudioDecoder.kt`    | Abstract decoder interface           |
| `src/jvmMain/kotlin/decoders/JvmAudioDecoder.kt`    | WAV / MP3 decoder                    |
| `src/jsMain/kotlin/decoders/BrowserAudioDecoder.kt` | Web Audio decodeAudioData            |
| `src/commonMain/kotlin/cache/UrlCache.kt`           | Cache abstraction (In-memory / Disk) |

### audio_jsworklet

| File                                     | Role                                                 |
|------------------------------------------|------------------------------------------------------|
| `src/jsMain/kotlin/KlangAudioWorklet.kt` | AudioWorkletProcessor; bridges JS worklet → audio_be |

## Reference Files — Read Only What You Need

| Topic                                                                              | File                       |
|------------------------------------------------------------------------------------|----------------------------|
| Architecture, data flow, comm-link protocol, platform backends                     | `ref/architecture.md`      |
| VoiceData fields, the `ignitorParams` / `katalystParams` slots, AdsrDef, FilterDef, ScheduledVoice | `ref/data-model.md` |
| Voice stages, `classic()`, the sample instrument, oscillators                      | `ref/voice-synthesis.md`   |
| Envelope rules — voice-lifetime semantics, amp vs modulator envelopes              | `ref/voice-synthesis.md`   |
| The gate off values: when a stage is not built (the one home)                      | `ref/off-values.md`        |
| KlangAudioRenderer, Cylinders, effects (Delay/Reverb/Compressor/…)                 | `ref/effects-mixing.md`    |
| The Katalyst chain's laws: switching, knob glides, orbit deactivation, chain swap, the output host | `ref/katalyst.md` |
| Envelope law and knobs, the voice rng and its draw order                           | `ref/voice-synthesis.md`   |
| How an engine change is proven: identity renders, controls, metric floors, oracle specs | `ref/verification.md` |
| The full dated record of `MEMORY.md` up to 2026-09-29 (history, not read by default) | `ref/memory-history.md` |
| Samples registry, audio decoders, URL caching (audio_fe)                           | `ref/sample-management.md` |
| Numerical safety (NaN/Inf/subnormals), `SAFE_MIN`/`SAFE_MAX`, framework precedents | `ref/numerical-safety.md`  |
| Performance rules (no SAM Ignitors, no per-block alloc, JS hot-path patterns)      | `ref/performance.md`       |

## Build & Test

```bash
./gradlew :audio_bridge:jvmTest
./gradlew :audio_be:jvmTest
./gradlew :audio_fe:jvmTest
```
