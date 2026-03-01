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

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Orbits` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- `duckOrbit` in `VoiceData` sets the orbit ID to duck when a voice plays; ducking is cross-orbit sidechain.
