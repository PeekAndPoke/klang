Below is a concise **implementation plan** you can hand to a coding agent. It focuses on the new **cycle-complete signal
**, emitted **stall‑safe** (no skipped cycles), and sets up the **one‑shot wrapper** that stops after one cycle.

---

# ✅ Implementation Plan: Cycle Completion Signal + One‑Shot Playback

## Goals

1. Emit a **CycleCompleted** signal after each full cycle.
2. Ensure **no cycles are skipped**, even if CPU stalls.
3. Use this signal to implement **one‑shot** Strudel playback (stop after 1 cycle).

---

## 1) Add new signal type

**File:** `src/commonMain/kotlin/io/peekandpoke/klang/audio_engine/KlangPlaybackSignal.kt`

**Action:**

- Add a new signal class:
    - `CycleCompleted(cycleIndex: Long, atTimeSec: Double)`
- Semantics:
    - `cycleIndex = 0` means cycle `[0,1)` is completed.
    - `atTimeSec` is the wall‑clock boundary time (seconds since KlangTime epoch), optionally latency‑offset for UI
      sync.

---

## 2) Track last emitted cycle in StrudelPlayback

**File:** `src/commonMain/kotlin/io/peekandpoke/klang/strudel/StrudelPlayback.kt`

**Action:**

- Add state: `private var lastEmittedCycle = -1L`
- Reset it when playback starts (`run` entry).

---

## 3) Emit completed cycles (stall‑safe)

**File:** `StrudelPlayback.kt`

**Action:**

- Add a helper `emitCompletedCycles()` and call it in the main loop (after `updateLocalFrameCounter()`).

**Logic:**

- Compute elapsed seconds since playback start.
- Compute the **last fully completed cycle**:
    - `completedCycle = floor(elapsedSec / secPerCycle) - 1`
- If `completedCycle > lastEmittedCycle`, emit **all missing cycles**:
    - for `c in (lastEmittedCycle+1 .. completedCycle)` emit `CycleCompleted(c, boundaryTimeSec)`
- Update `lastEmittedCycle`.

**Boundary time:**

- `boundaryTimeSec = playbackStartTimeSec + ((c + 1) * secPerCycle) + latencyOffsetSec`
- Use latencyOffsetSec if you want UI sync consistent with existing highlights.

**Concurrency:**

- Emit via `callbackDispatcher` to keep scheduler path clean.
- Emit in a single coroutine loop so order is preserved.

---

## 4) Implement one‑shot helper (stop after cycle 0)

**Location suggestion:** same place as `playStrudel(...)` helper (common/index utilities)

**Action:**

- Add `playStrudelOnce(pattern, cycles = 1)` or similar.
- Start playback normally.
- Subscribe to `CycleCompleted`:
    - when `cycleIndex == cycles-1`, call `stop()` and clear the listener if needed.

**Notes:**

- This cleanly reuses playback infrastructure.
- Works for any pattern, not just samples.

---

## 5) (Optional) Use in SamplesLibraryPage

When sample preview is needed, call the one‑shot helper with:

- `cycles = 1`
- pattern that triggers the specific sample.

---

## Acceptance Criteria

- CycleCompleted fires **every cycle**, never skipped even under artificial stalls.
- One‑shot playback stops reliably after exactly 1 cycle.
- Existing playback behavior is unaffected.

---

If you want, I can also draft the exact helper signature for the one‑shot API.

Below is a **revised implementation plan** (no checkmarks) plus an assessment of the controller split.

---

# Implementation Plan (Controller + Two Playback Types)

## Decision Summary

Extracting the core scheduling logic into a `StrudelPlaybackController` and then implementing:

- `ContinuousStrudelPlayback`
- `OneShotStrudelPlayback`

**does make sense** if you expect more playback variants (preview, fixed length, bouncing, auditioning, etc.).  
It’s not over‑engineering if it eliminates duplicated scheduling logic and keeps playback modes lightweight.

If this is the only alternative mode you’ll ever need, a thinner “one‑shot wrapper” might be simpler.  
Given your long‑term direction (“infrastructure we anyway need”), the controller split is reasonable.

---

## 1) Introduce `StrudelPlaybackController`

**Purpose**
Encapsulate all shared Strudel scheduling logic:

- preload samples
- event querying
- lookahead scheduling
- sample requests
- feedback handling
- cycle tracking + signals

**API sketch**

- `start(options)`
- `stop()`
- `updatePattern(pattern)`
- `updateCyclesPerSecond(cps)`
- `handleFeedback(feedback)`
- `signals` (or delegate to outer playback)
- `onCycleCompleted` callback or signal emission

**Dependencies**
Pass in all dependencies already available in `StrudelPlayback`:

- pattern
- player options
- sendControl
- scope + dispatchers

---

## 2) Add CycleCompleted signal

**Location**
`KlangPlaybackSignal`

**Signal**
`CycleCompleted(cycleIndex: Long, atTimeSec: Double)`

**Semantics**

- `cycleIndex = 0` means cycle `[0,1)` has completed
- `atTimeSec` is wall‑clock boundary time (with latency offset if desired)

---

## 3) Emit CycleCompleted (stall‑safe)

**Where**
Inside `StrudelPlaybackController` main loop

**Logic**

- Compute `elapsedSec`
- `completedCycle = floor(elapsedSec / secPerCycle) - 1`
- If `completedCycle > lastEmittedCycle`, emit **all missing cycles** in order
- Emit on `callbackDispatcher` to avoid interfering with scheduling path

