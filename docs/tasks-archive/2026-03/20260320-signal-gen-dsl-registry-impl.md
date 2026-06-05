# ExciterDsl + Registry Implementation Status

**Branch:** `signal-gen-01`
**Date:** 2026-03-20

## What Was Implemented

Serializable DSL (`ExciterDsl` sealed class hierarchy) + `ExciterRegistry` + Exciter as first-class
citizen in SynthVoice. Replaces the hard-coded `ExciterOscillators` POC.

## Architecture

```
audio_bridge (shared, serializable):
  ExciterDsl.kt        — sealed class hierarchy (Sine, Sawtooth, Plus, Lowpass, Fm, ...)
                           + builder extension functions (.lowpass(), .detune(), ...)

audio_be (backend only):
  ExciterDslRuntime.kt — ExciterDsl.toExciter() walks tree → runtime Exciter instances
  ExciterRegistry.kt   — single source of truth for oscillator lookup
                           wraps both DSL registry and legacy Oscillators
                           caches DSL-to-factory mappings, invalidates on re-register
                           createExciter(name, data, freqHz) → Exciter
  ExciterDefaults.kt   — registerDefaults() populates built-in oscillators
  ExciterBridge.kt     — OscFn.toExciter() wrapper for legacy oscillators
  PlaybackCtx.kt         — per-playback context (forked registry + epoch)
  SynthVoice.kt          — takes Exciter + SignalContext directly (no more OscFn)
```

## Files Created

| File                                                                | Module | Purpose                                                      |
|---------------------------------------------------------------------|--------|--------------------------------------------------------------|
| `audio_bridge/src/commonMain/kotlin/ExciterDsl.kt`                  | bridge | Sealed class: primitives, composition, filters, envelope, FM |
| `audio_bridge/src/commonTest/kotlin/ExciterDslSerializationTest.kt` | bridge | Round-trip JSON serialization tests                          |
| `audio_be/src/commonMain/kotlin/exciter/ExciterDslRuntime.kt`       | be     | `ExciterDsl.toExciter()` extension                           |
| `audio_be/src/commonMain/kotlin/exciter/ExciterRegistry.kt`         | be     | Registry class with `fork()`                                 |
| `audio_be/src/commonMain/kotlin/exciter/ExciterDefaults.kt`         | be     | `registerDefaults()` — all built-in oscillators              |
| `audio_be/src/commonMain/kotlin/voices/PlaybackCtx.kt`              | be     | Per-playback context                                         |
| `audio_be/src/commonTest/kotlin/exciter/ExciterDslRuntimeTest.kt`   | be     | Runtime instantiation tests                                  |
| `audio_be/src/commonTest/kotlin/exciter/ExciterRegistryTest.kt`     | be     | Registry + defaults tests                                    |

## Files Modified

| File                               | Changes                                                                                                                                                                                                                                                                                                                                       |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `VoiceScheduler.kt`                | Removed `Oscillators` from Options (registry wraps it now), `signalGenRegistry` is the sole oscillator lookup. `isOscillator()` → single `registry.contains()` call. `makeVoice()` → single `registry.createExciter()` call. Removed `createOscillator()` helper. Replaced `playbackEpochs` with `playbackContexts: Map<String, PlaybackCtx>` |
| `SynthVoice.kt`                    | Takes `signal: Exciter` + `signalCtx: SignalContext` + `freqHz` instead of `osc: OscFn` + `phaseInc` + `phase`                                                                                                                                                                                                                                |
| `ExciterBridge.kt`                 | **Replaced contents** — now has `OscFn.toExciter()` (reverse bridge for legacy oscillators), old `Exciter.toOscFn()` removed                                                                                                                                                                                                                  |
| `JvmAudioBackend.kt`               | Creates `ExciterRegistry(legacyOscillators=...)`, no longer passes `Oscillators` to VoiceScheduler                                                                                                                                                                                                                                            |
| `KlangAudioWorklet.kt`             | Same                                                                                                                                                                                                                                                                                                                                          |
| `KlangBenchmark.kt`                | Same                                                                                                                                                                                                                                                                                                                                          |
| `VoiceSchedulerDiagnosticsTest.kt` | Updated to pass registry                                                                                                                                                                                                                                                                                                                      |
| `VoiceTestHelpers.kt`              | `createSynthVoice` now takes `signal: Exciter` param, added `TestExciters` object                                                                                                                                                                                                                                                             |
| `SynthVoiceTest.kt`                | Rewritten for Exciter interface                                                                                                                                                                                                                                                                                                               |
| `FilterModulationTest.kt`          | Updated SynthVoice instantiation                                                                                                                                                                                                                                                                                                              |

## Files Deleted

| File                    | Reason                                            |
|-------------------------|---------------------------------------------------|
| `ExciterOscillators.kt` | Replaced by `ExciterRegistry` + `ExciterDefaults` |

## ExciterDsl Node Types

### Primitives

- `Sine(gain)`, `Sawtooth(gain)`, `Square(gain)`, `Triangle(gain)`, `WhiteNoise(gain)`, `Silence`

### Composition

- `Plus(left, right)`, `Times(left, right)`, `Mul(inner, factor)`, `Div(inner, divisor)`

