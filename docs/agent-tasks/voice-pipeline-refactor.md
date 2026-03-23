# Refactor VoiceImpl into Pitch → Excite → Filter Pipeline

## Context

VoiceImpl currently owns the logic for pitch modulation, FM synthesis, filter envelope modulation,
amplitude envelope, and lazy tremolo/phaser initialization — all inline in `render()`. This makes it
impossible to compose alternative pipelines and hard to add new synthesis techniques.

Goal: Make VoiceImpl a dumb pipeline runner that chains three composable stages:

- **Pitch** — frequency modulation (vibrato, glide, pitch envelope, FM)
- **Excite** — sound generation (oscillator, sample, noise, physical model) — already done as `SignalGen`
- **Filter** — signal sculpting (pre-filters, main filter, amplitude envelope, post-filters, tremolo, phaser)

All three share a common `BlockRenderer` interface so stages can be freely composed and reordered.

## Common Interface: BlockRenderer

```kotlin
fun interface BlockRenderer {
    fun render(ctx: BlockContext)
}
```

`BlockContext` holds everything any stage might need:

```kotlin
class BlockContext(
    // Shared buffers
    val audioBuffer: FloatArray,        // main audio signal (Excite writes, Filter reads/writes)
    val freqModBuffer: DoubleArray,     // pitch multipliers (Pitch writes, Excite reads)
    // Block region
    var offset: Int,
    var length: Int,
    // Voice timing (for envelopes)
    val sampleRate: Int,
    val startFrame: Long,
    val endFrame: Long,
    val gateEndFrame: Long,
    var blockStart: Long,
    // Signal gen context
    val signalCtx: SignalContext,
    // Exciter params
    val freqHz: Double,
    val signal: SignalGen,
    // Orbit routing
    val orbits: Orbits,
    // Scratch buffers
    val scratchBuffers: ScratchBuffers,
)
```

Every stage — whether Pitch, Excite, or Filter — is a `BlockRenderer`. The pipeline is just a
`List<BlockRenderer>` that VoiceImpl iterates. This means you could put a Filter before an Exciter
(for feedback loops), chain multiple Exciters, or insert Pitch modulation between Filter stages.

## Current State

`VoiceImpl.render()` in `audio_be/src/commonMain/kotlin/voices/VoiceImpl.kt`:

```
0.  Lifecycle check (skip if out of range)
0.5 Filter modulation (update cutoffs from envelopes — control rate)
1.  Pitch modulation → phaseMod buffer (fillPitchModulation in common.kt)
2.  FM synthesis → modifies phaseMod buffer (inline in render())
3.  SignalGen.generate() → fills voiceBuffer ← ALREADY EXTRACTED (= Excite)
4.  Pre-filters (crush, coarse) → AudioFilter.process() list
5.  Main filter (LP/HP/BP/Notch) → AudioFilter.process()
6.  Amplitude envelope (ADSR VCA) → applyEnvelope() in VoiceImpl
7.  Post-filters (distortion) → AudioFilter.process() list
7.5 Tremolo → lazy TremoloFilter init + process
7.6 Phaser → lazy PhaserFilter init + process
8.  Mix to orbit → mixToOrbit() in common.kt
```

## Approach: Incremental, One Stage at a Time

### Phase 1: Introduce BlockRenderer + BlockContext

Define the common interface and context. No behavior changes yet.

#### New files:

- `voices/BlockRenderer.kt` — the `fun interface`
- `voices/BlockContext.kt` — shared context for all stages

### Phase 2: Extract Filter stage

**Why first:** Biggest win, cleanest boundary, lowest risk.

Each sub-step becomes a `BlockRenderer`:

- `FilterModRenderer` — updates filter cutoffs from envelopes (control rate, runs once per block)
- `PreFilterRenderer` — wraps pre-filter AudioFilter list (crush, coarse)
- `MainFilterRenderer` — wraps main AudioFilter
- `EnvelopeRenderer` — ADSR VCA (extracted from VoiceImpl.applyEnvelope)
- `PostFilterRenderer` — wraps post-filter AudioFilter list (distortion)
- `TremoloRenderer` — wraps TremoloFilter (built at construction, no lazy init)
- `PhaserRenderer` — wraps PhaserFilter (built at construction, no lazy init)

Each renderer captures its own state at construction time. VoiceScheduler builds only the
renderers that are active (tremolo.depth > 0 → include TremoloRenderer, otherwise skip).

#### Files to modify:

