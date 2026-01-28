# Strudel Audio Effects - Reverb Implementation Plan

This document outlines the tasks required to complete the `Global Effects - Reverb` section of the Strudel Kotlin port.

## 🎯 Goal

Implement the missing Reverb effect parameters in the DSL, data structures, and audio bridge to match the original
Strudel specification.

**Missing Features:**

- `roomfade` (alias: `rfade`) - Reverb fade time
- `roomlp` (alias: `rlp`) - Reverb lowpass start frequency
- `roomdim` (alias: `rdim`) - Reverb lowpass frequency at -60dB
- `iresponse` (alias: `ir`) - Impulse response sample

---

## 🛠️ Implementation Steps

### 1. Data Structure Updates

#### A. Modify `StrudelVoiceData`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Add the following fields to the `StrudelVoiceData` data class and its `empty` companion object:

- `val roomFade: Double?`
- `val roomLp: Double?`
- `val roomDim: Double?`
- `val iResponse: String?` (likely a String for the IR name/path)

Update the `merge` function to include these new fields.

#### B. Modify `VoiceData` (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add the corresponding fields to the `VoiceData` data class and its `empty` companion object to ensure they are passed to
the audio engine:

- `val roomFade: Double?`
- `val roomLp: Double?`
- `val roomDim: Double?`
- `val iResponse: String?`

#### C. Update Mapping Logic

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Update the `toVoiceData()` function to map the new fields from `StrudelVoiceData` to `VoiceData`.

### 2. DSL Implementation

**File:** `strudel/src/commonMain/kotlin/lang/lang_effects.kt`

Implement the following DSL functions using the established patterns (`voiceModifier`, `applyNumericalParam`, etc.).

#### A. `roomfade` / `rfade`

- **Type:** Numerical
- **Field:** `roomFade`
- **Functions:**
    - `StrudelPattern.roomfade()`
    - `roomfade()` (standalone)
    - `String.roomfade()`
    - **Aliases:** `rfade` (pattern, standalone, string)

#### B. `roomlp` / `rlp`

- **Type:** Numerical
- **Field:** `roomLp`
- **Functions:**
    - `StrudelPattern.roomlp()`
    - `roomlp()` (standalone)
    - `String.roomlp()`
    - **Aliases:** `rlp` (pattern, standalone, string)

#### C. `roomdim` / `rdim`

- **Type:** Numerical
- **Field:** `roomDim`
- **Functions:**
    - `StrudelPattern.roomdim()`
    - `roomdim()` (standalone)
    - `String.roomdim()`
    - **Aliases:** `rdim` (pattern, standalone, string)

#### D. `iresponse` / `ir`

- **Type:** String (Control Pattern)
- **Field:** `iResponse`
- **Implementation Note:** Use `applyControlFromParams` (similar to `tremoloShape`) to handle string values.
- **Functions:**
    - `StrudelPattern.iresponse()`
    - `iresponse()` (standalone)
    - `String.iresponse()`
    - **Aliases:** `ir` (pattern, standalone, string)

### 3. Verification

- Create a new test file `strudel/src/commonTest/kotlin/lang/LangReverbSpec.kt`.
- Add tests to verify that:
    - The DSL functions correctly set the values in the pattern.
    - The aliases work as expected.
    - `toVoiceData()` correctly transfers the values.

---

## 📝 Code Snippets (Reference)

**Data Field Addition:**

```kotlin
// StrudelVoiceData.kt & VoiceData.kt
val roomFade: Double?,
val roomLp: Double?,
val roomDim: Double?,
val iResponse: String?,
```

**DSL Example (`roomfade`):**

```kotlin
private val roomFadeMutation = voiceModifier {
    copy(roomFade = it?.asDoubleOrNull())
}

private fun applyRoomFade(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = roomFadeMutation,
        getValue = { roomFade },
        setValue = { v, _ -> copy(roomFade = v) },
    )
}
// ... registration extensions ...
```

To ensure the Reverb implementation is fully functional in the audio engine, please follow these steps. This involves
wiring the new parameters through the audio backend (`Voice`, `VoiceScheduler`) and implementing the DSP logic in the
`Reverb` effect.

### 1. Update Voice Interface

We need to add the new reverb parameters to the `Voice.Reverb` data class so they can be passed from the scheduler to
the render context.

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

