Let me explore the current code structure to understand the Samples system and how it's used, so I can design the
prefetch and event system precisely.Here are the detailed instructions for Claude Code:

---

# Task: Sample Pre-fetching Before Playback + Playback Event System

Two features to implement:

1. **Pre-fetch samples before first playback** — Query the first N cycles of the pattern, identify which sounds are
   samples, load them, send them to the backend, and **wait** until the backend has acknowledged them before starting
   the main playback loop.

2. **Pub/Sub event system on `KlangPlayback`** — Replace the single `onVoiceScheduled` callback with a proper
   sealed-class event system so frontends can observe lifecycle events.

---

## Part 1: Playback Event System

### 1A. Create `KlangPlaybackEvent` sealed class

**New file: `klang/src/commonMain/kotlin/KlangPlaybackEvent.kt`**

```kotlin
package io.peekandpoke.klang.audio_engine

/**
 * Events emitted during playback lifecycle.
 * Subscribe via [KlangPlayback.events].
 */
sealed class KlangPlaybackEvent {

    /**
     * Emitted when sample preloading begins.
     * Gives the frontend a chance to show a "loading samples..." indicator.
     */
    data class PreloadingSamples(
        /** Number of unique samples that need to be loaded */
        val count: Int,
        /** The sample identifiers being loaded */
        val samples: List<String>,
    ) : KlangPlaybackEvent()

    /**
     * Emitted when sample preloading completes (all samples sent to backend).
     */
    data class SamplesPreloaded(
        /** Number of samples that were loaded */
        val count: Int,
        /** How long preloading took in milliseconds */
        val durationMs: Long,
    ) : KlangPlaybackEvent()

    /**
     * Emitted when playback has actually started (after preloading, first voices scheduled).
     */
    data object PlaybackStarted : KlangPlaybackEvent()

    /**
     * Emitted when playback has stopped.
     */
    data object PlaybackStopped : KlangPlaybackEvent()
}
```

### 1B. Create `KlangPlaybackEventBus` utility class

**New file: `klang/src/commonMain/kotlin/KlangPlaybackEventBus.kt`**

```kotlin
package io.peekandpoke.klang.audio_engine

/**
 * Simple, synchronous pub/sub event bus for playback lifecycle events.
 * 
 * Listeners are invoked synchronously on the thread that emits the event.
 * For UI updates, the subscriber is responsible for dispatching to the appropriate thread/dispatcher.
 * 
 * Thread-safety: Listeners list is copied-on-read to avoid ConcurrentModificationException.
 */
class KlangPlaybackEventBus {
    private val listeners = mutableListOf<(KlangPlaybackEvent) -> Unit>()

    /**
     * Subscribe to all events. Returns an unsubscribe function.
     */
    fun subscribe(listener: (KlangPlaybackEvent) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Subscribe to events of a specific type. Returns an unsubscribe function.
     */
    inline fun <reified T : KlangPlaybackEvent> on(crossinline listener: (T) -> Unit): () -> Unit {
        return subscribe { event ->
            if (event is T) listener(event)
        }
    }

    /**
     * Emit an event to all subscribers.
     */
    fun emit(event: KlangPlaybackEvent) {
        // Copy to avoid ConcurrentModificationException if a listener unsubscribes during iteration
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener(event)
        }
    }

    /**
     * Remove all listeners.
     */
    fun clear() {
        listeners.clear()
    }
}
```

### 1C. Update `KlangPlayback` interface

**File: `klang/src/commonMain/kotlin/KlangPlayback.kt`**

Add the `events` property:

```kotlin
package io.peekandpoke.klang.audio_engine

/**
 * Interface for playback implementations.
 * Each music source (Strudel, MIDI, etc.) implements this interface with its own logic.
 */
interface KlangPlayback {
    /**
     * Event bus for subscribing to playback lifecycle events.
     */
    val events: KlangPlaybackEventBus

    /**
     * Start playback
     */
    fun start()

    /**
     * Stop playback
     */
    fun stop()
}
```

### 1D. Update `StrudelPlayback` — integrate event bus and remove `onVoiceScheduled`

**File: `strudel/src/commonMain/kotlin/StrudelPlayback.kt`**

Changes:

1. **Add the `events` property** (implementing the interface):

```kotlin
override val events = KlangPlaybackEventBus()
```

2. **Remove the `onVoiceScheduled` field entirely:**

```kotlin
// DELETE THIS:
var onVoiceScheduled: ((event: ScheduledVoiceEvent) -> Unit)? = null
```

3. **Keep `ScheduledVoiceEvent` as-is** but we won't emit it through the event bus (it's a high-frequency per-voice
   event that the highlighting code needs). Instead, keep a callback specifically for voice scheduling but route it
   through the bus.

