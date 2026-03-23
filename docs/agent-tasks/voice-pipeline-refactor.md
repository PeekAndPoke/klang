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
| Phase 3: Extract Pitch stage           | TODO                         | Vibrato, accelerate, pitch envelope, FM |
| Phase 4: Wrap Excite as BlockRenderer  | TODO                         | Adapter for SignalGen                   |
| Phase 5: Clean up VoiceImpl + Voice.kt | TODO                         | VoiceImpl becomes ~30 lines             |
| Phase 6: Rename SignalGen → Exciter    | TODO (deferred, separate PR) | Mechanical rename                       |

## What's Been Implemented

### Common Interface

```
voices/BlockRenderer.kt    — fun interface BlockRenderer { fun render(ctx: BlockContext) }
voices/BlockContext.kt     — shared context: buffers, timing, signal gen, routing
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
// Pitch (still inline — Phase 3 will extract)
var modBuffer = fillPitchModulation(ctx, offset, length)
// ... FM synthesis inline ...

// Excite
signal.generate(ctx.voiceBuffer, freqHz, signalCtx)

// Filter (composable BlockRenderer pipeline)
for (renderer in filterPipeline) {
    renderer.render(bCtx)
}

// Route
mixToOrbit(ctx, offset, length)
```

## Remaining Work

### Phase 3: Extract Pitch stage

Each sub-step becomes a `BlockRenderer` that writes to `ctx.freqModBuffer`:
- `VibratoRenderer` — LFO pitch modulation
- `AccelerateRenderer` — pitch glide over voice lifetime
- `PitchEnvelopeRenderer` — attack/decay/release on pitch
- `FmRenderer` — FM synthesis (multiplies into the freqMod buffer)

#### Files to modify:
- `VoiceImpl.kt` — remove pitch mod/FM logic, replace with `List<BlockRenderer>`
- `VoiceScheduler.kt` — build pitch renderer list in `buildVoice()`
- `Voice.kt` — remove fm/accelerate/vibrato/pitchEnvelope properties
- `common.kt` — `fillPitchModulation` logic moves into pitch renderers

### Phase 4: Wrap Excite as BlockRenderer

Wrap `SignalGen` in an `ExciteRenderer` adapter:

```kotlin
class ExciteRenderer(val signal: SignalGen) : BlockRenderer {
    override fun render(ctx: BlockContext) {
        ctx.signalCtx.phaseMod = if (ctx.hasFreqMod) ctx.freqModBuffer else null
        signal.generate(ctx.audioBuffer, ctx.freqHz, ctx.signalCtx)
    }
}
```

### Phase 5: Clean up VoiceImpl + Voice.kt

VoiceImpl.render() becomes:
```kotlin
for (renderer in pipeline) { renderer.render(blockCtx) }
mixToOrbit(ctx, blockCtx.offset, blockCtx.length)
```

Voice.kt interface shrinks to: lifecycle, dynamics, routing, render.

### Phase 6: Rename SignalGen → Exciter (deferred, separate PR)

Mechanical rename across all modules. No logic changes.

## Code Review Fixes Applied

1. Removed unused `hasFreqMod` field from BlockContext
2. Extracted `buildFilterPipeline()` to shared utility — eliminates duplication between VoiceScheduler and
   VoiceTestHelpers
3. Fixed FilterModRenderer release phase bug — now calculates actual envelope level at gate end instead of assuming
   sustainLevel

## Verification

After each phase:
```bash
./gradlew :audio_be:jvmTest           # all voice/filter/signalgen tests must pass
./gradlew :audio_be:compileKotlinJs    # JS compilation check
```

Manual: play patterns in browser, verify identical sound before/after each phase.
