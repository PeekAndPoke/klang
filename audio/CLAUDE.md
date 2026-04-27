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
| `src/commonMain/kotlin/VoiceData.kt`                   | All voice parameters (pitch, gain, filters…)   |
| `src/commonMain/kotlin/AdsrEnvelope.kt`                | Attack/decay/sustain/release                   |
| `src/commonMain/kotlin/FilterDefs.kt` + `FilterDef.kt` | Sealed filter type hierarchy                   |
| `src/commonMain/kotlin/ScheduledVoice.kt`              | Voice scheduling message (VoiceData + timing)  |
| `src/commonMain/kotlin/MonoSamplePcm.kt`               | Decoded sample data (FloatArray + metadata)    |
| `src/commonMain/kotlin/infra/KlangCommLink.kt`         | Ring-buffer IPC; Cmd + Feedback sealed classes |
| `src/commonMain/kotlin/KlangTime.kt`                   | expect/actual monotonic clock                  |

### audio_be — DSP backend

| File                                             | Role                                      |
|--------------------------------------------------|-------------------------------------------|
| `src/commonMain/kotlin/KlangAudioRenderer.kt`    | Main render loop driver + master limiter  |
| `src/commonMain/kotlin/voices/Voice.kt`          | Voice sealed interface + all sub-objects  |
| `src/commonMain/kotlin/voices/AbstractVoice.kt`  | Base DSP logic shared by all voice types  |
| `src/commonMain/kotlin/voices/SynthVoice.kt`     | Oscillator-based synthesis                |
| `src/commonMain/kotlin/voices/SampleVoice.kt`    | Sample playback                           |
| `src/commonMain/kotlin/voices/VoiceScheduler.kt` | Voice lifecycle management                |
| `src/commonMain/kotlin/cylinders/Cylinders.kt`   | Effect bus manager                        |
| `src/commonMain/kotlin/cylinders/Cylinder.kt`    | Single effect bus (delay/reverb/phaser/…) |
| `src/commonMain/kotlin/osci/Oscillators.kt`      | Waveform factory (sine/saw/square/noise…) |
| `src/jvmMain/kotlin/JvmAudioBackend.kt`          | JVM: javax.sound.sampled output           |
| `src/jsMain/kotlin/JsAudioBackend.kt`            | JS: Web Audio API AudioContext output     |

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
| VoiceData fields (all parameters), FilterDefs, ADSR, ScheduledVoice                | `ref/data-model.md`        |
| Voice interface, AbstractVoice, SynthVoice, SampleVoice, oscillators               | `ref/voice-synthesis.md`   |
| KlangAudioRenderer, Cylinders, effects (Delay/Reverb/Compressor/…)                 | `ref/effects-mixing.md`    |
| Samples registry, audio decoders, URL caching (audio_fe)                           | `ref/sample-management.md` |
| Numerical safety (NaN/Inf/subnormals), `SAFE_MIN`/`SAFE_MAX`, framework precedents | `ref/numerical-safety.md`  |

## Build & Test

```bash
./gradlew :audio_bridge:jvmTest
./gradlew :audio_be:jvmTest
./gradlew :audio_fe:jvmTest
```
