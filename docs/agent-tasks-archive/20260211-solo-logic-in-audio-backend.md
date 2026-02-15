Here is the detailed implementation plan to add smooth "Solo" transitions to the audio backend.

### Goal

Implement a smooth volume transition when the "solo" state changes. Instead of muting non-solo voices abruptly, the
volume should fade in/out over a short duration (e.g., 100ms) using a cubic easing function. The logic will reside in
the `VoiceScheduler` (backend) to ensure accurate, continuous processing regardless of event scheduling timing.

### 1. Revert Frontend Logic

Remove any experimental smoothing logic from the frontend `StrudelPlayback` to ensure a clean slate. The frontend should
simply pass the `solo` flag in the events.

* **File:** `strudel/src/commonMain/kotlin/strudel/StrudelPlayback.kt`
* **Action:**
    * Remove `ValueRamp` usage and `applySoloLogicSmoothed`.
    * Revert `queryEvents` to simply pass `rawEvents` without modification (assuming `solo` flag is naturally in the
      event data).

### 2. Update Voice Interface

We need a way to modulate the gain of an active voice dynamically.

* **File:** `klang/src/commonMain/kotlin/audio_be/voices/Voice.kt` (or wherever `Voice` interface is defined)
* **Action:** Add a method to the `Voice` interface.

```kotlin
interface Voice {
    // ... existing methods ...

    /**
     * Sets a temporary gain scaling factor (e.g. for solo/mute logic).
     * This should be multiplied with the voice's base gain during rendering.
     * Default implementation can be empty if not all voices support it.
     */
    fun setSoloMuteGain(gain: Double) {}
}
```

* **File:** `klang/src/commonMain/kotlin/audio_be/voices/SynthVoice.kt` and `SampleVoice.kt`
* **Action:** Implement `setSoloMuteGain`.
    * Add a private field `private var soloMuteGain: Double = 1.0`
    * Override `setSoloMuteGain(gain: Double) { soloMuteGain = gain }`
    * In the `render()` method, ensure `soloMuteGain` is applied to the final output volume (e.g.,
      `output * gain * soloMuteGain`).

### 3. Implement Smoothing Logic in VoiceScheduler

The core logic moves to `VoiceScheduler`.

* **File:** `klang/src/commonMain/kotlin/audio_be/voices/VoiceScheduler.kt`
* **Action:**
    1. **Add `ValueRamp` and `Ease` classes**: Add these utility classes (private or internal) to `VoiceScheduler.kt` (
       or a utility file) to handle the smooth transition.
        * `ValueRamp` tracks current value, target value, and duration.
        * `Ease.InOutCubic` provides the curve.
    2. **Update `ActiveVoice`**: Add `isSolo: Boolean` to the `ActiveVoice` data class.

```kotlin
private data class ActiveVoice(
    val voice: Voice,
    val playbackId: String,
    val isSolo: Boolean // New field
)
```

    3.  **Initialize `ActiveVoice` correctly**: In `process`, when creating `ActiveVoice` from `ScheduledVoice`, set `isSolo = head.data.solo == true`.
    4.  **Add `soloMuteRamp`**: Add a private property `private val soloMuteRamp = ValueRamp(initialValue = 1.0, duration = 0.1, ease = Ease.InOutCubic)`.
    5.  **Update `process()` loop**:
        *   Before the render loop, determine the target gain.

```kotlin
val isSoloActive = active.any { it.isSolo }
val targetGain = if (isSoloActive) 0.05 else 1.0 // 0.05 for background, 1.0 for normal
```

        *   Step the ramp:

```kotlin
val blockDurationSec = options.blockFrames.toDouble() / options.sampleRateDouble
val currentSoloGain = soloMuteRamp.step(targetGain, blockDurationSec)
```

        *   Inside the render loop (`while (i < active.size)`):

```kotlin
if (!activeVoice.isSolo) {
    activeVoice.voice.setSoloMuteGain(currentSoloGain)
} else {
    activeVoice.voice.setSoloMuteGain(1.0) // Solo voices are always full volume
}
```

### 4. Verification

* **Test Case 1 (No Solo)**: Play a pattern `s("bd hh")`. Verify normal volume.
* **Test Case 2 (Solo Active)**: Play `s("bd hh").solo()`. Both are solo, so `isSoloActive` is true, but since they are
  solo voices, `setSoloMuteGain(1.0)` is called. Volume should be normal.
* **Test Case 3 (Mixed)**: Play `stack(s("bd").solo(), s("hh"))`.
    * `bd` is solo. `hh` is not.
    * `isSoloActive` becomes true.
    * `bd` gets gain 1.0.
    * `hh` gets gain 0.05 (fading down smoothly).
* **Test Case 4 (Transition)**: Switch from `s("bd hh")` to `s("bd").solo()`.
    * Verify `hh` fades out smoothly instead of cutting off abruptly.

### 5. Code Structure for `ValueRamp` & `Ease` (Reference)

Include these helper classes in the implementation:

```kotlin
private class ValueRamp(
    initialValue: Double,
    private val duration: Double = 0.1,
    private val ease: Ease.Fn = Ease.Linear,
) {
    var current: Double = initialValue
        private set
    // ... (state: startValue, targetValue, progress) ...
    // ... (step method) ...
}

private object Ease {
    interface Fn {
        operator fun invoke(progress: Double): Double
    }

    val InOutCubic = object : Fn { /* ... implementation ... */ }
}
```
