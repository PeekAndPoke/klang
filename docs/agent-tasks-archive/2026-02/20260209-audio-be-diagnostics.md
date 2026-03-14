Here is a detailed implementation plan for adding backend diagnostics reporting.

### **Plan: Backend Audio Diagnostics Implementation**

**Objective**: Implement a mechanism for the audio backend to report real-time performance and state diagnostics to the
frontend at a rate of ~20Hz (every 50ms).

#### **1. Define Diagnostics Protocol (`KlangCommLink.kt`)**

Modify the communication protocol to include a new feedback message structure for diagnostics.

* **File**: `audio_bridge/src/commonMain/kotlin/io/peekandpoke/klang/audio_bridge/infra/KlangCommLink.kt`
* **Action**: Update `KlangCommLink.Feedback` sealed interface.
* **Change Details**:
    * Add a `Diagnostics` data class inside `Feedback` with the `@Serializable` and `@SerialName("diagnostics")`
      annotations.
    * Define the payload:
        * `playbackId`: String (use "global" for system-wide stats).
        * `renderHeadroom`: Double (1.0 = idle, 0.0 = full load, <0.0 = overload).
        * `activeVoiceCount`: Int (number of currently playing voices).
        * `orbits`: List`<OrbitState>`.
    * Define `OrbitState` data class nested within `Diagnostics`:
        * `id`: Int (the orbit number).
        * `active`: Boolean (whether voices are currently routed to it).

#### **2. Expose Orbit State (`Orbits.kt`)**

The `Orbits` class needs to provide a way to inspect which orbits are currently initialized so the scheduler can report
on them.

* **File**: `audio_be/src/commonMain/kotlin/io/peekandpoke/klang/audio_be/orbits/Orbits.kt`
* **Action**: Add a property to access allocated orbit keys.
* **Change Details**:
    * Add `val allocatedIds: Set<Int>` property.
    * Implement getter to return `orbits.keys`.

#### **3. Implement Measurement & Reporting Logic (`VoiceScheduler.kt`)**

Update the main processing loop to measure execution time and dispatch the diagnostics message periodically.

* **File**: `audio_be/src/commonMain/kotlin/io/peekandpoke/klang/audio_be/voices/VoiceScheduler.kt`
* **Action**: Modify the `process` method to calculate headroom and send feedback.
* **Change Details**:
    * **State Variables**:
        * Add `private var lastDiagnosticsTimeMs: Double = 0.0` to track reporting intervals.
        * Add `private var minHeadroom: Double = 1.0` to capture the worst-case performance between reports.
    * **Timing Logic (inside `process`)**:
        * Capture `startMs` (using `options.timeMs()`) at the very beginning of the function.
        * Perform existing rendering logic (promote scheduled, render loop).
        * Capture `endMs` at the end of the function.
    * **Headroom Calculation**:
        * Calculate `durationMs = endMs - startMs`.
        * Calculate `blockDurationMs = (blockFrames / sampleRate) * 1000.0`.
        * Calculate `headroom = 1.0 - (durationMs / blockDurationMs)`.
        * Update `minHeadroom` if the current `headroom` is lower than the stored value.
    * **Reporting Logic**:
        * Check if `endMs - lastDiagnosticsTimeMs > 50.0` (approx 20Hz).
        * If true:
            * Collect active orbit IDs from the `active` voice list: `active.map { it.orbitId }.toSet()`.
            * Map all `allocatedIds` from `options.orbits` to `OrbitState` objects, setting `active = true` if the ID is
              in the active set.
            * Construct the `KlangCommLink.Feedback.Diagnostics` message.
            * Send message via `options.commLink.feedback.send()`.
            * Update `lastDiagnosticsTimeMs = endMs`.
            * Reset `minHeadroom = 1.0` for the next interval.

Here are the changes to implement the diagnostics reporting.

I will add a `Diagnostics` message to the `KlangCommLink`, expose allocated orbits in `Orbits`, and then implement the
timing and reporting logic in `VoiceScheduler`.

### 1. Update `KlangCommLink`

I'll add the `Diagnostics` data class to the `Feedback` interface. This payload includes the headroom, active voice
count, and the state of each orbit.

```kotlin
// ... existing code ...
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

            @Serializable
            @SerialName("diagnostics")
            data class Diagnostics(
                override val playbackId: String,
                val renderHeadroom: Double,
                val activeVoiceCount: Int,
                val orbits: List<OrbitState>,
            ) : Feedback {
                @Serializable
                data class OrbitState(
                    val id: Int,
                    val active: Boolean,
                )
            }
        }

        /** Frontend to backend buffer */
        private val controlBuffer = KlangRingBuffer<Cmd>(capacity)
// ... existing code ...
```

