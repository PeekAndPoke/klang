# Strudel Audio Effects - Dynamics Implementation Plan (DETAILED)

## 🎯 Overview

Implement three dynamics-related parameters for the Strudel Kotlin port:

- **`velocity`** - Volume scaling (0-1), multiplies with gain at the voice level
- **`postgain`** - Gain applied after voice processing, before mixing to orbit bus
- **`compressor`** - Dynamic range compression at the orbit level (threshold:ratio:knee:attack:release)

---

## 📋 Implementation Phases

### Phase 1: Data Structure Updates

#### Step 1.1: Update `StrudelVoiceData`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

**Location:** After line 25 (after `legato` field in the "Gain / Dynamics" section)

Add these fields:

```kotlin
// Gain / Dynamics
val gain: Double?,
val legato: Double?,
val velocity: Double?,    // NEW: Volume scaling (0-1), multiplies with gain
val postGain: Double?,    // NEW: Gain applied after voice processing
```

**Location:** Around line 202-285 in the `companion object empty`

Add to the `empty` companion object (after `legato = null,`):

```kotlin
legato = null,
velocity = null,     // NEW
postGain = null,     // NEW
```

**Location:** Around line 288-395 in the `merge()` function

Add to the `merge()` function (after `legato = other.legato ?: legato,`):

```kotlin
legato = other.legato ?: legato,
velocity = other.velocity ?: velocity,           // NEW
postGain = other.postGain ?: postGain,           // NEW
```

**Location:** In the `toVoiceData()` function (search for the VoiceData constructor call)

Add to the `toVoiceData()` mapping (after the gain field):

```kotlin
gain = gain,
velocity = velocity,         // NEW
postGain = postGain,         // NEW
```

#### Step 1.2: Update `VoiceData` (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add corresponding fields after `gain`:

```kotlin
val gain: Double?,
val velocity: Double?,
val postGain: Double?,
```

Update the `empty` companion object:

```kotlin
gain = null,
velocity = null,
postGain = null,
```

---

### Phase 2: DSL Implementation

#### Step 2.1: Implement `velocity()` function

**File:** `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`

**Location:** After the `gain()` implementation (around line 43)

**Code:**

```kotlin
// -- velocity() -------------------------------------------------------------------------------------------------------

private val velocityMutation = voiceModifier {
    copy(velocity = it?.asDoubleOrNull())
}

private fun applyVelocity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = velocityMutation,
        getValue = { velocity },
        setValue = { v, _ -> copy(velocity = v) },
    )
}

/** Modifies the velocity (volume scaling) of a pattern */
@StrudelDsl
val StrudelPattern.velocity by dslPatternExtension { p, args, /* callInfo */ _ -> applyVelocity(p, args) }

/** Creates a pattern with velocity */
@StrudelDsl
val velocity by dslFunction { args, /* callInfo */ _ -> args.toPattern(velocityMutation) }

/** Modifies the velocity of a pattern defined by a string */
@StrudelDsl
val String.velocity by dslStringExtension { p, args, callInfo -> p.velocity(args, callInfo) }

/** Alias for velocity */
@StrudelDsl
val StrudelPattern.vel by dslPatternExtension { p, args, callInfo -> p.velocity(args, callInfo) }

/** Alias for velocity */
@StrudelDsl
val vel by dslFunction { args, callInfo -> velocity(args, callInfo) }

/** Alias for velocity on a string */
@StrudelDsl
val String.vel by dslStringExtension { p, args, callInfo -> p.velocity(args, callInfo) }
```

#### Step 2.2: Implement `postgain()` function

**File:** `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`

**Location:** After the `velocity()` implementation

**Code:**

