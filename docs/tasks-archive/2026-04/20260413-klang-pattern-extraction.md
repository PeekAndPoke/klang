# Extract KlangPattern / KlangPlaybackController from Strudel

> **Status: RESOLVED** (archived 2026-04-13).
> `KlangPlaybackController`, `ContinuousPlayback`, and `OneShotPlayback` live in the `klang`
> module. `SourceLocation` / `SourceLocationChain` moved to `common/`. The strudel module has
> since been renamed to sprudel; the remaining pattern-specific classes live in sprudel and
> compose through the generic controller. Remaining follow-ups (if any) are captured in
> their own topic-specific docs.

## Problem

All playback scheduling logic currently lives inside the `strudel` module (`StrudelPlaybackController`,
`StrudelPlayback`,
`ContinuousStrudelPlayback`, `OneShotStrudelPlayback`). This makes it impossible to add new pattern sources (sequencer,
MIDI, generative, etc.) without either duplicating the entire scheduling engine or depending on the strudel module.

The goal is **composable patterns**: a `StrudelPattern` stacked with a `SequencerPattern` should just work, with both
going through the same scheduling and playback infrastructure.

Additionally, `SourceLocation` and `SourceLocationChain` currently live in `klangscript` but are a general concept (
where
in source code something came from). They should move to `common` so that `audio_bridge` and any pattern module can use
them with proper typing — eliminating the `sourceLocations: Any?` hack in `KlangPlaybackSignal.VoiceEvent`.

## Current Architecture

```
strudel module
├── StrudelPattern (interface)                    — 30+ pattern implementations
├── StrudelPatternEvent (data class)              — Rational TimeSpan, StrudelVoiceData
├── StrudelVoiceData (data class)                 — flat fields, converts to VoiceData
├── StrudelPlayback (interface)                   — extends KlangPlayback
│   ├── ContinuousStrudelPlayback (internal)      — thin wrapper around controller
│   └── OneShotStrudelPlayback (internal)         — cycle-limited wrapper
├── StrudelPlaybackController (internal)          — core scheduling engine (498 lines)
└── index_common.kt                               — playStrudel(), playStrudelOnce()

klang module (audio_engine)
├── KlangPlayback (interface)                     — playbackId, signals, start/stop
├── KlangPlaybackContext (data class)             — bundles dependencies for playbacks
├── KlangPlayer                                   — manages playbacks, routes feedback
└── SamplePreloader                               — shared sample cache

audio_bridge module
├── KlangPlaybackSignal (sealed class)            — VoicesScheduled, CycleCompleted, etc.
├── ScheduledVoice (data class)                   — voice sent to audio backend
├── VoiceData (data class)                        — engine-level voice representation
├── KlangCommLink                                 — FE ↔ BE communication protocol
├── KlangTime                                     — clock abstraction
└── SampleRequest                                 — sample loading request

common module
├── infra/                                        — KlangAtomicBool, KlangLock, etc.
└── math/                                         — Rational, Bjorklund, etc.

klangscript module
└── ast/
    ├── Ast.kt                                    — contains SourceLocation, SourceLocationAware
    └── SourceLocationChain.kt                    — chain of source locations
```

**Dependency chain**: `common` ← `audio_bridge` ← `klang` ← `strudel`

### The `sourceLocations: Any?` problem

`KlangPlaybackSignal.VoicesScheduled.VoiceEvent.sourceLocations` is typed `Any?` because `audio_bridge` cannot depend on
`klangscript`. The comment says "module-specific type, e.g. SourceLocationChain". This forces unsafe casts in UI code.
Moving `SourceLocation`/`SourceLocationChain` to `common` (which `audio_bridge` already depends on) fixes this.

### What is strudel-specific in StrudelPlaybackController?

Only `queryEvents()` (lines 319-384) touches strudel types:

1. Creates `StrudelPattern.QueryContext` with `cpsKey`
2. Calls `pattern.queryArcContextual()` → `List<StrudelPatternEvent>`
3. Filters by `isOnset`
4. Reads `event.part`/`whole` (Rational `TimeSpan`)
5. Calls `event.data.toVoiceData()` (`StrudelVoiceData` → `VoiceData`)
6. Reads `event.sourceLocations` (`SourceLocationChain?`)

Everything else — cycle tracking, lookahead scheduling, sample preloading, latency compensation, resync logic, coroutine
lifecycle, signal emission — is generic.

### External consumers of strudel types

