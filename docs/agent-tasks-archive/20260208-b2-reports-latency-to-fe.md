I now have a complete understanding of all the relevant code. Here are the detailed instructions:

---

# Task: Backend Latency Feedback for Frontend Highlight Synchronization

## Problem

The `DashboardPage` schedules code highlights using frontend wall-clock times (`event.startTimeMs` vs `Date.now()`). But
the audio actually plays later because of:

- `postMessage` latency from main thread to audio worklet
- Backend buffering/render queue delay
- Web Audio output buffer latency

So highlights flash **before** the sound is heard.

## Solution

When the backend (`VoiceScheduler`) records a playback epoch (on first `ScheduleVoice` for a `playbackId`), it knows the
time offset between the frontend's time and its own. It sends this offset back to the frontend via the existing
`KlangCommLink.Feedback` channel. The frontend stores it and applies it when emitting `VoicesScheduled` signals, so
highlights are delayed to match actual audio output.

---

## Files to Modify (in order)

### 1. `audio_bridge/src/commonMain/kotlin/infra/KlangCommLink.kt`

**Add a new `Feedback` subclass: `PlaybackLatency`**

Inside the `Feedback` sealed interface, add:

```kotlin
@Serializable
@SerialName("playback-latency")
data class PlaybackLatency(
    override val playbackId: String,
    /** 
     * The measured time offset in seconds.
     * Positive value means the backend is "behind" the frontend by this amount.
     * The frontend should delay its UI events (highlights) by this value.
     * 
     * Calculated as: (backendEpochAbsoluteSec - frontendStartTimeSec)
     * where frontendStartTimeSec is derived from the first voice's relative time = 0.
     */
    val offsetSec: Double,
) : Feedback
```

### 2. `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

**Emit `PlaybackLatency` feedback when a playback epoch is first recorded.**

In the `scheduleVoice()` method, find the block where the epoch is auto-registered:

```kotlin
// Auto-register playback epoch on first voice
if (pid !in playbackEpochs) {
    val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
    playbackEpochs[pid] = nowSec
}
```

**After** recording the epoch, send the feedback. The offset to send is the epoch value itself (absolute backend time),
which the frontend will use to compute the delay. But actually, what the frontend needs is simpler: **how many seconds
behind is the backend compared to the frontend's wall clock at the moment the first voice was created?**

The frontend records `startTimeMs` (wall-clock when playback began). The first `ScheduleVoice` has relative time ~0.0.
By the time the backend receives it, some time has passed (postMessage latency). The epoch `nowSec` is in backend-local
time. The frontend needs to know: "when I think time=0 is, how much later does the backend think time=0 is?"

**The simplest approach:** Send the backend's absolute epoch time back. The frontend already knows its own
`startTimeMs`. It computes `backendEpochMs - frontendStartTimeMs = latencyMs`.