```kotlin
// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceModifier {
    copy(postGain = it?.asDoubleOrNull())
}

private fun applyPostgain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = postgainMutation,
        getValue = { postGain },
        setValue = { v, _ -> copy(postGain = v) },
    )
}

/** Modifies the post-gain (applied after voice processing) of a pattern */
@StrudelDsl
val StrudelPattern.postgain by dslPatternExtension { p, args, /* callInfo */ _ -> applyPostgain(p, args) }

/** Creates a pattern with post-gain */
@StrudelDsl
val postgain by dslFunction { args, /* callInfo */ _ -> args.toPattern(postgainMutation) }

/** Modifies the post-gain of a pattern defined by a string */
@StrudelDsl
val String.postgain by dslStringExtension { p, args, callInfo -> p.postgain(args, callInfo) }
```

#### Step 2.3: Implement `compressor()` function (String-based)

**File:** `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`

**Location:** After the `postgain()` implementation

**Note:** Compressor is an **orbit-level** effect, but the DSL parameter is still attached to voice data (similar to
`room`, `delay`, etc.) and gets applied at the orbit level during audio processing.

**Code:**

```kotlin
// -- compressor() -----------------------------------------------------------------------------------------------------

private val compressorMutation = voiceModifier { shape ->
    copy(compressor = shape?.toString())
}

private fun applyCompressor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyControlFromParams(args, compressorMutation) { src, ctrl ->
        src.copy(compressor = ctrl.compressor)
    }
}

/** Sets dynamic range compression parameters (threshold:ratio:knee:attack:release) */
@StrudelDsl
val StrudelPattern.compressor by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }

/** Sets dynamic range compression parameters (threshold:ratio:knee:attack:release) */
@StrudelDsl
val compressor by dslFunction { args, /* callInfo */ _ -> args.toPattern(compressorMutation) }

/** Sets dynamic range compression parameters on a string */
@StrudelDsl
val String.compressor by dslStringExtension { p, args, callInfo -> p.compressor(args, callInfo) }

/** Alias for compressor */
@StrudelDsl
val StrudelPattern.comp by dslPatternExtension { p, args, callInfo -> p.compressor(args, callInfo) }

/** Alias for compressor */
@StrudelDsl
val comp by dslFunction { args, callInfo -> compressor(args, callInfo) }

/** Alias for compressor on a string */
@StrudelDsl
val String.comp by dslStringExtension { p, args, callInfo -> p.compressor(args, callInfo) }
```

---

### Phase 3: Audio Engine Implementation

#### Step 3.1: Add compressor field to StrudelVoiceData

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

**Location:** After the `vowel` field (around line 196)

Add:

```kotlin
// Voice / Singing
/** Vowel formant filter (a, e, i, o, u) */
val vowel: String?,

// Dynamics / Compression
/** Dynamic range compression settings (threshold:ratio:knee:attack:release) */
val compressor: String?,
```

Update `empty` companion object:

```kotlin
vowel = null,
compressor = null,
```

Update `merge()` function:

```kotlin
vowel = other.vowel ?: vowel,
compressor = other.compressor ?: compressor,
```

Update `toVoiceData()` function:

```kotlin
vowel = vowel,
compressor = compressor,
```

#### Step 3.2: Add compressor field to VoiceData

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add after existing fields:

```kotlin
val compressor: String?,
```

Update `empty` companion object:

```kotlin
compressor = null,
```

#### Step 3.3: Implement velocity and postgain in VoiceScheduler

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

**Location:** Search for where gain is initialized from VoiceData

**Current pattern:**

```kotlin
val gain = data.gain ?: 1.0
```

**Update to:**

```kotlin
val baseGain = data.gain ?: 1.0
val velocity = data.velocity ?: 1.0
val gain = baseGain * velocity
val postGain = data.postGain ?: 1.0
```

Then pass both `gain` and `postGain` to the Voice constructors (SynthVoice and SampleVoice).

#### Step 3.4: Update Voice interface

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

**Location:** After the `pan` field (around line 125)

Add:

```kotlin
// Dynamics
val gain: Double
val pan: Double
val postGain: Double  // NEW: Applied after voice processing
```