| File                     | Uses                                             |
|--------------------------|--------------------------------------------------|
| `CodeSongPage.kt`        | `StrudelPattern`, `StrudelPlayback`              |
| `PlayableCodeExample.kt` | `StrudelPlayback`, `playStrudel`                 |
| `StartPage.kt`           | `StrudelPlayback`, `playStrudelOnce`             |
| `Main.kt` (JVM)          | `strudelPlayer`, `StrudelPattern`, `playStrudel` |

---

## Design

### Prerequisite: Move `SourceLocation` to `common`

Move these types from `klangscript/ast/` to `common/`:

| Type                  | From                                         | To                                         |
|-----------------------|----------------------------------------------|--------------------------------------------|
| `SourceLocation`      | `klangscript/.../ast/Ast.kt` (lines 28-56)   | `common/.../SourceLocation.kt`             |
| `SourceLocationAware` | `klangscript/.../ast/Ast.kt` (lines 11-17)   | `common/.../SourceLocation.kt` (same file) |
| `SourceLocationChain` | `klangscript/.../ast/SourceLocationChain.kt` | `common/.../SourceLocationChain.kt`        |

New package: `io.peekandpoke.klang.common` (root of common module, not a subdirectory).

**Import update**: 29 files across klangscript, strudel, klangblocks, klangui, and the main app change from
`io.peekandpoke.klang.script.ast.SourceLocation` to `io.peekandpoke.klang.common.SourceLocation` (and same for
`SourceLocationChain`, `SourceLocationAware`).

`klangscript/ast/Ast.kt` keeps everything else (AST node types) — only the 3 source-location types move out.

### New types in `audio_bridge`

#### `KlangPatternEvent` (interface)

Minimal contract for an event that the scheduling engine can consume:

```kotlin
// audio_bridge/src/commonMain/kotlin/KlangPatternEvent.kt
package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.SourceLocationChain

interface KlangPatternEvent {
    /** Event start time in cycles (from pattern start) */
    val startCycles: Double

    /** Event duration in cycles */
    val durationCycles: Double

    /** Source locations for code highlighting */
    val sourceLocations: SourceLocationChain?

    /** Convert to engine-level voice data */
    fun toVoiceData(): VoiceData
}
```

#### Fix `KlangPlaybackSignal.VoicesScheduled.VoiceEvent`

Change `sourceLocations: Any?` → `sourceLocations: SourceLocationChain?` now that common provides the type.

#### `KlangPattern` (interface)

Universal pattern contract. Every pattern source implements this:

```kotlin
// audio_bridge/src/commonMain/kotlin/KlangPattern.kt
package io.peekandpoke.klang.audio_bridge

interface KlangPattern {
    /**
     * Query events in the given cycle range.
     *
     * Contract:
     * - Returns only events that should be played (onset filtering is the implementor's responsibility)
     * - Events outside [fromCycles, toCycles) may be excluded
     * - [cps] is provided for implementations that need tempo context
     */
    fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent>
}
```

### Moved to `klang` module (audio_engine)

#### `KlangPlaybackController` (internal)

The existing `StrudelPlaybackController` moves here with one change: it takes `KlangPattern` instead of
`StrudelPattern`. The `queryEvents()` method simplifies to:

```kotlin
// Before (strudel-specific):
private fun queryEvents(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
    val fromRational = Rational(from)
    val toRational = Rational(to)
    val ctx = QueryContext { set(QueryContext.cpsKey, cyclesPerSecond) }
    val events = pattern.queryArcContextual(from = fromRational, to = toRational, ctx = ctx)
        .filter { it.isOnset }
        .sortedBy { it.part.begin }
    // ... convert StrudelVoiceData → VoiceData, build ScheduledVoice ...
}

// After (generic):
private fun queryEvents(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
    val events = pattern.queryEvents(fromCycles = from, toCycles = to, cps = cyclesPerSecond)
    // ... use event.toVoiceData(), event.startCycles, event.durationCycles directly ...
}
```

No more `Rational`, `QueryContext`, `isOnset`, or `StrudelVoiceData` in this class.

#### `KlangCyclicPlayback` (interface, extends `KlangPlayback`)

```kotlin
interface KlangCyclicPlayback : KlangPlayback {
    fun updatePattern(pattern: KlangPattern)
    fun updateCyclesPerSecond(cps: Double)
    fun reemitVoiceSignals()
    fun start(options: Options = Options())

    data class Options(
        val lookaheadCycles: Double = 2.0,
        val cyclesPerSecond: Double = 0.5,
        val prefetchCycles: Int? = null,
    )
}
```

#### `ContinuousPlayback` / `OneShotPlayback` (internal)

Moved from strudel, now work with `KlangPattern`. Structurally identical to current implementations,
just with `KlangPattern` replacing `StrudelPattern`.