Actually, **on second thought**, the voice-scheduled events are very high-frequency and used for UI highlighting with
precise timing. Putting them through the event bus is fine — the bus is synchronous and lightweight. But we need a
`KlangPlaybackEvent` subclass for it.

Add to `KlangPlaybackEvent`:

```kotlin
/**
 * Emitted when voices are scheduled for a cycle chunk.
 * Contains all voices scheduled in this batch for efficient processing.
 *
 * Note: Uses absolute wall-clock times (seconds from KlangTime epoch)
 * for UI highlighting (compared with Date.now()).
 */
data class VoicesScheduled(
    /** All voice events scheduled in this batch */
    val voices: List<VoiceEvent>,
) : KlangPlaybackEvent() {
    data class VoiceEvent(
        /** Absolute start time in seconds (wall-clock) */
        val startTime: Double,
        /** Absolute end time in seconds (wall-clock) */
        val endTime: Double,
        /** Source locations for code highlighting */
        val sourceLocations: Any?, // SourceLocationChain from strudel module
    ) {
        /** Start time in milliseconds */
        val startTimeMs get() = (startTime * 1000).toLong()

        /** End time in milliseconds */
        val endTimeMs get() = (endTime * 1000).toLong()
    }
}
```

**IMPORTANT about module dependencies:** `KlangPlaybackEvent` lives in `klang` module, which cannot depend on `strudel`
module types like `SourceLocationChain` or `StrudelVoiceData`. So `sourceLocations` must be typed as `Any?`. The
consumer (like `DashboardPage`) will cast it.

Alternatively — and this is cleaner — **keep `ScheduledVoiceEvent` in the strudel module and emit it through the event
bus wrapped in a generic event.** The event bus accepts `KlangPlaybackEvent`, so we can create a Strudel-specific
subclass:

**Better approach: Make `VoicesScheduled` a strudel-level event.**

Since `KlangPlaybackEvent` is a sealed class, it can't be extended outside its module. So instead:

**Change `KlangPlaybackEvent` to be an interface (not sealed)**. This allows strudel-specific events. OR keep it sealed
but put a generic "custom data" event in it.

**Final decision — keep it sealed, use a generic wrapper:**

Add to `KlangPlaybackEvent`:

```kotlin
/**
 * Generic custom event for module-specific data.
 * Modules like Strudel can wrap their own events in this.
 */
data class Custom(
    val type: String,
    val data: Any,
) : KlangPlaybackEvent()
```

Then in `StrudelPlayback.queryEvents()`, replace the `onVoiceScheduled` callback section with:

```kotlin
// Fire events through the event bus
if (sendEvents && voiceEvents.isNotEmpty()) {
    scope.launch(callbackDispatcher) {
        events.emit(
            KlangPlaybackEvent.Custom(
                type = "voices-scheduled",
                data = voiceEvents
                    .distinctBy { it.startTime to it.sourceLocations }
            )
        )
    }
}
```

### 1E. Update `DashboardPage.kt` — use event bus instead of `onVoiceScheduled`

**File: `src/jsMain/kotlin/pages/DashboardPage.kt`**

Replace:

```kotlin
song?.onVoiceScheduled = { event -> scheduleHighlight(event) }
```

With:

```kotlin
song?.events?.on<KlangPlaybackEvent.Custom> { event ->
    if (event.type == "voices-scheduled") {
        @Suppress("UNCHECKED_CAST")
        val voices = event.data as List<ScheduledVoiceEvent>
        voices.forEach { scheduleHighlight(it) }
    }
}
```

Also add handling for other events if desired (e.g. show loading indicator):

```kotlin
song?.events?.on<KlangPlaybackEvent.PreloadingSamples> { event ->
    console.log("Preloading ${event.count} samples...")
}
song?.events?.on<KlangPlaybackEvent.SamplesPreloaded> { event ->
    console.log("Samples loaded in ${event.durationMs}ms")
}
```

### 1F. Clean up `StrudelPlayback.stop()` — emit stopped event and clear bus

In `StrudelPlayback.stop()`, before the `onStopped` callback:

```kotlin
events.emit(KlangPlaybackEvent.PlaybackStopped)
events.clear()
```

---

## Part 2: Sample Pre-fetching

### 2A. Overview of the approach

Before starting the main playback loop in `StrudelPlayback.run()`, we:

1. Query the first N cycles of the pattern (e.g., 2 cycles)
2. Identify which events require sample sounds (not oscillators)
3. For each unique sample: resolve it, load the PCM, send it to the backend via `control.send()`
4. Wait until all samples are loaded and sent
5. **Then** start the main playback loop

The key insight: We do NOT need to wait for the backend to "acknowledge" receipt. The `KlangCommLink` is a FIFO ring
buffer. As long as sample commands are sent **before** the first `ScheduleVoice` commands, the backend will process them
in order. So we just need to ensure all `Sample.Complete` / `Sample.Chunk` commands are enqueued before any
`ScheduleVoice`.