#### Step 3.5: Update SynthVoice and SampleVoice

**Files:**

- `audio_be/src/commonMain/kotlin/voices/SynthVoice.kt`
- `audio_be/src/commonMain/kotlin/voices/SampleVoice.kt`

Add `postGain` parameter to both data classes (after `pan`):

```kotlin
override val pan: Double,
override val postGain: Double,
```

#### Step 3.6: Apply postgain in voice rendering

**Files:**

- `audio_be/src/commonMain/kotlin/voices/SynthVoice.kt`
- `audio_be/src/commonMain/kotlin/voices/SampleVoice.kt`

**Location:** In the `render()` function, after all processing is done but before effects

Find the section where the voice buffer is being prepared (after envelope and gain application).

**Add this code before copying to the orbit buffer:**

```kotlin
// Apply post-gain
if (postGain != 1.0) {
    for (i in 0 until actualFrames) {
        ctx.voiceBuffer[i] *= postGain
    }
}
```

#### Step 3.7: Create Compressor audio processor

**File:** `audio_be/src/commonMain/kotlin/effects/Compressor.kt` (NEW FILE)

**Code:**

```kotlin
package io.peekandpoke.klang.audio_be.effects

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic range compressor with configurable threshold, ratio, knee, attack, and release.
 *
 * This compressor reduces the dynamic range of audio by attenuating signals above the threshold.
 * The amount of attenuation is controlled by the ratio parameter.
 *
 * @param sampleRate Sample rate in Hz
 * @param thresholdDb Threshold in dB (default: -20.0). Signals above this are compressed.
 * @param ratio Compression ratio (default: 4.0). For example, 4:1 means 4dB input = 1dB output above threshold.
 * @param kneeDb Soft knee width in dB (default: 6.0). A wider knee makes the compression more gradual.
 * @param attackSeconds Attack time in seconds (default: 0.003). How quickly compression is applied.
 * @param releaseSeconds Release time in seconds (default: 0.1). How quickly compression is released.
 */
class Compressor(
    private val sampleRate: Int,
    thresholdDb: Double = -20.0,
    ratio: Double = 4.0,
    kneeDb: Double = 6.0,
    attackSeconds: Double = 0.003,
    releaseSeconds: Double = 0.1,
) {
    // Compressor parameters (all mutable for real-time changes)
    var thresholdDb: Double = thresholdDb
        set(value) {
            field = value
            updateCoefficients()
        }

    var ratio: Double = ratio.coerceAtLeast(1.0)
        set(value) {
            field = value.coerceAtLeast(1.0)
            updateCoefficients()
        }

    var kneeDb: Double = kneeDb.coerceAtLeast(0.0)
        set(value) {
            field = value.coerceAtLeast(0.0)
            updateCoefficients()
        }

    var attackSeconds: Double = attackSeconds
        set(value) {
            field = value
            updateCoefficients()
        }

    var releaseSeconds: Double = releaseSeconds
        set(value) {
            field = value
            updateCoefficients()
        }

    // Envelope follower state
    private var envelopeDb: Double = -Double.MAX_VALUE

    // Computed coefficients
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0

    init {
        updateCoefficients()
    }

    /**
     * Process a stereo buffer in-place.
     */
    fun process(left: DoubleArray, right: DoubleArray, blockSize: Int) {
        for (i in 0 until blockSize) {
            val inputLevel = max(abs(left[i]), abs(right[i]))

            // Convert to dB (with floor to avoid log(0))
            val inputDb = if (inputLevel > 1e-10) {
                20.0 * ln(inputLevel) / ln(10.0)
            } else {
                -100.0
            }

            // Envelope follower with attack/release
            val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
            envelopeDb = inputDb + coeff * (envelopeDb - inputDb)

            // Calculate gain reduction
            val gainReductionDb = calculateGainReduction(envelopeDb)

            // Convert gain reduction to linear scale
            val gainReduction = if (gainReductionDb < -0.01) {
                exp(gainReductionDb * ln(10.0) / 20.0)
            } else {
                1.0
            }

            // Apply gain reduction
            left[i] *= gainReduction
            right[i] *= gainReduction
        }
    }

    /**
     * Process a mono buffer in-place.
     */
    fun process(buffer: DoubleArray, blockSize: Int) {
        for (i in 0 until blockSize) {
            val inputLevel = abs(buffer[i])

            // Convert to dB
            val inputDb = if (inputLevel > 1e-10) {
                20.0 * ln(inputLevel) / ln(10.0)
            } else {
                -100.0
            }

            // Envelope follower
            val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
            envelopeDb = inputDb + coeff * (envelopeDb - inputDb)

            // Calculate gain reduction
            val gainReductionDb = calculateGainReduction(envelopeDb)

            // Convert to linear
            val gainReduction = if (gainReductionDb < -0.01) {
                exp(gainReductionDb * ln(10.0) / 20.0)
            } else {
                1.0
            }

            // Apply gain reduction
            buffer[i] *= gainReduction
        }
    }

    /**
     * Calculate gain reduction in dB for a given input level in dB.
     * Implements soft knee compression.
     */
    private fun calculateGainReduction(inputDb: Double): Double {
        val overshootDb = inputDb - thresholdDb
        val halfKnee = kneeDb / 2.0

        return when {
            // Below threshold - no compression
            overshootDb < -halfKnee -> 0.0

            // In the knee - soft transition
            overshootDb < halfKnee -> {
                val t = (overshootDb + halfKnee) / kneeDb
                val gainReduction =
                    (1.0 / ratio - 1.0) * (overshootDb + halfKnee) * (overshootDb + halfKnee) / (2.0 * kneeDb)
                gainReduction
            }

            // Above threshold - full compression
            else -> (1.0 / ratio - 1.0) * overshootDb
        }
    }

    /**
     * Update time constants for attack and release.
     */
    private fun updateCoefficients() {
        val attackTime = max(0.0001, attackSeconds)
        val releaseTime = max(0.0001, releaseSeconds)

        attackCoeff = 1.0 - exp(-1.0 / (attackTime * sampleRate))
        releaseCoeff = 1.0 - exp(-1.0 / (releaseTime * sampleRate))
    }

    /**
     * Reset the compressor state.
     */
    fun reset() {
        envelopeDb = -Double.MAX_VALUE
    }

    companion object {
        /**
         * Parse compressor settings from a string.
         * Format: "threshold:ratio:knee:attack:release"
         * Example: "-20:4:6:0.003:0.1"
         *
         * @return Triple of (threshold, ratio, knee, attack, release) or null if parsing fails
         */
        fun parseSettings(input: String): CompressorSettings? {
            val parts = input.split(":").mapNotNull { it.toDoubleOrNull() }

            return when (parts.size) {
                5 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = parts[2],
                    attackSeconds = parts[3],
                    releaseSeconds = parts[4]
                )
                2 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = 6.0,
                    attackSeconds = 0.003,
                    releaseSeconds = 0.1
                )
                else -> null
            }
        }
    }

    data class CompressorSettings(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    )
}
```

