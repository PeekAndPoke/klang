Let me gather a few more details to make the instructions precise and complete.Now let me also check the `playStrudel`
extension and `DashboardPage` to understand how `ScheduledVoiceEvent` is consumed by the UI for highlighting:Now I have
a complete picture. Let me also check the `DashboardPage` for how `ScheduledVoiceEvent` timing is used for highlighting:
Now let me check how the `DashboardPage` uses the `ScheduledVoiceEvent` times for highlighting, since that will be
affected by the change:Now I have the full picture. Let me also verify how `ScheduledVoiceEvent` times are used in
`DashboardPage.kt` to make sure we account for the callback side too.Now I have a complete understanding of the
codebase. Let me also check for any tests that reference the Synchronize command:Now I have a complete picture of every
file that needs to change. Let me look at one more thing — how `DashboardPage` uses `ScheduledVoiceEvent` times — to
make sure the instructions cover the callback timing too.Now I have the complete picture. Here are the detailed
instructions:

---

# Task: Simplify Audio Timing — Switch to Relative-Time Events

## Problem

The JS audio worklet drops the first sound events because two independent clocks (frontend `MainThreadTimeSource` using
`performance.now()` vs backend `AudioWorkletTimeSource` using frame-counting) disagree on "now". The current
`Cmd.Synchronize` + offset machinery was added to bridge this, but it has race conditions and complexity issues.

## Solution: Relative-Time Events

**Core idea:** Stop sending absolute wall-clock times from frontend to backend. Instead, send times *relative to the
playback start* (seconds since this playback began). The backend records its own "epoch" (in its own clock) when it
first sees a playback, and converts relative → absolute internally. This eliminates all cross-clock synchronization.

---

## Files to Modify (in order)

### 1. `strudel/src/commonMain/kotlin/StrudelPlayback.kt`

**Changes:**

**a) In `queryEvents()` — send relative times instead of absolute:**

Find the section that computes `absoluteStartTime` and `absoluteEndTime`. Change it so `ScheduledVoice` gets **relative
** times (seconds since playback start), while `ScheduledVoiceEvent` (used for UI highlighting) still gets **absolute**
times (it needs them for `Date.now()` comparison in the UI).

Current code:

```kotlin
val playbackStartTimeSec = startTimeMs / 1000.0
// ...
val absoluteStartTime = playbackStartTimeSec + relativeStartTime
val absoluteEndTime = absoluteStartTime + duration
```

Change to:

```kotlin
val playbackStartTimeSec = startTimeMs / 1000.0
// ...
// Absolute times for UI callbacks (need wall-clock for setTimeout highlighting)
val absoluteStartTime = playbackStartTimeSec + relativeStartTime
val absoluteEndTime = absoluteStartTime + duration
```

Keep `ScheduledVoiceEvent` using absolute times (it's only used for frontend UI highlighting via `Date.now()`). But
change the `ScheduledVoice` to use relative times:

```kotlin
ScheduledVoice(
    playbackId = playbackId,
    data = event.data.toVoiceData(),
    startTime = relativeStartTime,       // ← CHANGED: relative, not absolute
    gateEndTime = relativeStartTime + duration,  // ← CHANGED: relative, not absolute
)
```

**b) Remove `syncWithBackend()` method entirely** — delete the whole method.

**c) In `run()` — remove the `syncWithBackend()` call:**

```kotlin
private suspend fun run(scope: CoroutineScope) {
    startTimeMs = klangTime.internalMsNow()

    // DELETE: syncWithBackend() call - no longer needed

    queryCursorCycles = 0.0
    sampleSoundLookAheadPointer = 0.0
// ... rest stays the same
```

**d) In `cleanUpBackend()` — keep as is.** The `Cmd.Cleanup` is still needed.

---

### 2. `audio_bridge/src/commonMain/kotlin/ScheduledVoice.kt`

**Update the doc comments** to reflect that times are now relative:

```kotlin
@Serializable
data class ScheduledVoice(
    val playbackId: String,
    val data: VoiceData,
    /** Time in seconds relative to playback start */
    val startTime: Double,
    /** Time in seconds relative to playback start when the note key is lifted */
    val gateEndTime: Double,
)
```

No structural change needed — the fields stay the same, only their semantics change.

---

### 3. `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

This is the biggest change. Replace the offset/sync machinery with a simple playback-epoch map.

**a) Replace state fields:**

Remove these fields:

```kotlin
// Map to store time offsets: playbackId -> offset (seconds)
private val playbackOffsets = mutableMapOf<String, Double>()

// Map to store when the last sync happened for a playback: playbackId -> frame number
private val playbackSyncFrame = mutableMapOf<String, Long>()

// Track the last processed frame to establish "Now" for incoming commands
private var lastRenderedFrame: Long = 0
```

