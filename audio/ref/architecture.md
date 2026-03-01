# Audio — Architecture

## Package Dependency Graph

```
common / tones
    ↓
audio_bridge          (data contracts + IPC — no DSP)
    ↓           ↓
audio_be        audio_fe
(DSP backend)   (sample frontend)
    ↓
audio_jsworklet  (JS worklet entry point — depends on audio_be)
```

## Data Flow

```
LIVE-CODING FRONTEND (main thread / JVM caller)
  │
  │  KlangCommLink.frontend.send(Cmd.ScheduleVoice(...))
  │  KlangCommLink.frontend.send(Cmd.ReplaceVoices(...))
  ▼
KlangRingBuffer (lock-free, single-producer / single-consumer)
  │
  ▼
AUDIO BACKEND (audio thread)
  │
  ├─ KlangCommLink.backend.poll()  →  Cmd sealed class
  │
  ├─ Cmd.ScheduleVoice   → VoiceScheduler.scheduleVoice()
  │                           stores ScheduledVoice in min-heap (keyed by startTime)
  │
  ├─ Cmd.ReplaceVoices   → VoiceScheduler replaces the entire pending queue
  │
  └─ Every block (128–256 frames):
       VoiceScheduler.processBlock()
         ├─ Activate due voices → SynthVoice or SampleVoice
         │    (requests MonoSamplePcm from audio_fe if needed)
         ├─ Call voice.render(ctx) for each active voice
         │    └─ Voice writes into ctx.voiceBuffer (FloatArray)
         └─ Mix voice output into its Orbit (ctx.orbits[orbitId])

       Orbits.processAndMix()
         ├─ Apply per-orbit effects: Delay → Reverb → Phaser
         ├─ Apply cross-orbit Ducking (sidechain)
         └─ Mix all orbits to master StereoBuffer

       KlangAudioRenderer
         ├─ Apply master limiter (−1 dB, 20:1, 1 ms attack)
         ├─ Clip ±1.0 and interleave L/R to ShortArray
         └─ Send to platform audio output
```

## KlangCommLink — IPC Protocol

File: `audio_bridge/src/commonMain/kotlin/infra/KlangCommLink.kt`

Two `KlangRingBuffer` channels: `frontend→backend` (Cmd) and `backend→frontend` (Feedback).

### Cmd (frontend → backend)

| Cmd subclass          | Payload                     | Effect                                      |
|-----------------------|-----------------------------|---------------------------------------------|
| `Cmd.ScheduleVoice`   | `ScheduledVoice`            | enqueue a voice for future playback         |
| `Cmd.ReplaceVoices`   | `List<ScheduledVoice>`      | replace entire pending queue atomically     |
| `Cmd.ClearScheduled`  | —                           | discard all pending (not yet active) voices |
| `Cmd.Cleanup`         | —                           | stop all voices, reset state                |
| `Cmd.Sample.Complete` | `SampleRequest` + PCM bytes | deliver decoded sample to backend           |
| `Cmd.Sample.NotFound` | `SampleRequest`             | signal that a sample could not be decoded   |
| `Cmd.Sample.Chunk`    | partial PCM data            | streaming delivery for large samples        |

### Feedback (backend → frontend)

| Feedback subclass          | Payload         | Effect                                 |
|----------------------------|-----------------|----------------------------------------|
| `Feedback.RequestSample`   | `SampleRequest` | ask frontend to load + decode a sample |
| `Feedback.SampleReceived`  | `SampleRequest` | confirm a sample was accepted          |
| `Feedback.PlaybackLatency` | latency info    | diagnostics for timing calibration     |
| `Feedback.Diagnostics`     | engine stats    | debug info (voice count, CPU, etc.)    |

## Platform Backends

### JVM (`audio_be/src/jvmMain/`)

- `JvmAudioBackend` uses `javax.sound.sampled.SourceDataLine`
- Format: 16-bit signed PCM, stereo, little-endian
- Buffer: ~250 ms for glitch-free playback
- `KlangTime.internalMsNow()` → `System.nanoTime() / 1_000_000L`

### JS (`audio_be/src/jsMain/`)

- `JsAudioBackend` creates an `AudioContext` + `AudioWorkletNode`
- Stereo: 2-channel explicit setup
- Latency hint: `"playback"` (stability over low latency)
- AudioContext auto-resumes on first voice (no user-gesture gate needed)
- ES2015 class output required for AudioWorkletProcessor inheritance
- `KlangTime.internalMsNow()` → `performance.now()`

### audio_jsworklet (`src/jsMain/`)

- `KlangAudioWorklet` extends `AudioWorkletProcessor`
- Runs in worklet thread (separate from main JS thread)
- Communicates with `JsAudioBackend` via `WorkletContract` + `KlangCommLink`
- Detects `sampleRate` and `blockSize` at runtime from `AudioWorkletGlobalScope`
- Registered via `@JsName("KlangAudioWorklet")` decorator

## Key Constants

| Constant             | Value   | Location             |
|----------------------|---------|----------------------|
| Max orbits           | 16      | `Orbits`             |
| Limiter threshold    | −1 dB   | `KlangAudioRenderer` |
| Limiter ratio        | 20:1    | `KlangAudioRenderer` |
| Limiter attack       | 1 ms    | `KlangAudioRenderer` |
| Limiter release      | 100 ms  | `KlangAudioRenderer` |
| Block size (typical) | 128–256 | platform backend     |