#### Step 3.8: Add Compressor to Voice interface

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

Add a Compressor settings holder class (after Ducking):

```kotlin
class Compressor(
    val thresholdDb: Double,
    val ratio: Double,
    val kneeDb: Double,
    val attackSeconds: Double,
    val releaseSeconds: Double,
)
```

#### Step 3.9: Add compressor field to Orbit

**File:** `audio_be/src/commonMain/kotlin/orbits/Orbit.kt`

**Location:** After the ducking fields

Add imports:

```kotlin
import io.peekandpoke.klang.audio_be.effects.Compressor
```

Add fields:

```kotlin
// Compressor
var compressor: Compressor? = null
private set
```

**Location:** In the `initFromVoice()` function, after ducking initialization

Add:

```kotlin
// Initialize compressor from voice if specified
voice.compressor?.let { compressorSettings ->
    compressor = Compressor(
        sampleRate = sampleRate,
        thresholdDb = compressorSettings.thresholdDb,
        ratio = compressorSettings.ratio,
        kneeDb = compressorSettings.kneeDb,
        attackSeconds = compressorSettings.attackSeconds,
        releaseSeconds = compressorSettings.releaseSeconds
    )
}
```

#### Step 3.10: Initialize compressor in VoiceScheduler

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

**Location:** After all other effect initialization (after ducking)

