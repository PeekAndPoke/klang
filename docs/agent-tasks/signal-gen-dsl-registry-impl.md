# SignalGenDsl + Registry Implementation Status

**Branch:** `signal-gen-01`
**Date:** 2026-03-20

## What Was Implemented

Serializable DSL (`SignalGenDsl` sealed class hierarchy) + `SignalGenRegistry` + SignalGen as first-class
citizen in SynthVoice. Replaces the hard-coded `SignalGenOscillators` POC.

## Architecture

```
audio_bridge (shared, serializable):
  SignalGenDsl.kt        — sealed class hierarchy (Sine, Sawtooth, Plus, Lowpass, Fm, ...)
                           + builder extension functions (.lowpass(), .detune(), ...)

audio_be (backend only):
  SignalGenDslRuntime.kt — SignalGenDsl.toSignalGen() walks tree → runtime SignalGen instances
  SignalGenRegistry.kt   — single source of truth for oscillator lookup
                           wraps both DSL registry and legacy Oscillators
                           caches DSL-to-factory mappings, invalidates on re-register
                           createSignalGen(name, data, freqHz) → SignalGen
  SignalGenDefaults.kt   — registerDefaults() populates built-in oscillators
  SignalGenBridge.kt     — OscFn.toSignalGen() wrapper for legacy oscillators
  PlaybackCtx.kt         — per-playback context (forked registry + epoch)
  SynthVoice.kt          — takes SignalGen + SignalContext directly (no more OscFn)
```

## Files Created

| File                                                                  | Module | Purpose                                                      |
|-----------------------------------------------------------------------|--------|--------------------------------------------------------------|
| `audio_bridge/src/commonMain/kotlin/SignalGenDsl.kt`                  | bridge | Sealed class: primitives, composition, filters, envelope, FM |
| `audio_bridge/src/commonTest/kotlin/SignalGenDslSerializationTest.kt` | bridge | Round-trip JSON serialization tests                          |
| `audio_be/src/commonMain/kotlin/signalgen/SignalGenDslRuntime.kt`     | be     | `SignalGenDsl.toSignalGen()` extension                       |
| `audio_be/src/commonMain/kotlin/signalgen/SignalGenRegistry.kt`       | be     | Registry class with `fork()`                                 |
| `audio_be/src/commonMain/kotlin/signalgen/SignalGenDefaults.kt`       | be     | `registerDefaults()` — all built-in oscillators              |
| `audio_be/src/commonMain/kotlin/voices/PlaybackCtx.kt`                | be     | Per-playback context                                         |
| `audio_be/src/commonTest/kotlin/signalgen/SignalGenDslRuntimeTest.kt` | be     | Runtime instantiation tests                                  |
| `audio_be/src/commonTest/kotlin/signalgen/SignalGenRegistryTest.kt`   | be     | Registry + defaults tests                                    |

## Files Modified

| File                               | Changes                                                                                                                                                                                                                                                                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `VoiceScheduler.kt`                | Removed `Oscillators` from Options (registry wraps it now), `signalGenRegistry` is the sole oscillator lookup. `isOscillator()` → single `registry.contains()` call. `makeVoice()` → single `registry.createSignalGen()` call. Removed `createOscillator()` helper. Replaced `playbackEpochs` with `playbackContexts: Map<String, PlaybackCtx>` |
| `SynthVoice.kt`                    | Takes `signal: SignalGen` + `signalCtx: SignalContext` + `freqHz` instead of `osc: OscFn` + `phaseInc` + `phase`                                                                                                                                                                                                                                |
| `SignalGenBridge.kt`               | **Replaced contents** — now has `OscFn.toSignalGen()` (reverse bridge for legacy oscillators), old `SignalGen.toOscFn()` removed                                                                                                                                                                                                                |
| `JvmAudioBackend.kt`               | Creates `SignalGenRegistry(legacyOscillators=...)`, no longer passes `Oscillators` to VoiceScheduler                                                                                                                                                                                                                                            |
| `KlangAudioWorklet.kt`             | Same                                                                                                                                                                                                                                                                                                                                            |
| `KlangBenchmark.kt`                | Same                                                                                                                                                                                                                                                                                                                                            |
| `VoiceSchedulerDiagnosticsTest.kt` | Updated to pass registry                                                                                                                                                                                                                                                                                                                        |
| `VoiceTestHelpers.kt`              | `createSynthVoice` now takes `signal: SignalGen` param, added `TestSignalGens` object                                                                                                                                                                                                                                                           |
| `SynthVoiceTest.kt`                | Rewritten for SignalGen interface                                                                                                                                                                                                                                                                                                               |
| `FilterModulationTest.kt`          | Updated SynthVoice instantiation                                                                                                                                                                                                                                                                                                                |

## Files Deleted

| File                      | Reason                                                |
|---------------------------|-------------------------------------------------------|
| `SignalGenOscillators.kt` | Replaced by `SignalGenRegistry` + `SignalGenDefaults` |

## SignalGenDsl Node Types

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

- **SignalGenDsl serialization** — all node types round-trip tested (JSON encode/decode)
- **SignalGenRegistry** — register, get, contains, names, fork, registerDefaults
- **SignalGenDslRuntime** — all primitives produce non-zero output, independence of instances
- **SynthVoice** — basic signal generation, pitch modulation passthrough, elapsed frame tracking, envelope, partial
  blocks
- **VoiceScheduler diagnostics** — diagnostics timing, orbit reporting, voice count
- **Filter modulation** — envelope modulation of filter cutoff (via SynthVoice)

### Needs More Coverage

- **OscFn.toSignalGen() bridge** — no dedicated unit tests. Verified indirectly through VoiceScheduler integration
  tests (legacy oscillators like "sine" still work). Should test:
    - Phase continuity across blocks
    - Frequency accuracy (phaseInc computation from freqHz)
    - PhaseMod passthrough
- **PlaybackCtx** — no dedicated tests. Verified indirectly through VoiceScheduler. Should test:
    - `fork()` produces independent registry
    - Epoch tracking across playback lifecycle
- **SignalGenDslRuntime with complex trees** — tests verify non-zero output but don't validate audio correctness. Should
  test:
    - `Detune` actually shifts frequency (compare output to known frequency)
    - `Mul`/`Div` actually scale amplitude
    - `Lowpass` attenuates high frequencies
    - `Adsr` envelope shape over time
    - FM depth and ratio produce expected sidebands
- **VoiceScheduler registry integration** — no test that verifies a SignalGenDsl-registered oscillator is actually used
  when `sound("sgpad")` is scheduled. Should test:
    - Registry-based oscillator lookup path (vs legacy fallback)
    - Per-playback registry fork isolation
- **SynthVoice with real SignalGen** — current tests use TestSignalGens (constant/ramp/silence). Should test:
    - Actual sine/sawtooth output through the full voice pipeline
    - SignalGen compositions (Plus, FM) rendered through SynthVoice

## What's NOT Implemented Yet (from the plan doc)

- **Frontend waveform preview** — `SignalGenDsl.renderPreview()` in `audio_bridge` (common code). Lightweight pure-math
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
