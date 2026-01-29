# Audio Backend - Pitch Envelope Wiring Instructions

This plan details how to wire the pitch envelope data (already present in `VoiceData`) into the DSP engine to modulate pitch over time.

## 🛠️ Task 1: Update `Voice` Interface

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

1.  **Define `PitchEnvelope` Class**:
    Inside the `Voice` interface (nested class), define the data structure for the resolved pitch envelope.
    ```kotlin
    class PitchEnvelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val releaseFrames: Double,
        val amount: Double, // In semitones
        val curve: Double,
        val anchor: Double, // 0.0 .. 1.0 (usually 0.0)
    )
    ```

2.  **Add Property**:
    Add the nullable property to the `Voice` interface definition.
    ```kotlin
    val pitchEnvelope: PitchEnvelope?
    ```

---

## 🛠️ Task 2: Update `VoiceScheduler`

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

In the `makeVoice` function:

1.  **Extract & Create `PitchEnvelope`**:
    Locate where other components (vibrato, etc.) are created. Add logic to instantiate `PitchEnvelope` if `pEnv` (amount) is non-zero.
    ```kotlin
    // Pitch Envelope
    val pEnvAmount = data.pEnv ?: 0.0
    val pitchEnvelope = if (pEnvAmount != 0.0) {
        Voice.PitchEnvelope(
            attackFrames = (data.pAttack ?: 0.0) * sampleRate,
            decayFrames = (data.pDecay ?: 0.0) * sampleRate,
            releaseFrames = (data.pRelease ?: 0.0) * sampleRate,
            amount = pEnvAmount,
            curve = data.pCurve ?: 1.0, 
            anchor = data.pAnchor ?: 0.0
        )
    } else null
    ```

2.  **Update Constructors**:
    Pass `pitchEnvelope` to the `SynthVoice(...)` and `SampleVoice(...)` constructor calls.

---

## 🛠️ Task 3: Update Voice Implementations

**Files:**
- `audio_be/src/commonMain/kotlin/voices/SynthVoice.kt`
- `audio_be/src/commonMain/kotlin/voices/SampleVoice.kt`

**Action**:
Update the primary constructor in **both files** to accept `pitchEnvelope` and override the property from the interface.

```kotlin
// Example for SynthVoice.kt (do same for SampleVoice.kt)
class SynthVoice(
    // ... existing params ...
    override val vibrato: Voice.Vibrato,
    override val pitchEnvelope: Voice.PitchEnvelope?, // <--- ADD THIS
    override val filter: AudioFilter,
    // ...
) : Voice { ... }
```

---

## 🛠️ Task 4: Implement DSP Logic in `common.kt`

**File:** `audio_be/src/commonMain/kotlin/voices/common.kt`

**Action**:
Rewrite the `fillPitchModulation` function to include the pitch envelope calculation.

**Logic Requirements:**
1.  **Check Existence**: If `vibrato`, `accelerate`, AND `pitchEnvelope` are all inactive, return `null` (optimization).
2.  **Envelope State Machine**:
    For each sample frame:
    *   Calculate `relPos` (frames since voice start).
    *   **Attack**: If `relPos < attackFrames`: Interpolate from `anchor` to `1.0`.
    *   **Decay**: If `relPos < attack + decayFrames`: Interpolate from `1.0` down to `anchor`.
    *   **Sustain**: Otherwise, hold at `anchor`.
    *   *(Note: Strudel pitch envelopes typically decay to the anchor point and stay there).*
3.  **Frequency Multiplier**:
    *   Calculate `semitones = level * amount`.
    *   `multiplier = 2.0.pow(semitones / 12.0)`.
4.  **Combine**:
    *   Result = `vibratoMod * accelerateMod * pitchEnvMod`.

**Implementation Reference:**

```kotlin
fun Voice.fillPitchModulation(ctx: Voice.RenderContext, offset: Int, length: Int): DoubleArray? {
    val vib = vibrato
    val accel = accelerate.amount
    val pEnv = pitchEnvelope
    
    val hasVibrato = vib.depth > 0.0
    val hasAccelerate = accel != 0.0
    val hasPitchEnv = pEnv != null

    if (!hasVibrato && !hasAccelerate && !hasPitchEnv) return null

    val out = ctx.freqModBuffer
    val totalFrames = (endFrame - startFrame).toDouble()
    
    var phase = vib.phase
    val phaseInc = (TWO_PI * vib.rate) / ctx.sampleRate

    for (i in 0 until length) {
        val idx = offset + i
        val absFrame = ctx.blockStart + idx
        val relPos = (absFrame - startFrame).toDouble()

        // 1. Vibrato
        val vibMod = if (hasVibrato) {
            val mod = 1.0 + (sin(phase) * vib.depth)
            phase += phaseInc
            mod
        } else 1.0

        // 2. Accelerate
        val accelMod = if (hasAccelerate) {
            val progress = relPos / totalFrames
            2.0.pow(accel * progress)
        } else 1.0

        // 3. Pitch Envelope
        val pEnvMod = if (hasPitchEnv && pEnv != null) {
            var envLevel = pEnv.anchor
            
            if (relPos < pEnv.attackFrames) {
                // Attack: Anchor -> 1.0
                val progress = relPos / pEnv.attackFrames
                envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress
            } else if (relPos < (pEnv.attackFrames + pEnv.decayFrames)) {
                // Decay: 1.0 -> Anchor
                val decayProgress = (relPos - pEnv.attackFrames) / pEnv.decayFrames
                envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress
            } 
            // Else Sustain at Anchor
            
            // Convert semitone offset to frequency ratio
            2.0.pow((pEnv.amount * envLevel) / 12.0)
        } else 1.0

        out[idx] = vibMod * accelMod * pEnvMod
    }

    vib.phase = phase
    return out
}
```