But wait — backend and frontend have **different clocks** (that's the whole reason we went to relative times). We can't
directly compare them.

**Better approach:** The `ScheduledVoice.startTime` is relative (seconds since playback start). The first voices have
startTime ≈ 0.0. The backend epoch is recorded in backend-absolute time. The offset that matters for the frontend is: *
*how much wall-clock time elapsed between the frontend creating the event and the backend recording the epoch.**

Since we can't compare clocks, we use a different strategy: **measure the round-trip implicitly.** The frontend sends
voices with relative times. The first voice has `startTime ≈ 0.0`. The frontend records `startTimeMs` (wall-clock) when
playback began. The backend records `epochSec` when it first sees a voice. The backend doesn't know frontend wall-clock,
but it CAN send back its own `epochSec` (backend-absolute).

Actually, the cleanest approach is: **the backend just sends the elapsed backend time since startup when the epoch was
recorded.** The frontend then knows: "the backend was at X seconds of audio time when it first saw my voices." Combined
with the frontend's own elapsed time, this gives the latency.

**Even simpler:** The backend sends back just the `playbackId` and the `offsetSec` = the number of backend-seconds that
had elapsed since `backendStartTimeSec` when the epoch was created. This is `epochSec - backendStartTimeSec`, i.e.,
`lastProcessedFrame / sampleRate`. The frontend can compare this with its own elapsed time to get the delta.

**Actually, the simplest of all:** Just send the **epoch as seconds-since-backend-start** back. The frontend knows its
own seconds-since-start. The difference is the latency.

Here's the modified `scheduleVoice()`:

```kotlin
fun scheduleVoice(voice: ScheduledVoice) {
    val pid = voice.playbackId

    // Auto-register playback epoch on first voice
    if (pid !in playbackEpochs) {
        // Record "now" in backend time as this playback's epoch
        val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
        playbackEpochs[pid] = nowSec

        // Report the backend's elapsed time at epoch to the frontend
        // The frontend can compare with its own elapsed time to derive latency.
        val backendElapsedSec = lastProcessedFrame.toDouble() / options.sampleRate.toDouble()
        options.commLink.feedback.send(
            KlangCommLink.Feedback.PlaybackLatency(
                playbackId = pid,
                offsetSec = backendElapsedSec,
            )
        )
    }

    // Schedule directly — no offset adjustment needed.
    scheduled.push(voice)

    // Prefetch sound samples (unchanged)
    // ... rest stays the same
}
```

Wait — `backendElapsedSec` is seconds since the backend audio engine started, NOT since this playback started. That's
not directly useful to the frontend.

**Let me rethink.** What the frontend actually needs is simple: **a delay value to add to its `setTimeout` calculations
so highlights match audio.**

The real latency has these components:

1. Time for `control.send()` → `postMessage` → worklet receives the message
2. Time from worklet receiving the voice → audio actually playing from the speaker

Component (1) is the transport latency. Component (2) is the audio buffer latency.

For **JVM**, both are ~0. For **JS**, (1) is the `postMessage` + event loop delay, and (2) is the Web Audio
`baseLatency` + `outputLatency`.

**Revised, cleaner approach:** Forget about trying to compute latency from clock differences. Instead, use a **ping-pong
measurement:**

The frontend sends a timestamp with the first `ScheduleVoice` (or a separate ping). The backend, upon receiving it,
reads its own clock and sends back the delta. But we already removed the Synchronize command...

**Final approach (pragmatic and clean):**

Since we switched to relative times, the frontend's `startTimeMs` marks "playback time 0" in wall-clock. The backend's
epoch marks "playback time 0" in backend-clock. The difference between when the frontend set `startTimeMs` and when the
backend recorded the epoch IS the transport latency.

To measure this without comparing clocks: **re-introduce a lightweight timestamp in `ScheduleVoice` or in a
new `Cmd.Ping`.**

Actually, the cleanest way with minimal changes:

**Add a `frontendTimeSec` field to `ScheduledVoice`** — no, that pollutes every voice.

**Better: Add a `Cmd.Ping` that the frontend sends right before the first voices.** The backend responds with
`Feedback.PlaybackLatency` containing the measured delta.

But we *just removed* `Cmd.Synchronize`... and now we're adding something similar? 😄

**OK, here's the truly simplest approach that works:**

The frontend knows its `startTimeMs`. When the backend records the epoch, it sends back a `PlaybackLatency` with the *
*backend's current KlangTime in milliseconds**. The frontend then computes:

```
latencyMs = backendTimeMs - frontendStartTimeMs
```

Both clocks use `Date.now()` as their base epoch (check the JS `KlangTime` implementations — both `MainThreadTimeSource`
and `AudioWorkletTimeSource` seed from `Date.now()`). So they share the same epoch! The difference is just that the
worklet's clock advances by frame-counting and may lag behind. But `Date.now()` at the moment of recording is
comparable.

So: **the backend sends its `Date.now()`-based `klangTime.internalMsNow()` when it records the epoch.** The frontend
compares with its own `startTimeMs` (also `Date.now()`-based). Done.

---

Here's the final, clean plan:

### 2. `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

**Add a `klangTimeMs` supplier to `Options`** so VoiceScheduler can read the current backend KlangTime:

```kotlin
class Options(
    val commLink: KlangCommLink.BackendEndpoint,
    val sampleRate: Int,
    val blockFrames: Int,
    val oscillators: Oscillators,
    val orbits: Orbits,
    /** Supplier for current backend time in milliseconds (from KlangTime) */
    val timeMs: () -> Double = { 0.0 },
) {
    val sampleRateDouble = sampleRate.toDouble()
}
```

**In `scheduleVoice()`, send `PlaybackLatency` when the epoch is recorded:**

```kotlin
if (pid !in playbackEpochs) {
    val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
    playbackEpochs[pid] = nowSec

    // Send the backend's current wall-clock time to the frontend.
    // Both clocks are seeded from Date.now(), so the frontend can compute:
    //   latencyMs = backendTimestampMs - frontendStartTimeMs
    options.commLink.feedback.send(
        KlangCommLink.Feedback.PlaybackLatency(
            playbackId = pid,
            backendTimestampMs = options.timeMs(),
        )
    )
}
```

**Update `PlaybackLatency` accordingly** (see step 1 — use `backendTimestampMs: Double` instead of `offsetSec`).

### 1. `audio_bridge/src/commonMain/kotlin/infra/KlangCommLink.kt` (revised)

```kotlin
@Serializable
@SerialName("playback-latency")
data class PlaybackLatency(
    override val playbackId: String,
    /**
     * The backend's KlangTime.internalMsNow() at the moment the playback epoch was recorded.
     * Both frontend and backend KlangTime are seeded from Date.now(), so the frontend
     * can compute transport latency as: backendTimestampMs - frontendStartTimeMs
     */
    val backendTimestampMs: Double,
) : Feedback
```

### 3. `audio_be/src/jsMain/kotlin/worklet/KlangAudioWorklet.kt`

**Pass `klangTime` into `VoiceScheduler.Options`:**

In the `Ctx` class, update the `VoiceScheduler` construction:

```kotlin
val voices = VoiceScheduler(
    VoiceScheduler.Options(
        commLink = commLink.backend,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        oscillators = oscillators(sampleRate = sampleRate),
        orbits = orbits,
        timeMs = { klangTime.internalMsNow() },
    )
)
```

### 4. `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt`

**Pass `klangTime` into `VoiceScheduler.Options`:**

Currently `klangTime` is created inside `run()`. Move it to be accessible earlier, or pass it in during `VoiceScheduler`
construction. Since `VoiceScheduler` is created at class level, the simplest approach is to create `klangTime` at class
level too:

```kotlin
private val klangTime = KlangTime.create()

val voices = VoiceScheduler(
    VoiceScheduler.Options(
        commLink = commLink,
        sampleRate = sampleRate,
        blockFrames = blockSize,
        oscillators = oscillators(sampleRate),
        orbits = orbits,
        timeMs = { klangTime.internalMsNow() },
    )
)
```

Then in `run()`, remove the local `val klangTime = KlangTime.create()` and use `this.klangTime` instead.

### 5. `strudel/src/commonMain/kotlin/StrudelPlayback.kt`

**Add a field to store the measured latency:**

```kotlin
// ===== Latency Compensation =====
/** Measured transport latency in milliseconds. Applied to VoicesScheduled signal times. */
private var backendLatencyMs: Double = 0.0
```

**In `processFeedbackEvents()`, handle the new feedback type:**

```kotlin
private fun processFeedbackEvents() {
    while (true) {
        val evt = feedback.receive() ?: break

        if (evt.playbackId != playbackId) continue

        when (evt) {
            is KlangCommLink.Feedback.RequestSample -> {
                requestAndSendSample(evt.req)
            }
            is KlangCommLink.Feedback.UpdateCursorFrame -> {
                // Ignore - event-fetcher is now autonomous
            }
            is KlangCommLink.Feedback.PlaybackLatency -> {
                // Compute transport latency:
                // Both KlangTime clocks are seeded from Date.now(), so they share the same epoch.
                // backendTimestampMs is when the backend first saw our voices.
                // startTimeMs is when we started this playback.
                // The difference is how long it took for our voices to reach the backend.
                backendLatencyMs = evt.backendTimestampMs - startTimeMs
                // Clamp to reasonable range (0 to 500ms). Negative means clock skew, treat as 0.
                backendLatencyMs = backendLatencyMs.coerceIn(0.0, 500.0)
            }
        }
    }
}
```

**In `queryEvents()`, apply the latency to the `VoicesScheduled` signal times.**

Find where `VoicesScheduled` voice events are created (inside the `voiceEvents.add(...)` block). Shift the times by
`backendLatencyMs`:

```kotlin
val latencyOffsetSec = backendLatencyMs / 1000.0

// In the voiceEvents list building, when creating VoiceEvent objects:
KlangPlaybackSignal.VoicesScheduled.VoiceEvent(
    startTime = absoluteStartTime + latencyOffsetSec,
    endTime = absoluteEndTime + latencyOffsetSec,
    sourceLocations = event.sourceLocations,
)
```

This shifts highlights to fire later, matching when the audio actually plays.

**IMPORTANT:** The `ScheduledVoice` times sent to the backend remain unchanged (relative, no latency applied). Only the
UI signal times are shifted.

### 6. `src/jsMain/kotlin/pages/DashboardPage.kt`

**No changes needed.** The `scheduleHighlight` method already uses `event.startTimeMs - Date.now()` to compute the
delay. Since `startTimeMs` is now shifted forward by the latency, the highlights will naturally fire later. ✅

---

## Summary of Changes

| File                                | Change                                                                                                                                             |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `audio_bridge/.../KlangCommLink.kt` | Add `Feedback.PlaybackLatency(playbackId, backendTimestampMs)`                                                                                     |
| `audio_be/.../VoiceScheduler.kt`    | Add `timeMs` lambda to `Options`. Send `PlaybackLatency` feedback when epoch is first recorded.                                                    |
| `audio_be/.../KlangAudioWorklet.kt` | Pass `{ klangTime.internalMsNow() }` as `timeMs` to `VoiceScheduler.Options`                                                                       |
| `audio_be/.../JvmAudioBackend.kt`   | Move `klangTime` to class level. Pass `{ klangTime.internalMsNow() }` as `timeMs` to `VoiceScheduler.Options`                                      |
| `strudel/.../StrudelPlayback.kt`    | Add `backendLatencyMs` field. Handle `PlaybackLatency` in `processFeedbackEvents()`. Apply latency offset to `VoicesScheduled` signal event times. |
| `src/.../DashboardPage.kt`          | **No changes** — automatically benefits from shifted signal times                                                                                  |

## Why This Works

- Both `MainThreadTimeSource` and `AudioWorkletTimeSource` in `KlangTime` (JS) are seeded from `Date.now()` at creation
  time. They share the same base epoch.
- The delta `backendTimestampMs - frontendStartTimeMs` captures the real transport delay: `postMessage` transit + any
  worklet scheduling delay.
- On JVM, the delay will be ~0ms (same process, `KlangTime` is a singleton), so no visible change.
- On JS, the delay will be 10–50ms typically, which is exactly the amount highlights are currently "too early."

## Build & Test

```shell script
./gradlew build
```

Manual test:

1. Play a pattern with percussive samples (e.g., `sound("bd hh sd oh")`)
2. Watch highlights — they should now flash in sync with the audio, not before it
3. On JVM, behavior should be unchanged (latency ≈ 0)