### 2. Update `Orbits`

I need to expose the IDs of the currently allocated orbits so the scheduler can query them.

```kotlin
// ... existing code ...
    class Orbits(
        maxOrbits: Int = 16,
        private val blockFrames: Int,
        private val sampleRate: Int,
    ) {
        private val maxOrbits = maxOrbits.coerceIn(1, 32)
        private val orbits = mutableMapOf<Int, Orbit>()
        private var cleanupIndex = 0

        /**
         * Get all currently allocated orbit IDs.
         */
        val allocatedIds: Set<Int> get() = orbits.keys

        /**
         * Clear all orbits
         */
        fun clearAll() {
// ... existing code ...
```

### 3. Update `VoiceScheduler`

This is where the main logic resides. I'll add timing measurement around the processing loop, calculate the "headroom",
and send the diagnostics roughly 20 times per second (every ~50ms).

I'm using `playbackId = "global"` for the diagnostics message since these stats describe the entire backend state, not
just a specific playback.

```kotlin
// ... existing code ...
        // Context reused per block
        private val ctx = Voice.RenderContext(
            orbits = options.orbits,
            sampleRate = options.sampleRate,
            blockFrames = options.blockFrames,
            voiceBuffer = voiceBuffer,
            freqModBuffer = freqModBuffer
        )

        // Diagnostics state
        private var lastDiagnosticsTimeMs = 0.0
        private var minHeadroom = 1.0

        fun VoiceData.isOscillator() = options.oscillators.isOsc(sound)

        fun VoiceData.isSampleSound() = !isOscillator()
// ... existing code ...
        fun cleanup(playbackId: String) {
            playbackEpochs.remove(playbackId)
        }

        fun scheduleVoice(voice: ScheduledVoice) {
            val pid = voice.playbackId
// ... existing code ...
        }

        fun process(cursorFrame: Long) {
            val startMs = options.timeMs()

            lastProcessedFrame = cursorFrame
            val blockEnd = cursorFrame + options.blockFrames

            // println("active voices: ${active.size}")

            // 1. Promote scheduled to active
            promoteScheduled(cursorFrame, blockEnd)

            // 2. Prepare Context
            ctx.blockStart = cursorFrame

            // 3. Render Loop
            var i = 0
            while (i < active.size) {
                val voice = active[i]

                // Delegate logic to the voice itself
                val isAlive = voice.render(ctx)

                if (isAlive) {
                    i++
                } else {
                    // Swap-remove for performance
                    if (i < active.size - 1) {
                        active[i] = active.last()
                    }
                    active.removeLast()
                }
            }

            // 4. Diagnostics & Headroom
            val endMs = options.timeMs()
            val durationMs = endMs - startMs
            // Calculate max available time for this block in ms
            val blockDurationMs = (options.blockFrames.toDouble() / options.sampleRateDouble) * 1000.0

            // 1.0 = all time left, 0.0 = no time left, < 0.0 = glitching
            val headroom = 1.0 - (durationMs / blockDurationMs)

            // Track minimum headroom (worst case) since last report to catch glitches
            if (headroom < minHeadroom) {
                minHeadroom = headroom
            }

            // Send diagnostics approx 20 times per second (every 50ms)
            if (endMs - lastDiagnosticsTimeMs > 50.0) {
                lastDiagnosticsTimeMs = endMs

                // Determine which orbits are currently active (have voices feeding them)
                val activeOrbitIds = active.map { it.orbitId }.toSet()

                val orbitStates = options.orbits.allocatedIds.map { id ->
                    KlangCommLink.Feedback.Diagnostics.OrbitState(
                        id = id,
                        active = id in activeOrbitIds
                    )
                }

                options.commLink.feedback.send(
                    KlangCommLink.Feedback.Diagnostics(
                        playbackId = "global", // System-wide diagnostics
                        renderHeadroom = minHeadroom,
                        activeVoiceCount = active.size,
                        orbits = orbitStates
                    )
                )

                // Reset min headroom for next interval
                minHeadroom = 1.0
            }
        }

        private fun promoteScheduled(nowFrame: Long, blockEnd: Long) {
            val blockEndSec = backendStartTimeSec + (blockEnd.toDouble() / options.sampleRate.toDouble())
// ... existing code ...
```
