Here is a detailed plan and the corresponding code implementation to achieve "instant voice updates" when changing
tempo (cps) or updating the pattern.

### Plan

The goal is to clear any voices scheduled in the future (to prevent rhythm mismatch or old pattern playback) and
immediately schedule new voices starting from the current playback position.

1. **Shared Communication (`KlangCommLink`)**:
    * Introduce a new command `ClearScheduled(playbackId)` in `KlangCommLink`. This command instructs the backend to
      discard any pending voices for the given playback session that haven't started playing yet.

2. **Audio Backend (`VoiceScheduler`, `KlangAudioWorklet`, `JvmAudioBackend`)**:
    * Implement `clearScheduled(playbackId)` in `VoiceScheduler`. This method will:
        * Remove all pending voices for the `playbackId` from the scheduling heap.
        * Clear the `playbackEpoch` for this ID. This forces the backend to establish a new time anchor ("epoch") upon
          receiving the next voice.
    * Wire up the command handler in both JS (`KlangAudioWorklet`) and JVM (`JvmAudioBackend`) backends.

3. **Frontend Logic (`StrudelPlayback`)**:
    * Add a `cycleOffset` state to track the accumulated cycle count when resetting time anchors.
    * **Tempo Change (`updateCyclesPerSecond`) & Pattern Update (`updatePattern`)**:
        * Calculate the current exact cycle position based on elapsed time and the *old* tempo.
        * Send the `ClearScheduled` command to the backend.
        * Update the `cycleOffset` to the current cycle count.
        * Reset `startTimeMs` to "now" and `localFrameCounter` to 0. This effectively performs a "transport handoff,"
          making the new tempo/pattern effective immediately from the current moment without discontinuity in the cycle
          counter.
        * Resume scheduling from this new anchor.
    * Adjust `queryEvents` to subtract `cycleOffset` from event times, ensuring the backend receives times relative to
      the new "now" anchor (which matches the backend's reset epoch).

### Implementation

```kotlin
// ... existing code ...
data class Cleanup(
    override val playbackId: String,
) : Cmd {
    companion object {
        const val SERIAL_NAME = "cleanup"
    }
}

data class ClearScheduled(
    override val playbackId: String,
) : Cmd {
    companion object {
        const val SERIAL_NAME = "clear-scheduled"
    }
}

data class ScheduleVoice(
    override val playbackId: String,
    val voice: ScheduledVoice,
// ... existing code ...
```

```kotlin
// ... existing code ...
fun clear() {
    scheduled.clear()
    active.clear()
    playbackEpochs.clear()
}

fun clearScheduled(playbackId: String) {
    val kept = mutableListOf<ScheduledVoice>()

    // Drain the heap
    while (scheduled.size() > 0) {
        scheduled.pop()?.let {
            if (it.playbackId != playbackId) kept.add(it)
        }
    }

    // Refill
    kept.forEach { scheduled.push(it) }

    // Clear epoch so it resets on next voice
    playbackEpochs.remove(playbackId)
}

fun addSample(msg: KlangCommLink.Cmd.Sample) {
    val req = msg.req
// ... existing code ...
```

```kotlin
// ... existing code ...
is KlangCommLink.Cmd.ScheduleVoice -> {
    ctx.voices.scheduleVoice(voice = cmd.voice)
}

is KlangCommLink.Cmd.ClearScheduled -> {
    ctx.voices.clearScheduled(cmd.playbackId)
}

is KlangCommLink.Cmd.Cleanup -> {
    ctx.voices.cleanup(cmd.playbackId)
}
// ... existing code ...
```

```kotlin
// ... existing code ...
is KlangCommLink.Cmd.ScheduleVoice -> {
    voices.scheduleVoice(voice = cmd.voice)
}

is KlangCommLink.Cmd.ClearScheduled -> {
    voices.clearScheduled(cmd.playbackId)
}

is KlangCommLink.Cmd.Cleanup -> {
    voices.cleanup(cmd.playbackId)
}
// ... existing code ...
```

```kotlin
// ... existing code ...
// ===== Playback Parameters =====
private var cyclesPerSecond: Double = 0.5
private var lookaheadSec: Double = 1.0
private var cycleOffset: Double = 0.0
private val secPerCycle get() = 1.0 / cyclesPerSecond

// ===== Fetcher State =====
// ... existing code ...
/**
 * Update the pattern being played
 */
fun updatePattern(pattern: StrudelPattern) {
    this.pattern = pattern

    // Re-anchor to current time to avoid drift and simplify logic
    val nowMs = klangTime.internalMsNow()
    val elapsedSec = (nowMs - startTimeMs) / 1000.0
    val currentCycles = elapsedSec * cyclesPerSecond + cycleOffset

    this.cycleOffset = currentCycles
    this.startTimeMs = nowMs
    this.localFrameCounter = 0L
    this.queryCursorCycles = currentCycles

    scope.launch(fetcherDispatcher) {
        control.send(KlangCommLink.Cmd.ClearScheduled(playbackId))
    }

    lookAheadForSampleSounds(queryCursorCycles, sampleSoundLookAheadCycles)
}

/**
 * Update the cycles per second (tempo)
 */
fun updateCyclesPerSecond(cps: Double) {
    // 1. Calculate current cycle position before changing tempo
    val nowMs = klangTime.internalMsNow()
    val elapsedSec = (nowMs - startTimeMs) / 1000.0
    val currentCycles = elapsedSec * cyclesPerSecond + cycleOffset

    // 2. Update parameters
    this.cyclesPerSecond = cps
    this.cycleOffset = currentCycles

    // 3. Reset anchors to "now"
    this.startTimeMs = nowMs
    this.localFrameCounter = 0L

    // 4. Reset cursor to continue from current position
    this.queryCursorCycles = currentCycles

    // 5. Clear backend to drop old scheduled events
    scope.launch(fetcherDispatcher) {
        control.send(KlangCommLink.Cmd.ClearScheduled(playbackId))
    }
}

override fun start() {
    start(Options())
    // ... existing code ...
    private fun queryEvents(from: Double, to: Double, sendEvents: Boolean): List<ScheduledVoice> {
        // Convert Double time to Rational for exact pattern arithmetic
        val fromRational = Rational(from)
// ... existing code ...
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackStartTimeSec = startTimeMs / 1000.0

        // Latency compensation for UI signals (shift highlights to match actual audio playback)
        val latencyOffsetSec = backendLatencyMs / 1000.0

        // Build voice signal events for callbacks (collected first to avoid blocking audio scheduling)
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            // Use whole for scheduling (complete event), not part (clipped portion)
            val timeSpan = event.whole
            val relativeStartTime = ((timeSpan.begin.toDouble() - cycleOffset) * secPerCycle)
            val duration = (timeSpan.duration * secPerCycle).toDouble()

            // Absolute times for UI callbacks (need wall-clock for setTimeout highlighting)
            val absoluteStartTime = playbackStartTimeSec + relativeStartTime
            val absoluteEndTime = absoluteStartTime + duration

            // Convert to VoiceData once (used by both ScheduledVoice and signal)
// ... existing code ...
            private fun requestNextCyclesAndAdvanceCursor() {
                // Use local frame counter for autonomous progression
                val nowFrame = localFrameCounter
                val nowSec = nowFrame.toDouble() / playerOptions.sampleRate.toDouble()
                // Calculate nowCycles including the offset
                val nowCycles = (nowSec / secPerCycle) + cycleOffset

                val targetCycles = nowCycles + (lookaheadSec / secPerCycle)

                // Fetch as many new cycles as needed
                while (queryCursorCycles < targetCycles) {
// ... existing code ...
```