`OneShotPlayback` needs a generic cycle-limiting mechanism to replace `pattern.filterWhen { it < cyclesToPlay }` (which
is a strudel DSL method). Options:

- A `KlangPattern` wrapper in `klang` module that truncates events beyond N cycles.
- Since `queryEvents` already returns events in a range, the controller can simply stop querying after N cycles and
  `OneShotPlayback` auto-stops on `CycleCompleted` (which it already does). The `filterWhen` call may be unnecessary.

### Moved to `klang` module: playback factory functions

The strudel `index_common.kt` functions move up to `klang` as generic `KlangPattern` operations:

```kotlin
// klang/src/commonMain/kotlin/index_common.kt (existing file, add these)

/**
 * Start continuous playback that runs indefinitely until stopped.
 */
fun KlangPlayer.play(pattern: KlangPattern): KlangCyclicPlayback {
    lateinit var playback: KlangCyclicPlayback
    playback = ContinuousPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        onStarted = { registerPlayback(playback) },
        onStopped = { unregisterPlayback(playback) },
    )
    return playback
}

/**
 * Start one-shot playback that stops automatically after N cycles.
 */
fun KlangPlayer.playOnce(pattern: KlangPattern, cycles: Int = 1): KlangCyclicPlayback {
    lateinit var playback: KlangCyclicPlayback
    playback = OneShotPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        cyclesToPlay = cycles,
        onStarted = { registerPlayback(playback) },
        onStopped = { unregisterPlayback(playback) },
    )
    return playback
}
```

**Deleted from strudel**:

- `strudelPlayer()` — just wraps `klangPlayer()`, adds nothing
- `playStrudel()` — replaced by `KlangPlayer.play(KlangPattern)`
- `playStrudelOnce()` — replaced by `KlangPlayer.playOnce(KlangPattern)`
- `strudel/src/commonMain/kotlin/index_common.kt` — entire file deleted

Since `StrudelPattern` implements `KlangPattern`, callers pass strudel patterns directly:

```kotlin
// Before:
val playback: StrudelPlayback = player.playStrudel(pattern)

// After:
val playback: KlangCyclicPlayback = player.play(pattern)
```

UI consumers (`CodeSongPage`, `PlayableCodeExample`, `StartPage`, `Main.kt`) update their import and variable type.
The API they use (`updatePattern`, `updateCyclesPerSecond`, `reemitVoiceSignals`, `signals`, `start`, `stop`) is
all on `KlangCyclicPlayback`.

### Changes in `strudel` module

#### `StrudelPattern` implements `KlangPattern`

```kotlin
interface StrudelPattern : KlangPattern {
    // Existing strudel-internal API (unchanged):
    val weight: Double
    val numSteps: Rational?
    fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent>
    fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent>

    // KlangPattern implementation (default method):
    override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
        val ctx = QueryContext { set(QueryContext.cpsKey, cps) }
        return queryArcContextual(Rational(fromCycles), Rational(toCycles), ctx)
            .filter { it.isOnset }
            .sortedBy { it.part.begin }
    }
}
```

#### `StrudelPatternEvent` implements `KlangPatternEvent`

```kotlin
data class StrudelPatternEvent(
    val part: TimeSpan,
    val whole: TimeSpan,
    val data: StrudelVoiceData,
    override val sourceLocations: SourceLocationChain?,
) : KlangPatternEvent {
    override val startCycles: Double get() = whole.begin.toDouble()
    override val durationCycles: Double get() = whole.duration.toDouble()
    override fun toVoiceData(): VoiceData = data.toVoiceData()

    // Existing strudel-internal properties:
    val isOnset: Boolean = ...
}
```

#### `StrudelPlayback` — deleted

No longer needed. `KlangCyclicPlayback` provides the full API. UI code uses `KlangCyclicPlayback` directly.
`updatePattern(pattern: KlangPattern)` accepts `StrudelPattern` because `StrudelPattern : KlangPattern`.

---

## Implementation Steps

### Phase 0: Move SourceLocation types to `common`

1. Create `common/src/commonMain/kotlin/SourceLocation.kt` with `SourceLocation` data class and `SourceLocationAware`
   interface (extracted from `klangscript/ast/Ast.kt` lines 11-56)