This guarantees no skipped cycles even after stalls.

---

## 4) Implement `ContinuousStrudelPlayback`

**Role**
A thin `KlangPlayback` wrapper that:

- owns a controller
- delegates lifecycle: `start`, `stop`, `handleFeedback`, etc.
- exposes `signals` from controller

This preserves current behavior.

---

## 5) Implement `OneShotStrudelPlayback`

**Role**
Another thin `KlangPlayback` wrapper that:

- delegates to controller
- listens for `CycleCompleted`
- stops after the desired number of cycles (default 1)

This becomes the foundation for sample previews and other short playbacks.

---

## 6) Update `KlangPlayer` helper(s)

Provide creation functions for:

- `playStrudel(pattern)` → `ContinuousStrudelPlayback`
- `playStrudelOnce(pattern, cycles = 1)` → `OneShotStrudelPlayback`

---

## 7) Wire into SamplesLibraryPage (later step)

Once one‑shot exists:

- build pattern for sample
- `playStrudelOnce(pattern, cycles = 1)` for preview

---

# Is this over‑engineering?

It’s justified **if you anticipate more playback modes** or want to isolate core scheduling logic for testing and
reuse.  
It becomes over‑engineering only if:

- this is the only alternate mode ever needed, and
- the controller ends up duplicating APIs without adding clarity.

Given your stated long‑term needs, I’d proceed with the controller split.

---

If you want, I can refine the controller API further or draft a migration checklist for the existing `StrudelPlayback`
code.

Here’s a **refined controller API + migration checklist** you can hand to the coding agent. No checkmarks included.

---

# StrudelPlaybackController: Proposed API + Migration Checklist

## Proposed Controller API (Kotlin)

```kotlin
class StrudelPlaybackController(
    playbackId: String,
    pattern: StrudelPattern,
    playerOptions: KlangPlayer.Options,
    sendControl: (KlangCommLink.Cmd) -> Unit,
    scope: CoroutineScope,
    fetcherDispatcher: CoroutineDispatcher,
    callbackDispatcher: CoroutineDispatcher,
    onStopped: () -> Unit,
    signals: KlangPlaybackSignals,
) {
    data class Options(
        val lookaheadSec: Double = 1.0,
        val cyclesPerSecond: Double = 0.5,
        val prefetchCycles: Int? = null,
    )

    fun start(options: Options = Options())
    fun stop()

    fun updatePattern(pattern: StrudelPattern)
    fun updateCyclesPerSecond(cps: Double)

    fun handleFeedback(feedback: KlangCommLink.Feedback)

    // Optional: expose controller state for wrappers
    val isRunning: Boolean
}
```

Notes:

- `signals` is passed in by the wrapper so both wrappers share the same bus.
- `onStopped` is invoked when the controller actually stops (used by wrappers to unregister / clean up).

---

## Migration Checklist

### 1) Introduce `StrudelPlaybackController`

- New file in `io.peekandpoke.klang.strudel`.
- Move all logic from the current `StrudelPlayback` into the controller, including:
    - preload logic
    - sample lookahead
    - event querying / scheduling
    - feedback handling
    - cycle tracking + emission
    - cleanup command
    - lifecycle loop

### 2) Add CycleCompleted signal

- Add `CycleCompleted(cycleIndex: Long, atTimeSec: Double)` to `KlangPlaybackSignal`.
- Emitted by controller using elapsed time + latency offset.
- Stall‑safe: if multiple cycles are missed, emit all in order.

### 3) Implement `ContinuousStrudelPlayback`

- New class implements `KlangPlayback`.
- Holds:
    - `playbackId`
    - `signals`
    - `controller`
- Delegates:
    - `start()` -> `controller.start(...)`
    - `stop()` -> `controller.stop()`
    - `handleFeedback()` -> `controller.handleFeedback(feedback)`
- No extra behavior.

### 4) Implement `OneShotStrudelPlayback`

- Same as Continuous, but:
    - Subscribe to `CycleCompleted`
    - When `cycleIndex == cyclesToPlay - 1`, call `stop()`
    - Ensure listener is removed on stop (or clear via signals)
- Accept `cyclesToPlay` in constructor (default 1).

### 5) Replace/adjust `playStrudel` helper

- `playStrudel(pattern)` returns `ContinuousStrudelPlayback`.
- Add `playStrudelOnce(pattern, cycles = 1)` -> `OneShotStrudelPlayback`.
- Ensure `registerPlayback` and `unregisterPlayback` still work.

### 6) Update call sites

- Anywhere expecting `StrudelPlayback` type:
    - either use interface `KlangPlayback`
    - or update to the new concrete types
- If existing code relies on `StrudelPlayback.Options`, either:
    - re‑expose a shared options data class
    - or reference `StrudelPlaybackController.Options`

### 7) Verification / sanity checks

- Continuous playback still behaves identical.
- One‑shot stops after one cycle.
- CycleCompleted is emitted even after artificial delays.

---

## Notes on Over‑Engineering

This split is reasonable if:

- you foresee more playback modes (preview, finite length, region playback)
- you want to keep the scheduling core isolated and testable
- you want UI features based on cycle signals

It becomes over‑engineering only if the system will never grow beyond continuous playback + single‑cycle preview.

---

If you want, I can also provide a minimal class diagram or a suggested file layout for the new classes.
