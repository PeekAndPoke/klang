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
  │  KlangCommLink.frontend.send(Cmd.ScheduleVoices(...))   (batches; the single ScheduleVoice is not on the hot path)
  │  KlangCommLink.frontend.send(Cmd.ReplaceVoices(...))
  ▼
KlangRingBuffer (a small ring guarded by a lock: ReentrantLock on the JVM, a no-op lock in JS)
  │  JVM: the audio thread drains the ring at the top of every block (JvmAudioBackend)
  │  JS:  the ring stays on the main thread; JsAudioBackend.pump() drains it into the worklet port
  │       (postMessage), and the worklet decodes each Cmd in port.onmessage (KlangAudioWorklet)
  ▼
AUDIO BACKEND (audio thread)
  │
  ├─ KlangCommLink.backend.poll()  →  Cmd sealed class
  │    → PlaybackEngineDispatcher routes each Cmd by playbackId to its own PlaybackEngine (see below)
  │
  ├─ Cmd.ScheduleVoice   → that engine's VoiceScheduler.scheduleVoice()
  │                           stores ScheduledVoice in min-heap (keyed by startTime)
  │
  ├─ Cmd.ReplaceVoices   → that engine's VoiceScheduler.replaceVoices (grace-cutoff replace + dedup)
  │
  └─ Every block (128 frames, pinned):
       VoiceScheduler.processBlock()
         ├─ Activate due voices → a Voice running its Ignitor tree
         │    (requests MonoSamplePcm from audio_fe if needed)
         ├─ Call voice.render(ctx) for each active voice
         │    └─ Voice writes into ctx.voiceBuffer (FloatArray)
         └─ Mix voice output into its Cylinder (ctx.orbits[orbitId])

       Cylinders.processAndMix()
         ├─ Apply per-cylinder effects: Delay → Reverb → Phaser
         ├─ Apply cross-cylinder Ducking (sidechain)
         └─ Mix all orbits to the playback's bus (straight into the shared mix while no master is active
            and the engine is not releasing)

       MasterBus.process()   (only while a master chain is in service or leaving it)
         └─ The playback's master: a KatalystChain on the summed orbits, swapped by ChainSwap
            (phase 3 step 12; `master(Katalyst(k => ...))`)

       KlangAudioRenderer
         ├─ Apply DC blockers, then master limiter (−1 dB, 20:1, 5 ms lookahead)
         ├─ Clip ±1.0 into the floating-point output (a StereoBuffer, no quantisation)
         └─ Send to platform audio output (Web Audio takes the floats; the JVM line and the
            WAV writer convert to 16-bit bytes at their edge, `writePcm16`)
```

## Per-Playback Engine Isolation

The backend is **not** a single global engine — it is sharded per playback.
`PlaybackEngineDispatcher` (`audio_be/src/commonMain/kotlin/PlaybackEngineDispatcher.kt`) routes
**every** `Cmd` by its `playbackId` to a dedicated `PlaybackEngine`, created lazily on first use and
disposed once idle.

Each `PlaybackEngine` owns its **entire render state**:

- its own `VoiceScheduler` (the scheduled min-heap + the active-voice list),
- its own `Cylinders` (the 16 orbits and their effects),
- its own **forks** of the ignitor and Katalyst registries: the inline instruments and chains
  registered for that playback live there, not on the shared parents, so they die with the engine (the
  pipeline fork retired with the Pipeline DSL, phase 3 step 9). The one Katalyst fork serves the orbits AND the
  output (`MasterBus`); the Master registry retired in phase 3 step 12.
- its own `MasterBus`: the playback's master chain and its swap.

**Shared across engines** (not per-playback): the `SampleStore`, the backend clock
(`AudioBackendContext` / `BackendClock`), and the ignitor and Katalyst **parent**
registries (the forks inherit the built-ins from them).

**Why**: with a single global cylinder pool, two playbacks using the same orbit id would collide
(last-writer-wins). Per-engine isolation fixes that — two playbacks on orbit 0 get independent
cylinders.

**Invariant that matters when coding**: a `VoiceScheduler` — and everything it holds (`scheduled`,
`active`) — belongs to **exactly one playbackId**. So inside a scheduler, **every voice already
shares one playbackId**; comparing `playbackId` between voices there is always true and redundant
(see `ScheduledVoice.isDuplicate`, which deliberately omits it). The `playbackId` filters that DO
appear (`clearScheduled`, the `replaceVoices` scheduled-removal) are belt-and-suspenders, not because
a scheduler can ever mix playbacks.

Test harness: `PlaybackEngineDispatcherTest` drives the real dispatcher — `d.handle(cmd)`,
`d.engine(pid)?.scheduler`, `d.activePlaybackIds`, and `d.renderBlock(cursorFrame, out)` to advance
the clock (which promotes due voices).

## KlangCommLink — IPC Protocol

File: `audio_bridge/src/commonMain/kotlin/infra/KlangCommLink.kt`

Two `KlangRingBuffer` channels: `frontend→backend` (Cmd) and `backend→frontend` (Feedback).

### Cmd (frontend → backend)

| Cmd subclass          | Payload                                 | Effect                                                                                           |
|-----------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------|
| `Cmd.ScheduleVoice`   | `ScheduledVoice`                        | enqueue a voice for future playback                                                              |
| `Cmd.ReplaceVoices`   | `List<ScheduledVoice>` + `afterTimeSec` | live update: drop scheduled voices past a grace cutoff, reschedule, dedup vs still-active voices |
| `Cmd.ClearScheduled`  | —                                       | discard all pending (not yet active) voices                                                      |
| `Cmd.Cleanup`         | —                                       | stop all voices, reset state                                                                     |
| `Cmd.RegisterIgnitor` | name + `IgnitorDsl`                     | register an inline instrument on the playback's ignitor fork                                    |
| `Cmd.RegisterKatalyst`| name + `KatalystDsl`                    | register a chain on the playback's Katalyst fork (for `katalyst(...)` and `master(...)`)        |
| `Cmd.Sample.Complete` | `SampleRequest` + PCM bytes             | deliver decoded sample to backend                                                                |
| `Cmd.Sample.NotFound` | `SampleRequest`                         | signal that a sample could not be decoded                                                        |
| `Cmd.Sample.Chunk`    | partial PCM data                        | streaming delivery for large samples                                                             |

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
- Format: 16-bit signed PCM, stereo, little-endian; the engine's float output is converted at this edge by
  `writePcm16` (`audio_be/src/commonMain/kotlin/_pcm16_edge.kt`, the one home of the 16-bit conversion)
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

| Constant             | Value                                        | Location             |
|----------------------|----------------------------------------------|----------------------|
| Max orbits           | 16                                           | `Cylinders`          |
| Limiter threshold    | −1 dB                                        | `MasterLimiterDefaults.kt`, used by `MasterStage` |
| Limiter ratio        | 20:1                                         | `MasterLimiterDefaults.kt`, used by `MasterStage` |
| Limiter attack       | 5 ms — gain-SMOOTHING length, not a one-pole | `MasterStage`        |
| Limiter lookahead    | 5 ms — delays the whole output uniformly     | `MasterStage`        |
| Limiter release      | 100 ms                                       | `MasterLimiterDefaults.kt`, used by `MasterStage` |
| Block size           | 128, pinned everywhere (a tone parameter)    | `AudioBackendContext.RENDER_QUANTUM_FRAMES` |