2. Move `klangscript/ast/SourceLocationChain.kt` → `common/src/commonMain/kotlin/SourceLocationChain.kt`
3. Change package from `io.peekandpoke.klang.script.ast` to `io.peekandpoke.klang.common` in both files
4. Remove the moved types from `klangscript/ast/Ast.kt` (keep remaining AST types), add import of new location
5. Update imports in all 29 affected files across klangscript, strudel, klangblocks, klangui, main app
6. Fix `KlangPlaybackSignal.VoicesScheduled.VoiceEvent.sourceLocations` from `Any?` to `SourceLocationChain?`
7. Remove any `as SourceLocationChain` unsafe casts in UI code that are now unnecessary
8. Verify: `./gradlew jvmTest` (all modules, since imports change everywhere)

### Phase 1: Introduce interfaces in `audio_bridge`

1. Create `KlangPatternEvent` interface in `audio_bridge` (with properly typed `sourceLocations: SourceLocationChain?`)
2. Create `KlangPattern` interface in `audio_bridge`
3. No existing code changes yet — purely additive

### Phase 2: Make strudel types implement the new interfaces

1. `StrudelPatternEvent` implements `KlangPatternEvent`
2. `StrudelPattern` implements `KlangPattern` (default method for `queryEvents`)
3. All 30+ pattern subclasses automatically inherit the `KlangPattern` implementation
4. Verify: `./gradlew :strudel:jvmTest`

### Phase 3: Extract controller to `klang` module

1. Copy `StrudelPlaybackController` → `KlangPlaybackController` in `klang` module
2. Replace `StrudelPattern` param with `KlangPattern`
3. Replace strudel-specific `queryEvents()` with generic version using `KlangPattern.queryEvents()`
4. Remove imports of `Rational`, `QueryContext`, `StrudelPatternEvent`, `StrudelVoiceData`
5. Verify it compiles

### Phase 4: Extract playback types to `klang` module

1. Create `KlangCyclicPlayback` interface in `klang` module
2. Move `ContinuousStrudelPlayback` → `ContinuousPlayback` in `klang`, using `KlangPattern`
3. Move `OneShotStrudelPlayback` → `OneShotPlayback` in `klang`, replacing `filterWhen` with cycle-range limiting
4. Create `Options` data class on `KlangCyclicPlayback`

### Phase 5: Clean up strudel module

1. Delete `StrudelPlayback.kt`, `ContinuousStrudelPlayback.kt`, `OneShotStrudelPlayback.kt`,
   `StrudelPlaybackController.kt`
2. Delete `strudel/index_common.kt` (`strudelPlayer`, `playStrudel`, `playStrudelOnce`)
3. Add `KlangPlayer.play()` and `KlangPlayer.playOnce()` to `klang/index_common.kt`
4. Verify: `./gradlew :strudel:jvmTest`

### Phase 6: Update UI consumers

1. `CodeSongPage.kt` — replace `StrudelPlayback` → `KlangCyclicPlayback`, `playStrudel` → `play`
2. `PlayableCodeExample.kt` — same
3. `StartPage.kt` — replace `playStrudelOnce` → `playOnce`
4. `Main.kt` (JVM) — replace `strudelPlayer` → `klangPlayer`, `playStrudel` → `play`
5. Verify: `./gradlew jsBrowserDevelopmentWebpack` and `./gradlew jvmTest`

---

## Risk Assessment

### Low risk

- **Phase 0 is mechanical**: moving files + updating imports. IDE-level refactor, no logic changes.
- **Phase 1-2 are purely additive**: adding interfaces and default implementations, no behavior change.
- **StrudelPlayback public API is unchanged**: UI consumers won't need changes.
- **30+ pattern subclasses don't change**: they inherit `KlangPattern` through `StrudelPattern`.

### Medium risk

- **`OneShotPlayback` cycle limiting**: the current `filterWhen` is strudel-specific. The generic replacement must
  produce identical behavior (no events beyond N cycles). Mitigation: the controller already only queries up to
  `queryCursorCycles` which advances in 1-cycle chunks, and `OneShotPlayback` auto-stops on `CycleCompleted`. Verify
  that removing `filterWhen` doesn't cause tail events to leak past the cycle boundary.

- **`queryEvents` sorting**: the current controller sorts by `it.part.begin` (Rational). The generic version relies on
  `KlangPattern.queryEvents` returning events in a usable order. The default `StrudelPattern.queryEvents` implementation
  must preserve the `sortedBy { it.part.begin }` behavior. This should be part of the `KlangPattern` contract
  (events returned in start-time order) or the controller should sort by `startCycles`.

### Things to watch

- **`SampleRequest` construction**: the current controller calls `event.data.asSampleRequest()` on `StrudelVoiceData`
  during sample lookahead. After extraction, this needs to work through `VoiceData` instead. Check that
  `VoiceData.asSampleRequest()` exists or can be added to `audio_bridge`.

