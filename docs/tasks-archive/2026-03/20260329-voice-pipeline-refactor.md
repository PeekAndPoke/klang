# Signal Pipeline Refactoring

## Context

The audio engine processes signals through two pipeline levels:

- **Strip** (per-voice): Pitch → Excite → Filter → Send
- **Bus** (per-orbit): Delay → Reverb → Phaser → Compressor → Ducking → Master Limiter

Both pipelines use composable functional interfaces (`BlockRenderer` for strip, `BusEffect` for bus)
with shared context objects (`BlockContext`, `BusContext`).

## Full Signal Flow

```
[Strip: Pitch → Excite → Filter → Send] → [Bus: Delay → Reverb → Phaser → Compressor] → Ducking → [Master: Limiter]
```

## Status: COMPLETE

| Phase                                 | Status   | Description                                    |
|---------------------------------------|----------|------------------------------------------------|
| Phase 1: BlockRenderer + BlockContext | **DONE** | Common strip interface and shared context      |
| Phase 2: Extract Filter stage         | **DONE** | 3 renderers + shared pipeline builder          |
| Phase 3: Extract Pitch stage          | **DONE** | 4 renderers + shared pipeline builder          |
| Phase 4: Wrap Excite as BlockRenderer | **DONE** | ExciteRenderer adapter for Exciter             |
| Phase 5: Clean up VoiceImpl           | **DONE** | VoiceImpl ~80 lines, single pipeline runner    |
| Phase 6: Rename SignalGen → Exciter   | **DONE** | Mechanical rename across 32+ files             |
| Phase 7: Strip cleanup                | **DONE** | SendRenderer, remove gateEndFrame from Voice   |
| Phase 8: Bus pipeline                 | **DONE** | BusEffect interface + 5 effect implementations |

## Strip Pipeline (per-voice)

### Interface

```
voices/strip/BlockRenderer.kt  — fun interface BlockRenderer { fun render(ctx: BlockContext) }
voices/strip/BlockContext.kt   — shared context: buffers, timing, exciter, renderContext
voices/strip/EnvelopeCalc.kt   — shared control-rate envelope calculation
```

### Stages

```
voices/strip/pitch/   — VibratoRenderer, AccelerateRenderer, PitchEnvelopeRenderer, FmRenderer
                        PitchPipelineBuilder.kt
voices/strip/excite/  — ExciteRenderer (wraps Exciter)
voices/strip/filter/  — FilterModRenderer, AudioFilterRenderer, EnvelopeRenderer
                        FilterPipelineBuilder.kt
voices/strip/send/    — SendRenderer (pan, gain, orbit routing)
```

### VoiceImpl (~80 lines)

```kotlin
for (renderer in pipeline) {
    renderer.render(blockCtx)
}
```

SendRenderer is appended automatically in VoiceImpl's init block.

## Bus Pipeline (per-orbit)

### Interface

```
orbits/bus/BusEffect.kt  — fun interface BusEffect { fun process(ctx: BusContext) }
orbits/bus/BusContext.kt  — shared context: mixBuffer, delaySendBuffer, reverbSendBuffer, sidechainBuffer
```

### Effects

```
orbits/bus/BusDelayEffect.kt      — send/return, short-circuits when < 10ms
orbits/bus/BusReverbEffect.kt     — send/return, short-circuits when roomSize < 0.01
orbits/bus/BusPhaserEffect.kt     — insert on mixBuffer, short-circuits when depth < 0.01
orbits/bus/BusCompressorEffect.kt — insert, nullable compressor instance
orbits/bus/BusDuckingEffect.kt    — sidechain, resolved by Orbits in separate cross-orbit pass
```

### Orbit

Holds `List<BusEffect>` pipeline (Delay → Reverb → Phaser → Compressor) + `BusContext`.
Ducking is separate because it needs cross-orbit access.

## Remaining Work

### Bus-level configuration (NOT STARTED)

Voice interface currently carries orbit config (delay.time, reverb.roomSize, phaser.*, compressor.*,
ducking.*) that should move to Bus-level configuration. This would decouple voice data from bus
parameters and allow per-orbit effect settings independent of voice scheduling.

## Code Review Fixes Applied

### Review 1 (after Phase 2)

1. Removed unused `hasFreqMod` field from BlockContext
2. Extracted `buildFilterPipeline()` to shared utility
3. Fixed FilterModRenderer release phase bug

### Review 2 (after Phase 4+5)

4. Fixed division-by-zero in AccelerateRenderer
5. Extracted shared envelope calculation to `EnvelopeCalc.kt`
6. Renamed `hasFreqMod` → `freqModBufferWritten`
7. Documented sequential rendering assumption on BlockContext
8. Removed `blockContextFactory` lambda

### Review 3 (after Bus pipeline)

9. Fixed Ducking L/R asymmetry — added `processStereo()` with linked stereo detection (max of L/R),
   `BusDuckingEffect` now uses it. Both channels get identical gain reduction.
10. Removed `require()` from Ducking hot path (was throwing exceptions on audio thread)
11. Ducking instance reuse — mirrors compressor pattern, preserves envelope state across voice updates
12. Ducking cleared when voice has no ducking config (prevents stale sidechain)
13. Phaser feedback clamped to 0.0–0.95 to prevent self-oscillation
14. Phaser params always updated (not just when depth > 0) to avoid stale state
15. Orbit ID mismatch fixed — `getOrInit` now passes `safeId` to Orbit constructor
16. Per-block allocation removed — `Orbits.processAndMix` cleanup no longer calls `keys.toList()`
17. Reverb anti-denormal constant (1e-18) added to comb filter IIR state

## Verification

```bash
./gradlew :audio_be:jvmTest           # all tests pass
./gradlew :audio_be:compileKotlinJs    # JS compilation check
```

Manual: play patterns in browser, verify identical sound before/after each phase.