Add:

```kotlin
// Compressor
val compressorParam = data.compressor
val compressor = if (compressorParam != null) {
    val settings = Compressor.parseSettings(compressorParam)
    settings?.let {
        Voice.Compressor(
            thresholdDb = it.thresholdDb,
            ratio = it.ratio,
            kneeDb = it.kneeDb,
            attackSeconds = it.attackSeconds,
            releaseSeconds = it.releaseSeconds
        )
    }
} else null
```

Then pass `compressor` to both SynthVoice and SampleVoice constructors.

#### Step 3.11: Add compressor to Voice interface

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

**Location:** After ducking field

Add:

```kotlin
val ducking: Ducking?
val compressor: Compressor?
```

#### Step 3.12: Update SynthVoice and SampleVoice

**Files:**

- `audio_be/src/commonMain/kotlin/voices/SynthVoice.kt`
- `audio_be/src/commonMain/kotlin/voices/SampleVoice.kt`

Add `compressor` parameter:

```kotlin
override val ducking: Ducking?,
override val compressor: Compressor?,
```

#### Step 3.13: Apply compressor in Orbit processing

**File:** `audio_be/src/commonMain/kotlin/orbits/Orbit.kt`

**Location:** In the `processEffects()` function, after all other effects

Add:

```kotlin
// Apply compressor if configured
compressor?.process(mixBuffer.left, mixBuffer.right, blockFrames)
```

---

### Phase 4: Test Implementation

#### Step 4.1: Create DSL tests

**File:** `strudel/src/commonTest/kotlin/lang/LangDynamicsSpec.kt` (NEW FILE)

**Code:**

```kotlin
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangDynamicsSpec : StringSpec({

    "velocity() sets velocity" {
        val p = note("c3").velocity(0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.5
    }

    "vel() alias sets velocity" {
        val p = note("c3").vel(0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.8
    }

    "velocity() can be used as standalone function" {
        val p = velocity(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.7
    }

    "postgain() sets postGain" {
        val p = note("c3").postgain(1.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 1.5
    }

    "postgain() can be used as standalone function" {
        val p = postgain(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 2.0
    }

    "compressor() sets compressor string" {
        val p = note("c3").compressor("-20:4:6:0.003:0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-20:4:6:0.003:0.1"
    }

    "comp() alias sets compressor" {
        val p = note("c3").comp("-15:3:4:0.005:0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-15:3:4:0.005:0.2"
    }

    "compressor() can be used as standalone function" {
        val p = compressor("-18:6:8:0.001:0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-18:6:8:0.001:0.05"
    }

    "velocity, postgain, and compressor work together" {
        val p = note("c3")
            .velocity(0.8)
            .postgain(1.2)
            .compressor("-20:4:6:0.003:0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.8
        events[0].data.postGain shouldBe 1.2
        events[0].data.compressor shouldBe "-20:4:6:0.003:0.1"
    }

    "dynamics parameters transfer to VoiceData" {
        val p = note("c3")
            .velocity(0.6)
            .postgain(1.5)
            .compressor("-18:3:4:0.005:0.15")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val voiceData = events[0].data.toVoiceData()

        voiceData.velocity shouldBe 0.6
        voiceData.postGain shouldBe 1.5
        voiceData.compressor shouldBe "-18:3:4:0.005:0.15"
    }

    "velocity works with pattern control" {
        val p = note("c3 d3 e3").velocity("0.5 0.8 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.velocity shouldBe 0.5
        events[1].data.velocity shouldBe 0.8
        events[2].data.velocity shouldBe 1.0
    }
})
```