- `VoiceImpl.kt` — remove inline filter/envelope logic, replace with `List<BlockRenderer>`
- `VoiceScheduler.kt` — build filter renderer list in `buildVoice()`
- `Voice.kt` — remove preFilters/postFilters/filter/filterModulators/envelope properties
- `common.kt` — `applyEnvelope` moves into EnvelopeRenderer, `calculateFilterEnvelope` into FilterModRenderer

#### New files:

- `voices/filter/FilterModRenderer.kt`
- `voices/filter/PreFilterRenderer.kt`
- `voices/filter/MainFilterRenderer.kt`
- `voices/filter/EnvelopeRenderer.kt`
- `voices/filter/PostFilterRenderer.kt`
- `voices/filter/TremoloRenderer.kt`
- `voices/filter/PhaserRenderer.kt`

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

#### New files:

- `voices/pitch/VibratoRenderer.kt`
- `voices/pitch/AccelerateRenderer.kt`
- `voices/pitch/PitchEnvelopeRenderer.kt`
- `voices/pitch/FmRenderer.kt`

### Phase 4: Wrap Excite as BlockRenderer

Wrap the existing `SignalGen` in a `BlockRenderer` adapter so it fits the unified pipeline:

```kotlin
class ExciteRenderer(val signal: SignalGen) : BlockRenderer {
    override fun render(ctx: BlockContext) {
        ctx.signalCtx.offset = ctx.offset
        ctx.signalCtx.length = ctx.length
        ctx.signalCtx.phaseMod = /* read from ctx.freqModBuffer if populated */
            signal.generate(ctx.audioBuffer, ctx.freqHz, ctx.signalCtx)
    }
}
```

No SignalGen rename yet — just an adapter. SignalGen → Exciter rename is deferred to Phase 6.

#### New files:

- `voices/excite/ExciteRenderer.kt`

### Phase 5: Clean up VoiceImpl + Voice.kt

After phases 2–4, VoiceImpl.render() becomes:

```kotlin
override fun render(ctx: RenderContext): Boolean {
    // Lifecycle
    if (ctx.blockStart >= endFrame) return false
    if (blockEnd <= startFrame) return true

    // Update block context
    blockCtx.offset = ...
    blockCtx.length = ...
    blockCtx.blockStart = ctx.blockStart

    // Run pipeline
    for (renderer in pipeline) {
        renderer.render(blockCtx)
    }

    // Route to orbit
    mixToOrbit(ctx, blockCtx.offset, blockCtx.length)
    return true
}
```

VoiceImpl constructor takes `pipeline: List<BlockRenderer>` built by VoiceScheduler.

Voice.kt interface shrinks to:

- Lifecycle: startFrame, endFrame, gateEndFrame, orbitId, cut
- Dynamics: gain, pan, postGain, gainMultiplier, setGainMultiplier()
- Routing: delay, reverb (orbit-level effects)
- Rendering: render(ctx)

### Phase 6: Rename SignalGen → Exciter (deferred, separate PR)

Mechanical rename across all modules:

- `SignalGen` → `Exciter`
- `SignalGenDsl` → `ExciterDsl`
- `SignalGenRegistry` → `ExciterRegistry`
- `SignalGenDefaults` → `ExciterDefaults`
- `SignalGenDslRuntime` → `ExciterDslRuntime`
- `SignalGens` → `Exciters`
- `SignalContext` → `ExciteContext`
- File names follow class names

This is a big rename but purely mechanical — no logic changes. Separate PR to keep reviews clean.

## Verification

After each phase:

```bash
./gradlew :audio_be:jvmTest           # all voice/filter/signalgen tests must pass
./gradlew :audio_be:compileKotlinJs    # JS compilation check
./gradlew :sprudel:jvmTest             # sprudel tests still pass
```

Manual: play patterns in browser, verify identical sound before/after each phase.

## Risks

- **Performance regression:** More function calls (renderer.render per stage). Mitigate: benchmark
  before/after. The renderers are just thin wrappers around the same math — should be negligible
  compared to the actual DSP work (sin, polyBlep, filter math).
- **PitchRenderer chaining:** The current phaseMod buffer is shared and accumulated across vibrato/
  accelerate/FM. Need to get the accumulation semantics right — each PitchRenderer must read the
  existing buffer state and modify it, not overwrite.
- **BlockContext size:** Carries a lot of fields. But it's created once per voice (not per block)
  and mutated in place — no allocation overhead in the hot path.