Add this field instead:

```kotlin
// Map playbackId -> backend-local epoch (seconds since backend start) when this playback was first seen
private val playbackEpochs = mutableMapOf<String, Double>()
```

**b) Remove `setPlaybackOffset()` method entirely.**

**c) Update `cleanup()` — clean up the new map:**

```kotlin
fun cleanup(playbackId: String) {
    playbackEpochs.remove(playbackId)
}
```

**d) Update `clear()` — clean up the new map:**

```kotlin
fun clear() {
    scheduled.clear()
    active.clear()
    playbackEpochs.clear()
}
```

**e) Simplify `scheduleVoice()` — remove all offset logic:**

```kotlin
fun scheduleVoice(voice: ScheduledVoice) {
    val pid = voice.playbackId

    // Auto-register playback epoch on first voice
    if (pid !in playbackEpochs) {
        // Record "now" in backend time as this playback's epoch
        // All voice times for this playback are relative to this moment.
        val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
        playbackEpochs[pid] = nowSec
    }

    // Schedule directly — no offset adjustment needed.
    // Times are relative to playback start; conversion happens in promoteScheduled.
    scheduled.push(voice)

    // Prefetch sound samples (unchanged)
    if (voice.data.isSampleSound()) {
        val req = voice.data.asSampleRequest()
        if (!samples.containsKey(req)) {
            samples[req] = SampleEntry.Requested(req)
            options.commLink.feedback.send(
                KlangCommLink.Feedback.RequestSample(
                    playbackId = pid,
                    req = voice.data.asSampleRequest(),
                )
            )
        }
    }
}
```

Note: We need a field to track the last frame so the epoch is accurate. Rename `lastRenderedFrame` to
`lastProcessedFrame` and keep it, but *only* for epoch recording:

```kotlin
// Track the last processed frame (for epoch recording)
private var lastProcessedFrame: Long = 0
```

Update `process()` to write it:

```kotlin
fun process(cursorFrame: Long) {
    lastProcessedFrame = cursorFrame
    val blockEnd = cursorFrame + options.blockFrames
// ... rest unchanged
```

**f) Rewrite `promoteScheduled()` — remove all forgiveness logic, add relative→absolute conversion:**

```kotlin
private fun promoteScheduled(nowFrame: Long, blockEnd: Long) {
    val blockEndSec = backendStartTimeSec + (blockEnd.toDouble() / options.sampleRate.toDouble())
    val nowSec = backendStartTimeSec + (nowFrame.toDouble() / options.sampleRate.toDouble())

    // Allow events up to 1 block in the past (normal scheduling jitter)
    val oldestAllowedSec = nowSec - (options.blockFrames.toDouble() / options.sampleRate.toDouble())

    while (true) {
        val head = scheduled.peek() ?: break

        // Look up this playback's epoch
        val epoch = playbackEpochs[head.playbackId]
        if (epoch == null) {
            // No epoch recorded — shouldn't happen, but skip gracefully
            scheduled.pop()
            continue
        }

        // Convert relative time to absolute backend time
        val absoluteStartSec = epoch + head.startTime

        // Early exit: if this event is beyond current block, stop
        if (absoluteStartSec >= blockEndSec) break

        // Remove from heap
        scheduled.pop()

        // Drop if too old
        if (absoluteStartSec < oldestAllowedSec) {
            continue
        }

        // Convert the voice to use absolute times for makeVoice
        val absoluteVoice = head.copy(
            startTime = absoluteStartSec,
            gateEndTime = epoch + head.gateEndTime,
        )

        makeVoice(absoluteVoice, nowFrame)?.let {
            active.add(it)
        }
    }
}
```

---

### 4. `audio_bridge/src/commonMain/kotlin/infra/KlangCommLink.kt`

**Remove `Cmd.Synchronize`:**

Delete the entire `Synchronize` data class from the `Cmd` sealed interface:

```kotlin
// DELETE THIS ENTIRE CLASS:
data class Synchronize(
    override val playbackId: String,
    val time: Double,
) : Cmd {
    companion object {
        const val SERIAL_NAME = "synchronize"
    }
}
```

Keep `Cmd.Cleanup` — it's still needed.

---

### 5. `audio_be/src/jsMain/kotlin/worklet/KlangAudioWorklet.kt`

**Remove the `Synchronize` handler from the `when` block in `port.onmessage`:**

```kotlin
when (cmd) {
    is KlangCommLink.Cmd.ScheduleVoice -> {
        ctx.voices.scheduleVoice(voice = cmd.voice)
    }

    // DELETE the Synchronize case entirely

    is KlangCommLink.Cmd.Cleanup -> {
        ctx.voices.cleanup(cmd.playbackId)
    }

    is KlangCommLink.Cmd.Sample -> ctx.voices.addSample(msg = cmd)
}
```