```kotlin
// ... existing code ...
class Delay(
    val amount: Double, // mix amount 0 .. 1
    val time: Double,
    val feedback: Double,
)

class Reverb(
    /** mix amount 0 .. 1 */
    val room: Double,
    /** room size 0 .. 1 */
    val roomSize: Double,
    /** reverb fade time (feedback) */
    val roomFade: Double? = null,
    /** lowpass filter cutoff */
    val roomLp: Double? = null,
    /** dim frequency (currently unused in simple model) */
    val roomDim: Double? = null,
    /** impulse response name */
    val iResponse: String? = null,
)

class Phaser(
    val rate: Double,
// ... existing code ...
```

### 2. Update Reverb DSP Logic

Implement the logic to use `roomFade` and `roomLp`.

* **`roomFade`**: If provided, it overrides `roomSize` for the feedback calculation.
* **`roomLp`**: If provided, it determines the damping factor based on the frequency relative to the Nyquist frequency.

**File:** `audio_be/src/commonMain/kotlin/effects/Reverb.kt`

```kotlin
// ... existing code ...
// Parameters
var roomSize: Double = 0.5 // 0.0 .. 1.0 (Feedback)
var damp: Double = 0.5     // 0.0 .. 1.0 (High frequency damping)
var width: Double = 1.0    // 0.0 .. 1.0 (Stereo width) -- Not strictly exposed but useful

// Extended Strudel Parameters
var roomFade: Double? = null
var roomLp: Double? = null
var roomDim: Double? = null
var iResponse: String? = null

fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
    val inL = input.left
    val inR = input.right
    val outL = output.left
    val outR = output.right

    // Calculate feedback and damping based on parameters
    // Typical Freeverb ranges: Feedback 0.7..0.98, Damping 0..0.4

    // Use roomFade for feedback if available, otherwise fallback to roomSize
    val effectiveSize = roomFade ?: roomSize
    val feedback = (effectiveSize * 0.28f) + 0.7f

    // Use roomLp to control damping if available
    // roomLp (Hz) -> damp (0..1)
    // High cutoff = low damping (bright)
    // Low cutoff = high damping (dull)
    val effectiveDamp = if (roomLp != null) {
        val nyquist = sampleRate / 2.0
        // Normalize freq to 0..1 and invert for damping
        val normalized = (roomLp!! / nyquist).coerceIn(0.0, 1.0)
        1.0 - normalized
    } else {
        damp
    }

    val damping = effectiveDamp * 0.4f

    for (i in 0 until length) {
        val inpL = inL[i]
// ... existing code ...
```

### 3. Update Orbit Initialization

Wire the parameters from the `Voice` object into the `Reverb` effect instance when the orbit is initialized.

**File:** `audio_be/src/commonMain/kotlin/orbits/Orbit.kt`

```kotlin
// ... existing code ...
// Reverb
// TODO: check the below ...
// Use room amount as the send level? Usually room=amount
// The mix is handled by how much we write to reverbSendBuffer
// But we can also modulate damping if needed.
reverb.roomSize = voice.reverb.roomSize.coerceIn(0.0, 1.0)
reverb.roomFade = voice.reverb.roomFade
reverb.roomLp = voice.reverb.roomLp
reverb.roomDim = voice.reverb.roomDim
reverb.iResponse = voice.reverb.iResponse

// Phaser
if (voice.phaser.depth > 0) {
    phaser.rate = voice.phaser.rate
// ... existing code ...
```

### 4. Update Voice Scheduler

Finally, map the incoming `VoiceData` (from the bridge) to the `Voice.Reverb` object in the scheduler.

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

```kotlin
// ... existing code ...
// Reverb
val reverb = Voice.Reverb(
    room = data.room ?: 0.0,
    // In Strudel, room size is between [0 and 10], so we need to normalize it
    // See https://strudel.cc/learn/effects/#roomsize
    roomSize = (data.roomSize ?: 0.0) / 10.0,
    roomFade = data.roomFade,
    roomLp = data.roomLp,
    roomDim = data.roomDim,
    iResponse = data.iResponse,
)

// Phaser
val phaser = Voice.Phaser(
    rate = data.phaser ?: 0.0,
// ... existing code ...
```
