# Klang Audio — Memory

## Current Status

- **JVM**: Full audio output via javax.sound.sampled ✅
- **JS**: Full audio output via Web Audio API + AudioWorklet ✅
- Synthesis: oscillators (sine/saw/square/triangle/supersaw/noise) + sample playback
- Effects: delay, reverb, phaser, compressor, ducking, distortion, bit-crush, tremolo

## Architecture Decisions

- **Block-based processing**: fixed-size blocks (128–256 frames). No per-sample allocation in hot paths.
- **Ring-buffer IPC**: `KlangCommLink` uses two `KlangRingBuffer`s — no locking between threads.
- **Sealed `Voice` interface**: `Voice` defines contract; `AbstractVoice` implements ~80% of DSP;
  `SynthVoice`/`SampleVoice` only implement `generateSignal()`.
- **Orbits = effect buses**: up to 16 mixing channels, each with independent delay/reverb/phaser/compressor/ducking.
- **Master limiter**: −1 dB threshold, 20:1 ratio, 1 ms attack, 100 ms release — always last in chain.
- **`NullLiteral` / singletons**: `audio_bridge` data types use data classes; expect/actual for platform types.

## SignalGen Composable Architecture (2026-03-19)

New package `audio_be/.../signalgen/` — composable per-voice effect combinators.
Files: SignalGen, SignalContext, ScratchBuffers, SignalGenEnvelopes, SignalGenFilters,
SignalGenEffects, SignalGenPitchMod, SignalGenFm. Phase 0+1 complete (additive, nothing wired in yet).

### Known Issues to Revisit

- **Filter envelope release jumps from sustainLevel**: `computeFilterEnvelope()` always ramps
  release from `sustainLevel`, not from the actual envelope position at gate end. If gate ends
  during attack/decay, there's a discontinuity. Same bug exists in source `calculateFilterEnvelope()`.
  Fix: capture actual level at release onset (like the amplitude ADSR does).
- **`pitchEnvelope()` per-sample `pow()`**: `2.0.pow(amount * envLevel / 12.0)` called per sample.
  Expensive (~50-100ns per call). Could optimize sustain phase (constant value, compute once).
  `accelerate()` was already optimized to multiplicative stepping.
- **phaseMod save/restore lacks exception safety**: No try/finally wrapper around the
  save→set→generate→restore pattern in vibrato/accelerate/pitchEnvelope/fm combinators.
  Low risk (exceptions in audio hot paths are rare and fatal), but not structurally safe.
- **Stringly-typed distortion shape**: `distort(amount, shape = "soft")` uses String for shape
  selection. Typos silently fall through to default. Consider enum in the future.

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Orbits` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- `duckOrbit` in `VoiceData` sets the orbit ID to duck when a voice plays; ducking is cross-orbit sidechain.
