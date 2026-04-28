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
  Pitch stage is still inline (pending extraction). Excite delegates to `Ignitor`.
- **Cylinders = effect buses**: up to 16 mixing channels, each with independent delay/reverb/phaser/compressor/ducking.
- **Master limiter**: −1 dB threshold, 20:1 ratio, 1 ms attack, 100 ms release — always last in chain.
- **`NullLiteral` / singletons**: `audio_bridge` data types use data classes; expect/actual for platform types.

## Numerical Safety Contract + Distort DC-Lock Fix (2026-04-27)

Established `SAFE_MIN = 1e-15f` / `SAFE_MAX = 1e15f` as the engine's numerical
safety bounds (matches SuperCollider/ChucK convention — see
`audio/ref/numerical-safety.md` for the reasoning and framework precedents).

Two helpers in `Ignitor.kt`: `safeDiv(d)` (sign-preserving magnitude clamp ≥ SAFE_MIN,
scrubs NaN) and `safeOut(v)` (output clamp to ±SAFE_MAX, scrubs NaN to 0).
Applied to divisor-class ops (`Div`, `Mod`, `Recip`) and output-clamp ops
(`Times`, `Pow`, `Exp`, `Sq`, `mul-by-const`).

Distort/clip path now applies the DC blocker **unconditionally** (was previously
only for `diode`/`rectify` shapes). Fixes user-reported rail-lock at extreme
drive amounts where output got stuck at -1 due to envelope-decay asymmetries.
Trade-off: transient overshoots up to ~2x at sharp transitions of square-like
signals — existing tests updated, master limiter handles the spike.

**Edge-overshoot bounder moved to ignitor output (2026-04-28)**: the 2×
DC-blocker transient was audible as per-cycle clicks on heavy-drive
`distort()`/`clip()` users (reported on the rhythm-pattern guitar ignitor in
`TestTextPatterns.kt`). The fix is a single `coerceIn(-1f, 1f)` (hard clip)
pass at the **`IgniteRenderer` boundary** — caps the entire ignitor output
once per output sample at base rate. This is cheaper than tanh-saturating
inside each `distort()`/`clip()` stage at oversampled rate, and identity for
`|x| ≤ 1` so existing tests + clean ignitors are untouched.

