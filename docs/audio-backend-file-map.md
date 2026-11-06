# Shared Audio Backend — File Map

Reference map of the backend for walking the code. The shared backend is `audio_be/src/commonMain`
(platform-independent DSP, used by both JVM and the JS AudioWorklet). Tags: **[NEW]** / **[changed]**
mark files created/modified by the per-playback-engine work (D1 + D2 — see
KLANG-00176 (per playback engine)).

## Signal flow (the spine)

```
Cmd → PlaybackEngineDispatcher.handle
    → per-pid PlaybackEngine { VoiceScheduler → VoiceFactory builds Voice
        → Voice render (Ignite → (teardown fade) → Send) → Cylinder mix
    → Cylinders.processAndMix (per cylinder: its Katalyst chain, swapped through ChainSwap)
    → MasterBus.process (the Katalyst chain at the output; skipped while it is off) }
    → accumulate into the shared mix
    → HouseStage (DC block → limiter[5 ms lookahead] → clip)
    → StereoBuffer out (floating point; the JVM line and the WAV writer convert to 16-bit at their edge)
```

## Changed/created by the per-playback-engine work (D1 + D2)

| File                                                     | What                                                                                                              |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `PlaybackEngineDispatcher.kt` **[NEW]**                  | Host: `Map<playbackId, PlaybackEngine>`, `handle(cmd)` routing, `renderBlock` mixdown, drain-dispose lifecycle    |
| `PlaybackEngine.kt` **[NEW]**                            | One isolated engine per playback (`VoiceScheduler` + `Cylinders` + `renderInto` + `isIdle`)                       |
| `SampleStore.kt` **[NEW]**                               | Shared PCM sample cache (extracted from `VoiceScheduler`)                                                         |
| `HouseStage.kt` **[NEW]**                               | Final stage: DC block → safety limiter (5 ms lookahead) → clip + interleave (extracted from `KlangAudioRenderer`; float output, interleave and 16-bit at the JVM/WAV edge since 2026-10-07) |
| `KlangAudioRenderer.kt` **[changed]**                    | Now a thin standalone wrapper delegating to `PlaybackEngine.renderInto + HouseStage` (offline + benchmarks)      |
| `WarmupRunner.kt` **[changed]**                          | Runs on a dedicated warmup engine via the dispatcher; **dropped global `preallocateAll`**                         |
| `cylinders/Cylinders.kt` **[changed]**                   | Added alloc-free `anyActive()`                                                                                    |
| `voices/VoiceScheduler.kt` **[changed]**                 | Sample cache delegated to `SampleStore`                                                                           |
| `voices/VoiceFactory.kt` **[changed]**                   | One `SampleStore.SampleEntry` type reference                                                                      |
| `jvmMain/JvmAudioBackend.kt` **[changed]**               | Thin pump → `dispatcher.handle` / `dispatcher.renderBlock`                                                        |
| `audio_jsworklet/.../KlangAudioWorklet.kt` **[changed]** | Thin pump (same)                                                                                                  |

> ⚠️ `voices/PlaybackCtx.kt` is **not** `PlaybackEngine` — it's the per-pid context *inside* a
> scheduler (epoch + forked ignitor registry). Unchanged.

## Host / orchestration

- `PlaybackEngineDispatcher.kt` **[NEW]** — the host (see above).
- `PlaybackEngine.kt` **[NEW]** — per-playback engine.
- `SampleStore.kt` **[NEW]** — shared sample cache.
- `HouseStage.kt` **[NEW]**: final output stage.
- `KlangAudioRenderer.kt` **[changed]** — standalone single-engine render-to-PCM.
- `WarmupRunner.kt` **[changed]** — JIT/cache priming; emits `BackendReady`.
- `AudioBackend.kt` — the `AudioBackend` interface + `Config`.

## Voices: scheduling + per-voice render

- `voices/VoiceScheduler.kt` **[changed]** — scheduled heap, active voices, solo/mute, epoch, promote loop, diagnostics
  emit.
- `voices/VoiceFactory.kt` **[changed]**: builds a `Voice` (the instrument's Ignitor tree and the stages after it)
  from `VoiceData`.
- `voices/Voice.kt` — running voice + `RenderContext` (per-engine scratch + cylinders) + per-block render.
- `voices/VoiceLimits.kt`: a voice's onset, gate end and end, their one home (the voice writes, the stages read).
- `voices/PlaybackCtx.kt` — per-pid context inside a scheduler (epoch + ignitor fork).
- The voice's fixed stages are plain code in `Voice.render` since 2026-11-06 (KLANG-00290 item 21; the per-block
  stage framework, `BlockContext` and `BlockRenderer`, is gone): the tree (`Voice.ignite`, every pitch door a
  `classic()` stage of it), `voices/teardown_fade.kt` (`applyTeardownFade`, after a tree for which
  `BuiltIgnitor.endsInEnvelope` is false), `voices/voice_channel.kt` (`mixIntoCylinder`: pans and sums the voice into
  its cylinder, the `Cylinders.offer` / `checkIn` routing seam).

## Cylinders and the master: the Katalyst hosts