#### Step 4.2: Create audio engine tests

**File:** `audio_be/src/commonTest/kotlin/effects/CompressorSpec.kt` (NEW FILE)

**Code:**

```kotlin
package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class CompressorSpec : StringSpec({

    val sampleRate = 44100

    "Compressor reduces signal above threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with loud signal (above threshold)
        val buffer = DoubleArray(1000) { 0.5 } // ~-6 dB

        compressor.process(buffer, 1000)

        // Signal should be reduced
        val avgLevel = buffer.map { abs(it) }.average()
        avgLevel shouldBe (0.5 plusOrMinus 0.2)
    }

    "Compressor does not affect signal below threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with quiet signal (below threshold)
        val buffer = DoubleArray(1000) { 0.01 } // ~-40 dB
        val original = buffer.copyOf()

        compressor.process(buffer, 1000)

        // Signal should be mostly unchanged
        for (i in buffer.indices) {
            buffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }

    "Compressor.parseSettings parses full format" {
        val settings = Compressor.parseSettings("-20:4:6:0.003:0.1")

        settings shouldBe Compressor.CompressorSettings(
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 6.0,
            attackSeconds = 0.003,
            releaseSeconds = 0.1
        )
    }

    "Compressor.parseSettings parses short format (threshold:ratio only)" {
        val settings = Compressor.parseSettings("-15:3")

        settings shouldBe Compressor.CompressorSettings(
            thresholdDb = -15.0,
            ratio = 3.0,
            kneeDb = 6.0,
            attackSeconds = 0.003,
            releaseSeconds = 0.1
        )
    }

    "Compressor.parseSettings returns null for invalid input" {
        val settings = Compressor.parseSettings("invalid")
        settings shouldBe null
    }

    "Compressor reset clears state" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0
        )

        // Process some audio
        val buffer = DoubleArray(100) { 0.5 }
        compressor.process(buffer, 100)

        // Reset
        compressor.reset()

        // Process quiet signal - should not be affected by previous state
        val quietBuffer = DoubleArray(100) { 0.01 }
        val original = quietBuffer.copyOf()
        compressor.process(quietBuffer, 100)

        for (i in quietBuffer.indices) {
            quietBuffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }

    "Compressor processes stereo correctly" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val left = DoubleArray(1000) { 0.5 }
        val right = DoubleArray(1000) { 0.5 }

        compressor.process(left, right, 1000)

        // Both channels should be reduced
        val avgLeft = left.map { abs(it) }.average()
        val avgRight = right.map { abs(it) }.average()

        avgLeft shouldBe (0.5 plusOrMinus 0.2)
        avgRight shouldBe (0.5 plusOrMinus 0.2)
    }

    "Compressor soft knee creates smooth transition" {
        val hardKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val softKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 12.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create signal right at threshold
        val bufferHard = DoubleArray(1000) { 0.1 } // ~-20 dB
        val bufferSoft = bufferHard.copyOf()

        hardKnee.process(bufferHard, 1000)
        softKnee.process(bufferSoft, 1000)

        // Soft knee should result in different output (more gradual)
        val avgHard = bufferHard.map { abs(it) }.average()
        val avgSoft = bufferSoft.map { abs(it) }.average()

        // Both should compress, but soft knee should be gentler
        avgHard shouldBe (0.1 plusOrMinus 0.05)
        avgSoft shouldBe (0.1 plusOrMinus 0.05)
    }
})
```

---

### Phase 5: GraalVM JavaScript Interop