For JS, the `JsAudioBackend.loop()` drains the control buffer every 10ms and forwards via `postMessage`. Since
`postMessage` preserves order, the samples will arrive at the worklet before the voice commands. **So no special
acknowledgement is needed** — just "send samples first, then send voices."

### 2B. Changes to `StrudelPlayback.run()`

**File: `strudel/src/commonMain/kotlin/StrudelPlayback.kt`**

Modify the `run()` method to add a preload phase:

```kotlin
private suspend fun run(scope: CoroutineScope) {
    // Record start time for autonomous progression
    startTimeMs = klangTime.internalMsNow()

    // Reset state
    queryCursorCycles = 0.0
    sampleSoundLookAheadPointer = 0.0

    // ===== PRELOAD PHASE =====
    // Query first cycles to discover and preload samples before any voices are scheduled.
    // This ensures the backend has sample data before it tries to play them.
    preloadSamples()

    // Emit started event
    events.emit(KlangPlaybackEvent.PlaybackStarted)

    // Normal lookahead for future sample sounds
    lookAheadForSampleSounds(0.0, sampleSoundLookAheadCycles)

    while (scope.isActive) {
        // Update local frame counter based on elapsed time
        updateLocalFrameCounter()

        // Look ahead for sample sound
        lookAheadForSampleSounds(queryCursorCycles + sampleSoundLookAheadCycles, 1.0)

        // Request the next cycles from the source
        requestNextCyclesAndAdvanceCursor()

        // Query feedback-events from backend (only for sample requests now)
        processFeedbackEvents()

        // roughly 60 FPS
        delay(16)
    }

    println("StrudelPlayback stopped")
}
```

### 2C. Add `preloadSamples()` method

**File: `strudel/src/commonMain/kotlin/StrudelPlayback.kt`**

Add this new method:

```kotlin
/**
 * Pre-fetches samples needed for the first cycles of the pattern.
 * 
 * Queries the pattern for the first [prefetchCycles] cycles, identifies sample sounds,
 * loads their PCM data, and sends them to the backend BEFORE any voices are scheduled.
 * 
 * This ensures the backend has sample data available when the first ScheduleVoice
 * commands arrive, preventing silent first notes.
 */
private suspend fun preloadSamples() {
    val preloadStartMs = klangTime.internalMsNow()

    // Query first 2 cycles (configurable via options.prefetchCycles)
    val prefetchCycles = 2.0
    val events = try {
        queryEvents(from = 0.0, to = prefetchCycles, sendEvents = false)
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }

    // Identify unique sample requests (non-oscillator sounds)
    val sampleRequests = events
        .map { it.data.asSampleRequest() }
        .toSet()

    if (sampleRequests.isEmpty()) return

    // Emit preloading event
    events.emit(
        KlangPlaybackEvent.PreloadingSamples(
            count = sampleRequests.size,
            samples = sampleRequests.map { "${it.bank ?: ""}/${it.sound ?: ""}:${it.index ?: 0}" },
        )
    )

    // Load all samples concurrently and send to backend
    val jobs = sampleRequests.map { req ->
        scope.async(fetcherDispatcher) {
            // Remember this sample so lookAheadForSampleSounds doesn't re-request it
            samplesAlreadySent.add(req)

            // Resolve and load
            val loaded = samples.get(req)

            val cmd = if (loaded?.sample == null || loaded.pcm == null) {
                KlangCommLink.Cmd.Sample.NotFound(
                    playbackId = playbackId,
                    req = req,
                )
            } else {
                KlangCommLink.Cmd.Sample.Complete(
                    playbackId = playbackId,
                    req = req,
                    note = loaded.sample.note,
                    pitchHz = loaded.sample.pitchHz,
                    sample = loaded.pcm,
                )
            }

            // Send to backend (this goes into the ring buffer, processed in order)
            control.send(cmd)
        }
    }

    // Wait for ALL samples to be loaded and sent
    jobs.forEach { it.await() }

    val durationMs = (klangTime.internalMsNow() - preloadStartMs).toLong()

    // Emit completion event
    events.emit(
        KlangPlaybackEvent.SamplesPreloaded(
            count = sampleRequests.size,
            durationMs = durationMs,
        )
    )
}
```

**IMPORTANT:** There's a name collision — `events` is used both for the `KlangPlaybackEventBus` property and the local
variable from `queryEvents()`. Rename the local variable to `preloadVoices` or similar:

```kotlin
val preloadVoices = try {
    queryEvents(from = 0.0, to = prefetchCycles, sendEvents = false)
} catch (e: Exception) {
    ...
}

val sampleRequests = preloadVoices
    .map { it.data.asSampleRequest() }
    .toSet()
```