- `cylinders/Cylinders.kt` **[changed]**: `Map<cylinderId, Cylinder>`, additive `processAndMix`, round-robin cleanup.
- `cylinders/Cylinder.kt`: one cylinder: its `KatalystChain` (the born-with `Katalyst.classic`, or a declared one),
  its owner (the newest `Sounding` voice: `offer`, `commitOwner`), the check-in, its `ChainHost` (the chain cache and the swap),
  `tryDeactivate` (tail check).
- `katalyst/`: `KatalystChain` + `KatalystChainBuilder` (the wire `KatalystDsl` to stages), one effect per
  stage (`Eq`, `Gain`, `Delay`, `Reverb`, `Phaser`, `Compressor`, `Resonator` for body and vowel (one class, two kinds; its
  tables `ResonatorTables`), `Duck`),
  `KatalystFilterSwap` (the body and vowel bank crossfade), `KatalystSlots` + `KatalystSlotWriters` (`katp`),
  `KatalystRegistry` (one fork per playback, serving cylinders and the output), `KatalystContext`.
- `master/MasterBus.kt`: the same `KatalystChain` at the output position (phase 3 step 12); `master(Katalyst())`
  switches it off.
- `ChainHost.kt` (the chain plumbing both hosts own: the chain in service, the cache, the parked request, the lookup,
  KLANG-00243), `ChainSwap.kt` (the swap law both hosts share: fade the leaving chain's input, drain its tail, release it after
  `MAX_DRAIN_SECONDS`), `Crossfade.kt`, `TailRelease.kt` (the one home of the release law), `KnobGlide.kt`
  (cylinder knobs glide over `KNOB_GLIDE_SECONDS`).

## Effects (DSP building blocks)

- `effects/` — `Compressor.kt` (also the limiter), `DelayLine.kt`, `Reverb.kt`, `Phaser.kt` + `PhaserCore.kt`,
  `Ducking.kt`.

## Filters

- `filters/`: `AudioFilter`, `LowPassHighPassFilters` (the SVF coefficient helpers, DcBlocker, `createVowel`/`createBody`,
  `bodyGain`/`vowelGain`), `ResonatorBank` with `ResonatorTable` and `ResonatorConfig`, `EqCore`, `WetDryMix`,
  `SvfCoeffSweep` (the per-block coefficient glide of `Ignitor.svf`).

## Ignitors (oscillators / exciters)

- core: `Ignitor.kt`, `Ignitors.kt`, `IgnitorRegistry.kt`, `IgnitorDslRuntime.kt`, `IgnitorDefaults.kt`.
- synthesis: `FreqIgnitor`, `ConstantIgnitor`, `ParamIgnitor`, `SampleIgnitor`, `MemoizingIgnitor`,
  `ModApplyingIgnitor`, `WaveVoiceState`, `OscillatorTuning`, `PitchModFactories`.
- character: `AnalogDrift` + `AnalogDriftCoeffs`, `DriftLanes`, `IgnitorEnvelopes`, `IgnitorEffects`,
  `IgnitorFilters`, `IgniteContext`, `ScratchBuffers.kt` (per-engine scratch pool).

## Engines / primitives / math

- `StereoBuffer.kt`, `AudioSample.kt`, `Oversampler.kt`, `ShapingFuncs.kt`, `DistortionShape.kt`,
  `AdsrCurveMath.kt`, `AudioAnalyzer.kt`, `IndexCommon.kt`.
- `utils/`: the domain-free helpers (the fast math, the numeric guards, the phase wraps, the buffer copy,
  the fade to zero, the time constant, the ramp step, `retainInOrder`); `jsMain/.../utils/js_objects.kt`.

## Platform entrypoints (thin pumps — not shared)

- `audio_be/src/jvmMain/.../JvmAudioBackend.kt` **[changed]** — `SourceDataLine` loop. (`index_jvm.kt` — bootstrap.)
- `audio_jsworklet/src/jsMain/.../KlangAudioWorklet.kt` **[changed]** — `AudioWorklet` processor. (`init.kt` —
  bootstrap.)

## Wire contract (`audio_bridge/commonMain` — shared FE↔BE; we use it, didn't change it)

- `infra/KlangCommLink.kt` — the protocol: `Cmd` (in) + `Feedback` (out, incl. `Diagnostics`).
- `ScheduledVoice.kt`, `VoiceData.kt`, `KlangPattern.kt`/`KlangPatternEvent.kt`, `SampleRequest.kt`/`MonoSamplePcm.kt`/
  `SampleMetadata.kt`, `IgnitorDsl.kt` + `IgnitorDslClassic.kt`, `KatalystDsl.kt`, `KlangTime.kt`, `WireFormat`/`WireName`.

## Tests (`audio_be/src/commonTest`)

- `PlaybackEngineDispatcherTest.kt` **[NEW]** (9 tests, incl. isolation + ReplaceVoices-leak gates),
  `SampleStoreSpec.kt` **[NEW]**, `HouseStageSpec.kt` **[NEW]**.
- existing: `KlangAudioRendererSpec`, `voices/VoiceSchedulerDiagnosticsTest`, `cylinders/CylindersCleanupTest`.