---

### 6. `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt`

**Remove the `Synchronize` handler from the `when` block:**

```kotlin
when (cmd) {
    is KlangCommLink.Cmd.ScheduleVoice -> {
        voices.scheduleVoice(voice = cmd.voice)
    }

    // DELETE the Synchronize case entirely

    is KlangCommLink.Cmd.Cleanup -> {
        voices.cleanup(cmd.playbackId)
    }

    is KlangCommLink.Cmd.Sample -> voices.addSample(msg = cmd)
}
```

---

### 7. `audio_be/src/jsMain/kotlin/worklet/WorkletContract.kt`

**Remove `Synchronize` encoding and decoding:**

In the `Cmd.encode()` function, delete the `is Cmd.Synchronize ->` case.

In the `decodeCmd()` function, delete the `Cmd.Synchronize.SERIAL_NAME ->` case.

---

### 8. `strudel/src/commonMain/kotlin/StrudelPatternEvent.kt`

**Update doc comments on `ScheduledVoiceEvent`** — these still use absolute times (wall-clock), which is correct for the
UI. Just update the comment to clarify the distinction:

```kotlin
/**
 * Event fired when a voice is scheduled for playback.
 *
 * Uses ABSOLUTE wall-clock times (from KlangTime epoch) because
 * this is consumed by the frontend UI for highlighting (compared with Date.now()).
 * 
 * Note: This is different from ScheduledVoice which uses RELATIVE times
 * (seconds since playback start) for the audio backend.
 */
```

---

## Summary of What Gets Deleted

| Thing                                   | Where                   | Why safe to remove                                           |
|-----------------------------------------|-------------------------|--------------------------------------------------------------|
| `Cmd.Synchronize` class                 | `KlangCommLink.kt`      | No longer needed — no cross-clock sync                       |
| `syncWithBackend()` method              | `StrudelPlayback.kt`    | Was sending `Cmd.Synchronize`                                |
| `syncWithBackend()` call                | `StrudelPlayback.run()` | Caller of above                                              |
| `setPlaybackOffset()` method            | `VoiceScheduler.kt`     | Replaced by epoch auto-detection                             |
| `playbackOffsets` map                   | `VoiceScheduler.kt`     | Replaced by `playbackEpochs`                                 |
| `playbackSyncFrame` map                 | `VoiceScheduler.kt`     | Forgiveness logic removed                                    |
| Forgiveness logic in `promoteScheduled` | `VoiceScheduler.kt`     | No longer needed — events are always in correct clock domain |
| `Synchronize` handler                   | `KlangAudioWorklet.kt`  | No more sync commands                                        |
| `Synchronize` handler                   | `JvmAudioBackend.kt`    | No more sync commands                                        |
| `Synchronize` encode/decode             | `WorkletContract.kt`    | No more sync commands                                        |

## Summary of What Gets Added

| Thing                        | Where                               | Purpose                                                         |
|------------------------------|-------------------------------------|-----------------------------------------------------------------|
| `playbackEpochs` map         | `VoiceScheduler.kt`                 | Maps `playbackId` → backend-local time when first event arrived |
| `lastProcessedFrame` field   | `VoiceScheduler.kt`                 | Tracks current frame for accurate epoch recording               |
| Auto-epoch registration      | `VoiceScheduler.scheduleVoice()`    | Records epoch on first event per playback                       |
| Relative→absolute conversion | `VoiceScheduler.promoteScheduled()` | Converts `epoch + relativeTime` to absolute backend time        |

## Key Invariants to Verify After Changes

1. **JVM backend still works** — on JVM, `postMessage` latency is ~0, so the epoch will be recorded almost instantly.
   Behavior should be identical to before.
2. **JS backend no longer drops first events** — the epoch is established when the first `ScheduleVoice` arrives at the
   worklet, so all events are relative to that moment.
3. **UI highlighting still works** — `ScheduledVoiceEvent` still uses absolute wall-clock times, so
   `DashboardPage.scheduleHighlight()` (which compares with `Date.now()`) continues to work correctly.
4. **Samples still work** — sample prefetch logic in `scheduleVoice` is unchanged.
5. **Cleanup still works** — `Cmd.Cleanup` is kept, and `cleanup()` now clears `playbackEpochs` instead of
   `playbackOffsets`/`playbackSyncFrame`.

## Build & Test

After making changes, run:

```shell script
./gradlew build
```

Specifically check:

- `audio_be` module compiles (commonMain, jsMain, jvmMain)
- `audio_bridge` module compiles (commonMain)
- `strudel` module compiles (commonMain)
- Any existing tests in `audio_be/src/commonTest` still pass