#### Step 5.1: Update GraalStrudelPattern

**File:** `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt`

**Location:** In the parameter extraction section, after gain

Add:

```kotlin
// Dynamics
val velocity = value.safeGetMember("velocity").safeNumberOrNull()
    ?: value.safeGetMember("vel").safeNumberOrNull()
val postGain = value.safeGetMember("postgain").safeNumberOrNull()
val compressor = value.safeGetMember("compressor").safeStringOrNull()
    ?: value.safeGetMember("comp").safeStringOrNull()
```

**Location:** In the StrudelVoiceData constructor call

Add after gain:

```kotlin
gain = gain,
velocity = velocity,
postGain = postGain,
```

Add after vowel:

```kotlin
vowel = vowel,
compressor = compressor,
```

---

## ✅ Verification Checklist

After implementation, verify:

- [ ] All three DSL functions (`velocity`, `postgain`, `compressor`) work in patterns
- [ ] Aliases (`vel`, `comp`) work correctly
- [ ] Parameters transfer correctly from StrudelVoiceData to VoiceData
- [ ] `velocity` multiplies with `gain` correctly in VoiceScheduler
- [ ] `postgain` is applied after voice processing but before orbit mixing
- [ ] Compressor parses settings string correctly
- [ ] Compressor effect processes audio at the orbit level
- [ ] All DSL tests pass (11 tests)
- [ ] All audio engine tests pass (8 tests)
- [ ] JavaScript interop works (parameters can be set from JS patterns)
- [ ] Existing tests still pass after changes

---

## 📚 Usage Examples

```kotlin
// Velocity (volume scaling)
s("bd").velocity(0.8)  // 80% of the normal gain
s("bd").vel(0.5)       // Alias version

// Postgain (applied after processing)
s("bass").postgain(1.5)  // Boost by 50% after voice effects

// Compressor (orbit-level dynamic range compression)
s("drums").orbit(0).compressor("-20:4:6:0.003:0.1")
// Format: threshold:ratio:knee:attack:release
// threshold: -20 dB
// ratio: 4:1
// knee: 6 dB
// attack: 0.003 seconds (3ms)
// release: 0.1 seconds (100ms)

// Short format (just threshold and ratio)
s("drums").orbit(0).comp("-15:3")

// Combined usage
s("bass")
    .velocity(0.8)
    .postgain(1.2)
    .orbit(0)
    .compressor("-18:4:8:0.005:0.15")
```

---

## 🔍 Design Decisions

### Velocity vs Gain

- **gain**: Base volume level (pre-processing)
- **velocity**: Multiplier applied to gain (mimics MIDI velocity)
- Combined: `effectiveGain = gain * velocity`

### Postgain Application Point

- Applied **after** voice rendering (envelope, filters, etc.)
- Applied **before** mixing to orbit bus
- Allows boosting/cutting after all voice-level processing

### Compressor at Orbit Level

- Unlike velocity/postgain (voice-level), compressor is orbit-level
- Compresses the mixed output of all voices on that orbit
- Parsed from string format for pattern control compatibility
- Uses envelope follower with attack/release for smooth gain reduction

### String Format for Compressor

- Format: `"threshold:ratio:knee:attack:release"` or `"threshold:ratio"`
- Allows pattern control: `.compressor("-20:4 -15:3 -18:6")`
- Compatible with Strudel.js string-based parameter system

---

## ⏱️ Time Estimates

- **Phase 1 (Data Structures)**: ~30 minutes
- **Phase 2 (DSL Functions)**: ~30 minutes
- **Phase 3 (Audio Engine)**: ~90 minutes
    - velocity/postgain: 20 min
    - Compressor processor: 40 min
    - Integration: 30 min
- **Phase 4 (Tests)**: ~40 minutes
- **Phase 5 (GraalVM)**: ~15 minutes

**Total**: ~3.5 hours