### Frequency

- `Detune(inner, semitones)`

### Filters

- `Lowpass(inner, cutoffHz, q)`, `Highpass(inner, cutoffHz, q)`, `OnePoleLowpass(inner, cutoffHz)`

### Envelope

- `Adsr(inner, attackSec, decaySec, sustainLevel, releaseSec)`

### FM

- `Fm(carrier, modulator, ratio, depth, envAttackSec, envDecaySec, envSustainLevel, envReleaseSec)`

## Test Coverage Status

### Well Covered

- **ExciterDsl serialization** — all node types round-trip tested (JSON encode/decode)
- **ExciterRegistry** — register, get, contains, names, fork, registerDefaults
- **ExciterDslRuntime** — all primitives produce non-zero output, independence of instances
- **SynthVoice** — basic signal generation, pitch modulation passthrough, elapsed frame tracking, envelope, partial
  blocks
- **VoiceScheduler diagnostics** — diagnostics timing, orbit reporting, voice count
- **Filter modulation** — envelope modulation of filter cutoff (via SynthVoice)

### Needs More Coverage

- **OscFn.toExciter() bridge** — no dedicated unit tests. Verified indirectly through VoiceScheduler integration
  tests (legacy oscillators like "sine" still work). Should test:
    - Phase continuity across blocks
    - Frequency accuracy (phaseInc computation from freqHz)
    - PhaseMod passthrough
- **PlaybackCtx** — no dedicated tests. Verified indirectly through VoiceScheduler. Should test:
    - `fork()` produces independent registry
    - Epoch tracking across playback lifecycle
- **ExciterDslRuntime with complex trees** — tests verify non-zero output but don't validate audio correctness. Should
  test:
    - `Detune` actually shifts frequency (compare output to known frequency)
    - `Mul`/`Div` actually scale amplitude
    - `Lowpass` attenuates high frequencies
    - `Adsr` envelope shape over time
    - FM depth and ratio produce expected sidebands
- **VoiceScheduler registry integration** — no test that verifies a ExciterDsl-registered oscillator is actually used
  when `sound("sgpad")` is scheduled. Should test:
    - Registry-based oscillator lookup path (vs legacy fallback)
    - Per-playback registry fork isolation
- **SynthVoice with real Exciter** — current tests use TestExciters (constant/ramp/silence). Should test:
    - Actual sine/sawtooth output through the full voice pipeline
  - Exciter compositions (Plus, FM) rendered through SynthVoice

## FloatArray Migration (2026-03-20)

All audio signal buffers migrated from `DoubleArray` to `FloatArray` for 2x cache fit (~15-30% throughput).

**FloatArray (audio signal buffers):**

- `Exciter.generate(buffer: FloatArray, ...)`
- `OscFn.process(buffer: FloatArray, ...)`
- `AudioFilter.process(buffer: FloatArray, ...)`
- `StereoBuffer.left/right`, `ScratchBuffers` pool, `Voice.RenderContext.voiceBuffer`
- All filter implementations, effects (Reverb, DelayLine, Compressor, Ducking, Phaser)

**Stays DoubleArray (precision-sensitive):**

- `SignalContext.phaseMod` — frequency multipliers
- `Voice.RenderContext.freqModBuffer` — pitch modulation ratios
- Phase accumulators (`var phase = 0.0` inside oscillators)
- FM modulation buffers (`phaseModBuf` in `ExciterFm.kt`)
- Pitch modulation buffers in `ExciterPitchMod.kt`

**Current pattern in filters/effects:** IIR filter state (`ic1eq`, `ic2eq`, `y`, `xPrev`, coefficients)
is kept as `Double` with explicit `.toDouble()` on buffer read and `.toFloat()` on buffer write. This is
a no-op on JS (numbers are always f64 internally, Float32Array read/write does implicit conversion anyway)
and a single-cycle instruction on JVM. Could be simplified to Float state throughout — the Double precision
is not actually needed for our filter types (SVF, one-pole). Professional DAWs like Bitwig run entirely at
32-bit float. Left as-is for now; simplification is a low-risk cleanup task.

## What's NOT Implemented Yet (from the plan doc)

- **Frontend waveform preview** — `ExciterDsl.renderPreview()` in `audio_bridge` (common code). Lightweight pure-math
  renderer that fills a `DoubleArray` for display. Sine/saw/square/triangle are trivial math; compositions (Plus, Mul)
  compose the results; filters/FM can be approximated or shown as visual badges. No dependency on `audio_be` or
  `SignalContext`.
- **OscParamSchema** — parameterized oscillators (detune, cutoff controllable per-voice from strudel patterns)
- **VoiceData.osciParams** — transport for oscillator params
- **Strudel `.osciParam()` control** — pattern-based parameter control
- **KlangScript `registerOsc`** — per-playback oscillator registration from KlangScript
- **Feedback combinators** — `feedback()`, `feedbackTuned()`, `phaseFeedback()`
- **Time-windowed combinators** — `during()`, `duringProgress()`, `chain()`, `ring()`
- **ControlRate combinator** — block-rate computation for modulators
- **Additional primitives** — Karplus-Strong, wavetable, LFOGen, etc.
