# Klang Audio — Memory

## Current Status

- **JVM**: Full audio output via javax.sound.sampled ✅
- **JS**: Full audio output via Web Audio API + AudioWorklet ✅
- Synthesis: oscillators (sine/saw/square/triangle/supersaw/noise) + sample playback
- Effects: delay, reverb, phaser, compressor, ducking, distortion, bit-crush, tremolo

## Architecture Decisions

- **Block-based processing**: fixed-size blocks (128–256 frames). No per-sample allocation in hot paths.
- **Ring-buffer IPC**: `KlangCommLink` uses two `KlangRingBuffer`s — no locking between threads.
- **Voice pipeline**: `Voice` interface + `VoiceImpl` runs a **Pitch → Excite → Filter** pipeline.
  Filter stage is a composable `List<BlockRenderer>` built by `buildFilterPipeline()`.
  Pitch stage is still inline (pending extraction). Excite delegates to `Exciter`.
- **Orbits = effect buses**: up to 16 mixing channels, each with independent delay/reverb/phaser/compressor/ducking.
- **Master limiter**: −1 dB threshold, 20:1 ratio, 1 ms attack, 100 ms release — always last in chain.
- **`NullLiteral` / singletons**: `audio_bridge` data types use data classes; expect/actual for platform types.

## Exciter Composable Architecture (2026-03-19)

New package `audio_be/.../exciter/` — composable per-voice effect combinators.
Files: Exciter, ExciteContext, ScratchBuffers, ExciterEnvelopes, ExciterFilters,
ExciterEffects, ExciterPitchMod, ExciterFm. Phase 0+1 complete (additive, nothing wired in yet).

### Known Issues to Revisit

- ~~**Filter envelope release jumps from sustainLevel**~~ **FIXED (2026-03-23)**:
  `FilterModRenderer` now calculates the actual envelope level at gate end using
  `levelAtPosition()`, matching the amplitude ADSR's capture behavior.
- **`pitchEnvelope()` per-sample `pow()`**: `2.0.pow(amount * envLevel / 12.0)` called per sample.
  Expensive (~50-100ns per call). Could optimize sustain phase (constant value, compute once).
  `accelerate()` was already optimized to multiplicative stepping.
- **phaseMod save/restore lacks exception safety**: No try/finally wrapper around the
  save→set→generate→restore pattern in vibrato/accelerate/pitchEnvelope/fm combinators.
  Low risk (exceptions in audio hot paths are rare and fatal), but not structurally safe.
- **Stringly-typed distortion shape**: `distort(amount, shape = "soft")` uses String for shape
  selection. Typos silently fall through to default. Consider enum in the future.

## BlockRenderer Pipeline Architecture (2026-03-23)

Voice rendering refactored into **Pitch → Excite → Filter** pipeline using composable `BlockRenderer` stages.

Key files:

- `voices/strip/BlockRenderer.kt` — `fun interface BlockRenderer { fun render(ctx: BlockContext) }`
- `voices/strip/BlockContext.kt` — shared context (buffers, timing, exciter)
- `voices/strip/EnvelopeCalc.kt` — shared control-rate envelope calculation
- `voices/strip/pitch/` — VibratoRenderer, AccelerateRenderer, PitchEnvelopeRenderer, FmRenderer
- `voices/strip/excite/ExciteRenderer.kt` — wraps Exciter as BlockRenderer
- `voices/strip/filter/` — FilterModRenderer, AudioFilterRenderer, EnvelopeRenderer, FilterPipelineBuilder

Status: **All stages extracted.** VoiceImpl runs a single `List<BlockRenderer>` pipeline:
Pitch renderers → ExciteRenderer → Filter renderers → mixToOrbit.
Shared envelope calculation in `EnvelopeCalc.kt`. Rename SignalGen → Exciter completed.
See `docs/agent-tasks/voice-pipeline-refactor.md` for full plan.

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Orbits` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- `duckOrbit` in `VoiceData` sets the orbit ID to duck when a voice plays; ducking is cross-orbit sidechain.