- **Why hard clip vs soft tanh wrap**: tanh compresses everywhere
  (`tanh(1)=0.78`, ~22% reduction at peak), changing exact-amplitude
  expectations across ~20 voice/envelope tests. Hard clip is identity for
  `|x| ≤ 1` (preserves all those expectations) and only acts on the rare
  rail-edge transients above ±1 — exactly the click case. Cheaper too
  (`coerceIn` is 2 FLOPs vs `fastTanh`'s ~6).

- **Why at the wrapper, not per-stage**: per-stage cap would fire inside the
  ignitor on `dcOut` peaks of ~±2; the wrapper fires on the final ignitor
  output (post the user's internal LPF/HPF/ADSR), once per output sample at
  base rate (vs `factor×` per oversampled sample inside each distort). Per-stage
  code is back to the pre-2026-04-28 simple `dcOut.toFloat()` write — no
  in-oversampler placement, no α rate-compensation, no per-stage tanh.

- **Trade-off**: hard clip adds a small derivative-discontinuity at ±1, which
  technically aliases. Mitigated because the wrapper sees the signal *after*
  the user's internal LPF/HPF (already band-limited), and the master limiter
  on the cylinder bus handles residuals.

- **Industry context** (researched while designing this): Surge XT and Vital
  don't post-DC-block at all — Klang's click symptom only exists because of
  the 2026-04-27 rail-lock guard. ChowDSP / Jatin Chowdhury use
  Antiderivative Antialiasing (ADAA) to side-step both oversampling *and*
  post-shape DC issues — captured as future direction.

- **Future**: ADAA migration to replace the oversampler+DC-block stack
  entirely (see `chowdsp_waveshapers` and `jatinchowdhury18/ADAA`). Drive
  smoothing (Surge XT's `lipol` pattern) for parameter-change clicks. Soft
  saturator option (tanh) at the wrapper as opt-in for "warmth" character.

- **Scope**: the wrapper lives in `audio_be/.../voices/strip/ignite/IgniteRenderer.kt`.
  `Ignitor.distort()`/`Ignitor.clip()` are responsible only for shape +
  DC-block (no longer for bounding to ±1) — see
  `audio_be/.../ignitor/IgnitorEffects.kt`. The legacy filter-stage
  `DistortionRenderer` (covered by `effects/DistortionSpec.kt`) is a separate
  code path and unchanged. End-to-end coverage: `IgnitorsTest.kt` has both a
  direct-call test (asserts `< 2.5`, the pre-cap envelope) and an
  IgniteRenderer-wrapped test (asserts `< 1.05`, the in-engine invariant).

- **Regression harness — grow it over time**:
  `audio_be/src/commonTest/kotlin/ignitor/GuitarClickHuntTest.kt` is the
  click-hunt harness. **Standing intent: keep adding setups to it whenever a
  new click symptom is reported or a new ignitor archetype is introduced.** It
  is the safety net for any future change to `distort`/`clip`/`IgniteRenderer`
  / DC-blocker / oversampler.

**Pitch-mod factories closed (same day)**: code review identified four pre-existing
hazards in `PitchModFactories.kt` (`vibratoModIgnitor`, `accelerateModIgnitor`,
`pitchEnvelopeModIgnitor`, `fmModIgnitor`) with the same overflow pattern. All
four now apply `safeOut`/`safeDiv`. Stress test for extreme depth exposed a
**latent O(N) hazard in `wrapPhase`** (`DspUtil.kt`) — the `while (p >= period)
p -= period` loop hung the audio thread for huge phase increments coming out of
a SAFE_MAX-clamped pitch-mod ratio. Rewritten as O(1) modulo with `Inf`/`NaN`
recovery; common-case fast subtraction path preserved.

**Generalised lesson** (see `audio/ref/numerical-safety.md` "Why this matters"):
the per-op safety contract guarantees **finite + bounded**, NOT **small**. Any
audio-rate consumer of a value clamped to `±SAFE_MAX` must run in O(1)
regardless of the value's magnitude — no subtraction loops, no retry loops, no
state scaling with input. The `wrapPhase` rewrite is the canonical pattern.

## Ignitor Composable Architecture (2026-03-19)

New package `audio_be/.../ignitor/` — composable per-voice effect combinators.
Files: Ignitor, IgniteContext, ScratchBuffers, IgnitorEnvelopes, IgnitorFilters,
IgnitorEffects, IgnitorPitchMod, IgnitorFm. Phase 0+1 complete (additive, nothing wired in yet).

## Ignitor Param Slots — "Everything is a Signal" (2026-03-26)

All numeric ignitor parameters converted from `Double` to `IgnitorDsl` (Param slots).
`IgnitorDsl.Param(name, default, description)` is a new leaf node that produces a constant signal
by default, but can be replaced with any ignitor subtree for audio-rate modulation.

Key changes:

- **`ParamIgnitor`** runtime class fills buffer with constant value
- **`getParamSlots()`** walks DSL tree to discover all Param leaves (for generic UI)
- **Gain separated from oscillators**: factories produce raw output, gain applied via `withGain()`
- **`analog`** param: lazy `AnalogDrift` init on first block via `initAnalogDrift()`
- **Control-rate params** (filter cutoff, ADSR times, etc.): read once per block via `readParam()`
- **oscParams override**: `toIgnitor(oscParams)` propagates through tree; Param nodes check map by name
- **New DSL nodes**: Distort, Crush, Coarse, Phaser, Tremolo, Vibrato, Accelerate, PitchEnvelope
- **Convenience wrappers**: Double-accepting extension functions on IgnitorDsl still work

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
- `voices/strip/BlockContext.kt` — shared context (buffers, timing, ignitor)
- `voices/strip/EnvelopeCalc.kt` — shared control-rate envelope calculation
- `voices/strip/pitch/` — VibratoRenderer, AccelerateRenderer, PitchEnvelopeRenderer, FmRenderer
- `voices/strip/excite/IgniteRenderer.kt` — wraps Ignitor as BlockRenderer
- `voices/strip/filter/` — FilterModRenderer, AudioFilterRenderer, EnvelopeRenderer, FilterPipelineBuilder

Status: **Complete.** `Voice` (merged from Voice interface + VoiceImpl) runs a `List<BlockRenderer>` pipeline:
Pitch renderers → IgniteRenderer → Filter renderers → SendRenderer.
Bus pipeline: composable `KatalystEffect` pipeline (`cylinders/bus/`).
`VoiceScheduler` split into `VoiceScheduler` (scheduling) + `VoiceFactory` (voice construction).
Legacy effect filters (BitCrush, SampleRateReducer, Distortion, Tremolo, Phaser) replaced by
BlockRenderer implementations. ~426 tests across 35 files.
See `docs/agent-tasks/audio-pipeline-open-topics.md` for remaining open topics.

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Cylinders` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- `duckCylinder` in `VoiceData` sets the cylinder ID to duck when a voice plays; ducking is cross-cylinder sidechain.