- **Performance**: the generic `queryEvents` default on `StrudelPattern` creates a `QueryContext` per call. This matches
  current behavior (controller already creates one per query), so no regression.

- **`reemitVoiceSignals()`**: currently queries pattern and fires signals. Works unchanged since the controller calls
  `pattern.queryEvents()` which is now generic.

---

## Verification Plan

| Step       | Command                                            | Validates                                         |
|------------|----------------------------------------------------|---------------------------------------------------|
| After Ph 0 | `./gradlew jvmTest`                                | All modules compile with new import paths         |
| After Ph 0 | `./gradlew jsBrowserDevelopmentWebpack`            | JS build works with moved types                   |
| After Ph 2 | `./gradlew :strudel:jvmTest`                       | Strudel patterns still work with new interface    |
| After Ph 4 | `./gradlew :klang:compileKotlinJvm`                | New controller/playback types compile             |
| After Ph 5 | `./gradlew :strudel:jvmTest`                       | Strudel rewiring doesn't break tests              |
| After Ph 6 | `./gradlew jsBrowserDevelopmentWebpack`            | Full JS build succeeds                            |
| After Ph 6 | `./gradlew jvmTest`                                | All JVM tests pass                                |
| Manual     | Play a strudel pattern in browser                  | End-to-end: compile → play → highlight works      |
| Manual     | One-shot playback stops after N cycles             | OneShotPlayback behavior preserved                |
| Manual     | Check code highlighting uses typed sourceLocations | No more `as SourceLocationChain` casts in UI code |

---

## File Manifest

### New files

| File                                                      | Module       | Description                                  |
|-----------------------------------------------------------|--------------|----------------------------------------------|
| `common/src/commonMain/kotlin/SourceLocation.kt`          | common       | SourceLocation + SourceLocationAware         |
| `common/src/commonMain/kotlin/SourceLocationChain.kt`     | common       | SourceLocationChain (moved from klangscript) |
| `audio_bridge/src/commonMain/kotlin/KlangPatternEvent.kt` | audio_bridge | Event interface                              |
| `audio_bridge/src/commonMain/kotlin/KlangPattern.kt`      | audio_bridge | Pattern interface                            |
| `klang/src/commonMain/kotlin/KlangCyclicPlayback.kt`      | klang        | Cyclic playback interface                    |
| `klang/src/commonMain/kotlin/KlangPlaybackController.kt`  | klang        | Generic scheduling controller                |
| `klang/src/commonMain/kotlin/ContinuousPlayback.kt`       | klang        | Continuous playback (from strudel)           |
| `klang/src/commonMain/kotlin/OneShotPlayback.kt`          | klang        | One-shot playback (from strudel)             |

### Modified files

| File                                                        | Module       | Change                                                |
|-------------------------------------------------------------|--------------|-------------------------------------------------------|
| `klangscript/src/commonMain/kotlin/ast/Ast.kt`              | klangscript  | Remove SourceLocation/SourceLocationAware, add import |
| `audio_bridge/src/commonMain/kotlin/KlangPlaybackSignal.kt` | audio_bridge | `sourceLocations: Any?` → `SourceLocationChain?`      |
| `strudel/src/commonMain/kotlin/StrudelPattern.kt`           | strudel      | `: KlangPattern`, default `queryEvents` impl          |
| `strudel/src/commonMain/kotlin/StrudelPatternEvent.kt`      | strudel      | `: KlangPatternEvent`, implement interface properties |
| `klang/src/commonMain/kotlin/index_common.kt`               | klang        | Add `KlangPlayer.play()`, `KlangPlayer.playOnce()`    |
| 29 files across all modules                                 | various      | Update SourceLocation imports (Phase 0)               |

### Deleted files

| File                                                           | Module      | Reason                                     |
|----------------------------------------------------------------|-------------|--------------------------------------------|
| `klangscript/src/commonMain/kotlin/ast/SourceLocationChain.kt` | klangscript | Moved to `common` module                   |
| `strudel/src/commonMain/kotlin/StrudelPlaybackController.kt`   | strudel     | Replaced by `KlangPlaybackController`      |
| `strudel/src/commonMain/kotlin/ContinuousStrudelPlayback.kt`   | strudel     | Replaced by `ContinuousPlayback`           |
| `strudel/src/commonMain/kotlin/OneShotStrudelPlayback.kt`      | strudel     | Replaced by `OneShotPlayback`              |
| `strudel/src/commonMain/kotlin/StrudelPlayback.kt`             | strudel     | Replaced by `KlangCyclicPlayback`          |
| `strudel/src/commonMain/kotlin/index_common.kt`                | strudel     | Functions moved to `klang/index_common.kt` |
