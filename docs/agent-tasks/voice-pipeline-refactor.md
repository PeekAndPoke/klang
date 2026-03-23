# Refactor VoiceImpl into Pitch → Excite → Filter Pipeline

## Context

VoiceImpl currently owns the logic for pitch modulation and FM synthesis inline in `render()`.
The Filter stage and Excite stage have been extracted into composable `BlockRenderer` pipelines.

Goal: Make VoiceImpl a dumb pipeline runner that chains three composable stages:

- **Pitch** — frequency modulation (vibrato, glide, pitch envelope, FM)
- **Excite** — sound generation (oscillator, sample, noise, physical model) — already done as `SignalGen`
- **Filter** — signal sculpting (pre-filters, main filter, amplitude envelope, post-filters, tremolo, phaser)

All three share a common `BlockRenderer` interface so stages can be freely composed and reordered.

## Progress

| Phase                                  | Status                       | Description                             |
|----------------------------------------|------------------------------|-----------------------------------------|
| Phase 1: BlockRenderer + BlockContext  | **DONE**                     | Common interface and shared context     |
| Phase 2: Extract Filter stage          | **DONE**                     | 3 renderers + shared pipeline builder   |
| Phase 3: Extract Pitch stage           | **DONE**                     | 4 renderers + shared pipeline builder   |
| Phase 4: Wrap Excite as BlockRenderer  | **DONE**                     | ExciteRenderer adapter for SignalGen    |
| Phase 5: Clean up VoiceImpl + Voice.kt | **DONE**                     | VoiceImpl is ~85 lines, single pipeline |
| Phase 6: Rename SignalGen → Exciter    | TODO (deferred, separate PR) | Mechanical rename                       |

## What's Been Implemented

### Common Interface

```
voices/BlockRenderer.kt    — fun interface BlockRenderer { fun render(ctx: BlockContext) }
voices/BlockContext.kt     — shared context: buffers, timing, signal gen, routing
```

### Shared Utilities

```
voices/EnvelopeCalc.kt  — calculateControlRateEnvelope() + envelopeLevelAtPosition()
                          shared by FmRenderer and FilterModRenderer
```

### Pitch Pipeline (Phase 3)

```
voices/pitch/PitchPipelineBuilder.kt   — buildPitchPipeline() top-level function
voices/pitch/VibratoRenderer.kt        — LFO pitch modulation
voices/pitch/AccelerateRenderer.kt     — pitch glide over voice lifetime
voices/pitch/PitchEnvelopeRenderer.kt  — attack/decay pitch transient
voices/pitch/FmRenderer.kt             — FM synthesis with envelope-controlled depth
```

### Excite (Phase 4)

```
voices/excite/ExciteRenderer.kt  — wraps SignalGen as BlockRenderer
```

### Filter Pipeline (Phase 2)

```
voices/filter/FilterPipelineBuilder.kt  — buildFilterPipeline() top-level function
voices/filter/FilterModRenderer.kt      — filter cutoff envelope modulation (control rate)
voices/filter/AudioFilterRenderer.kt    — wraps AudioFilter list(s) as BlockRenderer
voices/filter/EnvelopeRenderer.kt       — ADSR VCA (amplitude envelope)
```

Pipeline order built by `buildFilterPipeline()`:

1. **FilterModRenderer** — updates filter cutoffs from envelopes (control rate, once per block)
2. **AudioFilterRenderer** (pre-filters) — bit crush, sample rate reduction (only if active)
3. **AudioFilterRenderer** (main filter) — LP/HP/BP/Notch (baked/combined)
4. **EnvelopeRenderer** — ADSR VCA
5. **AudioFilterRenderer** (post-filters) — distortion (only if active)
6. **AudioFilterRenderer** (tremolo) — amplitude LFO (only if active)
7. **AudioFilterRenderer** (phaser) — allpass sweep (only if active)

### Current VoiceImpl.render() flow

```kotlin
// Update per-block state
blockCtx.audioBuffer = ctx.voiceBuffer
blockCtx.offset = offset
blockCtx.length = length
blockCtx.blockStart = ctx.blockStart
blockCtx.freqModBufferWritten = false

// Pitch → Excite → Filter (single composable pipeline)
for (renderer in pipeline) {
  renderer.render(blockCtx)
}

// Route
mixToOrbit(ctx, offset, length)
```

## Remaining Work

### Phase 6: Rename SignalGen → Exciter (deferred, separate PR)

Mechanical rename across all modules. No logic changes.

### Future: Package reorganization

When renaming, also reorganize into clean packages:

- `voices/strip/` — VoiceImpl (→ Strip), BlockRenderer, BlockContext
- `voices/strip/pitch/` — pitch renderers
- `voices/strip/excite/` — ExciteRenderer
- `voices/strip/filter/` — filter renderers
- `orbits/` — orbit/bus pipeline (already exists)

## Code Review Fixes Applied

### Review 1 (after Phase 2)
1. Removed unused `hasFreqMod` field from BlockContext
2. Extracted `buildFilterPipeline()` to shared utility — eliminates duplication between VoiceScheduler and
   VoiceTestHelpers
3. Fixed FilterModRenderer release phase bug — now calculates actual envelope level at gate end instead of assuming
   sustainLevel

### Review 2 (after Phase 4+5)

4. Fixed division-by-zero in AccelerateRenderer when `endFrame == startFrame` — guarded in `buildPitchPipeline()`
5. Extracted shared envelope calculation to `EnvelopeCalc.kt` (`calculateControlRateEnvelope` +
   `envelopeLevelAtPosition`)
   — eliminates 40+ lines of duplication between FmRenderer and FilterModRenderer
6. Renamed `hasFreqMod` → `freqModBufferWritten` — clearer intent (per-block accumulator flag, not config property)
7. Documented sequential rendering assumption on BlockContext
8. Removed `blockContextFactory` lambda — BlockContext is now pre-built by VoiceScheduler at voice construction time

## Verification

After each phase:
```bash
./gradlew :audio_be:jvmTest           # all voice/filter/signalgen tests must pass
./gradlew :audio_be:compileKotlinJs    # JS compilation check
```

Manual: play patterns in browser, verify identical sound before/after each phase.