### 2D. Update `startTimeMs` — reset AFTER preloading

The preloading takes time (network fetches, decoding). We don't want this time counted against playback progression. So
reset `startTimeMs` **after** preloading:

```kotlin
private suspend fun run(scope: CoroutineScope) {
    // Reset state
    queryCursorCycles = 0.0
    sampleSoundLookAheadPointer = 0.0

    // ===== PRELOAD PHASE =====
    // Use a temporary time for preloading (doesn't matter for timing)
    preloadSamples()

    // NOW record start time — after preloading, so playback time starts fresh
    startTimeMs = klangTime.internalMsNow()

    // Emit started event
    events.emit(KlangPlaybackEvent.PlaybackStarted)

    // ... rest of loop
}
```

This is critical — if preloading takes 500ms, we don't want the first 500ms of notes to be considered "in the past."

---

## Summary of All Files

| File                                                   | Action                                                                                                                                                                                                                                                                                                                                                              |
|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `klang/src/commonMain/kotlin/KlangPlaybackEvent.kt`    | **NEW** — Sealed class with `PreloadingSamples`, `SamplesPreloaded`, `PlaybackStarted`, `PlaybackStopped`, `Custom`                                                                                                                                                                                                                                                 |
| `klang/src/commonMain/kotlin/KlangPlaybackEventBus.kt` | **NEW** — Simple pub/sub bus with `subscribe()`, `on<T>()`, `emit()`, `clear()`                                                                                                                                                                                                                                                                                     |
| `klang/src/commonMain/kotlin/KlangPlayback.kt`         | **MODIFY** — Add `val events: KlangPlaybackEventBus` to interface                                                                                                                                                                                                                                                                                                   |
| `strudel/src/commonMain/kotlin/StrudelPlayback.kt`     | **MODIFY** — (1) Add `override val events = KlangPlaybackEventBus()`, (2) Remove `onVoiceScheduled` property, (3) Add `preloadSamples()` method, (4) Update `run()` to call preload then reset `startTimeMs`, (5) Replace `onVoiceScheduled?.invoke` with `events.emit(Custom(...))`, (6) Emit `PlaybackStopped` in `stop()`, (7) Call `events.clear()` in `stop()` |
| `strudel/src/commonMain/kotlin/StrudelPatternEvent.kt` | **NO CHANGE** — `ScheduledVoiceEvent` stays as-is, still used as the data inside `Custom` events                                                                                                                                                                                                                                                                    |
| `src/jsMain/kotlin/pages/DashboardPage.kt`             | **MODIFY** — Replace `song?.onVoiceScheduled = ...` with `song?.events?.on<KlangPlaybackEvent.Custom> { ... }`                                                                                                                                                                                                                                                      |

## Key Design Decisions

1. **`KlangPlaybackEvent` is sealed** — keeps it type-safe. Module-specific events use `Custom(type, data)`.
2. **Event bus is synchronous** — no coroutines needed in the bus itself. The emitter (in `StrudelPlayback`) already
   dispatches to `callbackDispatcher` for UI events.
3. **No backend acknowledgement for preloading** — FIFO ordering of the ring buffer + `postMessage` guarantees samples
   arrive before voices. No roundtrip needed.
4. **`startTimeMs` is set AFTER preloading** — preload time doesn't count as playback time. The first notes will have
   relative time 0.0 from the backend's perspective.
5. **`ScheduledVoiceEvent` stays in strudel module** — it has strudel-specific types (`StrudelVoiceData`,
   `SourceLocationChain`). The event bus wraps it in `Custom`.

## Build & Test

```shell script
./gradlew build
```

Check:

- `klang` module compiles (new files)
- `strudel` module compiles (modified `StrudelPlayback`)
- `src` (frontend) compiles (`DashboardPage` changes)
- Existing tests pass
- Manual test: Play a pattern with sample sounds — first beat should now play correctly

------------------------------------------------------------------------------------

      I like "signals" — it's distinctive, won't clash with anything in the codebase, and reads nicely:

```kotlin
song?.signals?.on<KlangPlaybackSignal.PreloadingSamples> { ... }
song?.signals?.on<KlangPlaybackSignal.PlaybackStarted> { ... }
```

So the full naming would be:

| Old name                | New name               |
|-------------------------|------------------------|
| `KlangPlaybackEventBus` | `KlangPlaybackSignals` |
| `KlangPlaybackEvent`    | `KlangPlaybackSignal`  |
| property `events`       | property `signals`     |

And the sealed class becomes `KlangPlaybackSignal.PreloadingSamples`, `KlangPlaybackSignal.PlaybackStarted`, etc. —
clean and unambiguous. 👌

Want me to regenerate the full instructions doc with this naming applied?
