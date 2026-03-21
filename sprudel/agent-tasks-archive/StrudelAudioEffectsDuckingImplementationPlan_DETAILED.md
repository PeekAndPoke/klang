# Strudel Audio Effects - Ducking / Sidechain - DETAILED Implementation Plan

## 🎯 Overview

Implement cross-orbit sidechain ducking, allowing one orbit to automatically reduce the volume of another orbit based on
its signal level. This is commonly used for:

- Bass ducking when kick drum plays
- Sidechain compression for pumping effects
- Creating space in the mix for lead elements

**Example Use Case:**

```javascript
// Kick drum on orbit 0
s("bd").orbit(0)

// Bass on orbit 1, ducked by orbit 0
s("bass").orbit(1).duck(0).duckdepth(0.7).duckattack(0.1)
```

---

## 📋 Implementation Phases

### PHASE 1: Data Structures (DSL Layer)

### PHASE 2: DSL Functions

### PHASE 3: Audio Engine Implementation

### PHASE 4: Testing & Verification

---

## 🔧 PHASE 1: Data Structure Updates

### 1.1 Update StrudelVoiceData

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

**Location:** Add after tremolo fields (around line 88)

```kotlin
// Ducking / Sidechain
/** Target orbit to listen to for ducking (source of sidechain signal) */
val duckOrbit: Int?,
/** Duck return-to-normal time in seconds (attack/release time) */
val duckAttack: Double?,
/** Ducking amount (0.0 = no ducking, 1.0 = full silence) */
val duckDepth: Double?,
```

**Update companion object `empty`** (around line 194):

```kotlin
duckOrbit = null,
duckAttack = null,
duckDepth = null,
```

**Update `merge()` function** (around line 307):

```kotlin
duckOrbit = other.duckOrbit ?: duckOrbit,
duckAttack = other.duckAttack ?: duckAttack,
duckDepth = other.duckDepth ?: duckDepth,
```

**Update `toVoiceData()` function** (around line 510, after roomDim/iResponse):

```kotlin
duckOrbit = duckOrbit,
duckAttack = duckAttack,
duckDepth = duckDepth,
```

---

### 1.2 Update VoiceData (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

**Location:** Add after iResponse field (around line 100)

```kotlin
// Ducking / Sidechain
val duckOrbit: Int?,
val duckAttack: Double?,
val duckDepth: Double?,
```

**Update companion object `empty`** (around line 155):

```kotlin
duckOrbit = null,
duckAttack = null,
duckDepth = null,
```

---

## 🎨 PHASE 2: DSL Implementation

**File:** `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`

**Location:** Add after the `orbit()` function section (after line 386)

### 2.1 `duckorbit()` / `duck()`

```kotlin
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// ///

// -- duckorbit() / duck() -----------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceModifier {
    copy(duckOrbit = it?.asIntOrNull())
}

private fun applyDuckOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = duckOrbitMutation,
        getValue = { duckOrbit?.toDouble() },
        setValue = { v, _ -> copy(duckOrbit = v.toInt()) }
    )
}

/** Sets the target orbit to listen to for ducking (sidechain source) */
@StrudelDsl
val StrudelPattern.duckorbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }

/** Sets the target orbit to listen to for ducking (sidechain source) */
@StrudelDsl
val duckorbit by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckOrbitMutation) }

/** Sets the target orbit to listen to for ducking (sidechain source) on a string */
@StrudelDsl
val String.duckorbit by dslStringExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }

/** Alias for [duckorbit] */
@StrudelDsl
val StrudelPattern.duck by dslPatternExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }

/** Alias for [duckorbit] */
@StrudelDsl
val duck by dslFunction { args, callInfo -> duckorbit(args, callInfo) }

/** Alias for [duckorbit] on a string */
@StrudelDsl
val String.duck by dslStringExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }
```

### 2.2 `duckattack()` / `duckatt()`

```kotlin
// -- duckattack() / duckatt() -------------------------------------------------------------------------------------

private val duckAttackMutation = voiceModifier {
    copy(duckAttack = it?.asDoubleOrNull())
}

private fun applyDuckAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = duckAttackMutation,
        getValue = { duckAttack },
        setValue = { v, _ -> copy(duckAttack = v) },
    )
}

/** Sets duck return-to-normal time in seconds (attack/release time) */
@StrudelDsl
val StrudelPattern.duckattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckAttack(p, args) }

/** Sets duck return-to-normal time in seconds (attack/release time) */
@StrudelDsl
val duckattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckAttackMutation) }

/** Sets duck return-to-normal time in seconds (attack/release time) on a string */
@StrudelDsl
val String.duckattack by dslStringExtension { p, args, callInfo -> p.duckattack(args, callInfo) }

/** Alias for [duckattack] */
@StrudelDsl
val StrudelPattern.duckatt by dslPatternExtension { p, args, callInfo -> p.duckattack(args, callInfo) }

/** Alias for [duckattack] */
@StrudelDsl
val duckatt by dslFunction { args, callInfo -> duckattack(args, callInfo) }

/** Alias for [duckattack] on a string */
@StrudelDsl
val String.duckatt by dslStringExtension { p, args, callInfo -> p.duckattack(args, callInfo) }
```

### 2.3 `duckdepth()`

```kotlin
// -- duckdepth() --------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceModifier {
    copy(duckDepth = it?.asDoubleOrNull())
}

private fun applyDuckDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = duckDepthMutation,
        getValue = { duckDepth },
        setValue = { v, _ -> copy(duckDepth = v) },
    )
}

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) */
@StrudelDsl
val StrudelPattern.duckdepth by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckDepth(p, args) }

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) */
@StrudelDsl
val duckdepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckDepthMutation) }

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) on a string */
@StrudelDsl
val String.duckdepth by dslStringExtension { p, args, callInfo -> p.duckdepth(args, callInfo) }
```

---

## 🎵 PHASE 3: Audio Engine Implementation

This is the most complex part. We need to add sidechain ducking as an orbit-level effect.

### 3.1 Create DuckingProcessor Class

**File:** `audio_be/src/commonMain/kotlin/effects/Ducking.kt` (NEW FILE)

```kotlin
package io.peekandpoke.klang.audio_be.effects

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Sidechain ducking/compression processor.
 *
 * Reduces the volume of a signal based on the level of a sidechain (trigger) signal.
 * Commonly used for kick-bass ducking or pumping effects.
 *
 * @param sampleRate Audio sample rate in Hz
 * @param attackSeconds Time to return to normal volume after trigger stops (in seconds)
 * @param depth Amount of ducking (0.0 = no effect, 1.0 = full silence)
 */
class Ducking(
    private val sampleRate: Int,
    attackSeconds: Double = 0.1,
    depth: Double = 0.5,
) {
    /** Attack coefficient for envelope follower (how fast volume returns) */
    private var attackCoeff: Double = calculateCoefficient(attackSeconds)

    /** Current gain reduction (0.0 = full duck, 1.0 = no duck) */
    private var currentGain: Double = 1.0

    /** Ducking depth (0.0 = no ducking, 1.0 = full silence) */
    var depth: Double = depth.coerceIn(0.0, 1.0)
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** Attack time in seconds (return to normal) */
    var attackSeconds: Double = attackSeconds
        set(value) {
            field = value
            attackCoeff = calculateCoefficient(value)
        }

    /**
     * Processes audio with sidechain ducking.
     *
     * @param input The audio buffer to be ducked
     * @param sidechain The trigger signal (e.g., kick drum on another orbit)
     * @param blockSize Number of samples to process
     */
    fun process(input: FloatArray, sidechain: FloatArray, blockSize: Int) {
        require(input.size >= blockSize) { "Input buffer too small" }
        require(sidechain.size >= blockSize) { "Sidechain buffer too small" }

        for (i in 0 until blockSize) {
            // Calculate sidechain signal level (RMS-like envelope following)
            val sidechainLevel = abs(sidechain[i])

            // Calculate target gain based on sidechain level
            // When sidechain is loud, gain goes down (ducking)
            val targetGain = if (sidechainLevel > 0.01) {
                // Sidechain is active - reduce gain
                1.0 - (depth * min(1.0, sidechainLevel * 2.0))
            } else {
                // No sidechain signal - full volume
                1.0
            }

            // Smooth gain changes with envelope follower
            // Fast attack when ducking, slower release when returning
            currentGain = if (targetGain < currentGain) {
                // Fast attack (immediate ducking)
                targetGain
            } else {
                // Slower release (smooth return to normal)
                currentGain + attackCoeff * (targetGain - currentGain)
            }

            // Apply gain reduction
            input[i] *= currentGain.toFloat()
        }
    }

    /**
     * Calculate time constant coefficient for envelope follower.
     * Higher values = faster response.
     */
    private fun calculateCoefficient(timeSeconds: Double): Double {
        // Prevent division by zero and ensure minimum attack time
        val clampedTime = max(0.001, timeSeconds)
        // Time constant for 63% response
        return 1.0 - kotlin.math.exp(-1.0 / (clampedTime * sampleRate))
    }

    /** Reset internal state */
    fun reset() {
        currentGain = 1.0
    }
}
```

### 3.2 Update Orbit Class

**File:** `audio_be/src/commonMain/kotlin/orbits/Orbit.kt`

**Add ducking fields** (around line 30, after phaser):

```kotlin
// Ducking / Sidechain
/** Sidechain source orbit ID (which orbit triggers the ducking) */
var duckOrbitId: Int? = null
private set

/** Ducking processor instance */
var ducking: Ducking? = null
private set
```

**Update `init()` function** (around line 65, after phaser initialization):

```kotlin
// Initialize ducking if parameters present
val duckOrbitParam = voice.duckOrbit
val duckAttackParam = voice.duckAttack
val duckDepthParam = voice.duckDepth

if (duckOrbitParam != null && duckDepthParam != null && duckDepthParam > 0.0) {
    duckOrbitId = duckOrbitParam
    ducking = Ducking(
        sampleRate = sampleRate,
        attackSeconds = duckAttackParam ?: 0.1,
        depth = duckDepthParam
    )
}
```

**Update `processEffects()` function** - This is where ducking will be applied (around line 95, after phaser
processing):

```kotlin
// Apply ducking (requires reference to source orbit - handled in Orbits.kt)
// Note: Actual ducking processing happens in Orbits.processAndMix()
// to access the sidechain source orbit's buffer
```

### 3.3 Update Orbits Class

**File:** `audio_be/src/commonMain/kotlin/orbits/Orbits.kt`

**Update `processAndMix()` function** (around line 40, replace the current implementation):

```kotlin
/**
 * Process effects on all active orbits and mix to output.
 *
 * Processing order:
 * 1. Process all orbit effects (delay, reverb, phaser)
 * 2. Apply ducking (requires all orbits processed first)
 * 3. Mix all orbits to master output
 */
fun processAndMix(out: FloatArray, blockSize: Int) {
    // Step 1: Process effects on all active orbits
    for (i in 0 until maxOrbits) {
        val orbit = orbits[i]
        if (orbit.active) {
            orbit.processEffects(blockSize)
        }
    }

    // Step 2: Apply ducking (cross-orbit sidechain)
    for (i in 0 until maxOrbits) {
        val orbit = orbits[i]
        if (orbit.active) {
            val ducking = orbit.ducking
            val duckOrbitId = orbit.duckOrbitId

            if (ducking != null && duckOrbitId != null) {
                // Get sidechain source orbit
                val sidechainOrbit = orbits.getOrNull(duckOrbitId)

                if (sidechainOrbit != null && sidechainOrbit.active) {
                    // Apply ducking using sidechain orbit's mix as trigger
                    ducking.process(
                        input = orbit.mixBuffer,
                        sidechain = sidechainOrbit.mixBuffer,
                        blockSize = blockSize
                    )
                }
            }
        }
    }

    // Step 3: Mix all orbits to output
    for (i in 0 until maxOrbits) {
        val orbit = orbits[i]
        if (orbit.active) {
            val mixBuffer = orbit.mixBuffer

            for (j in 0 until blockSize) {
                out[j] += mixBuffer[j]
            }
        }
    }
}
```

---

## 🧪 PHASE 4: Testing & Verification

### 4.1 Create DSL Test

**File:** `strudel/src/commonTest/kotlin/lang/LangDuckingSpec.kt` (NEW FILE)

```kotlin
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.initStrudelLang
import io.peekandpoke.klang.strudel.pattern.mini

class LangDuckingSpec : StringSpec({
    beforeTest {
        initStrudelLang()
    }

    "duck() sets duckOrbit" {
        val result = "bd".mini()
            .duck(0)
            .queryFirstValue()

        result?.duckOrbit shouldBe 0
    }

    "duckorbit() sets duckOrbit" {
        val result = "bd".mini()
            .duckorbit(1)
            .queryFirstValue()

        result?.duckOrbit shouldBe 1
    }

    "duckattack() sets duckAttack" {
        val result = "bd".mini()
            .duckattack(0.15)
            .queryFirstValue()

        result?.duckAttack shouldBe 0.15
    }

    "duckatt() alias sets duckAttack" {
        val result = "bd".mini()
            .duckatt(0.2)
            .queryFirstValue()

        result?.duckAttack shouldBe 0.2
    }

    "duckdepth() sets duckDepth" {
        val result = "bd".mini()
            .duckdepth(0.7)
            .queryFirstValue()

        result?.duckDepth shouldBe 0.7
    }

    "ducking parameters merge correctly" {
        val result = "bd".mini()
            .duck(0)
            .duckatt(0.1)
            .duckdepth(0.8)
            .queryFirstValue()

        result?.duckOrbit shouldBe 0
        result?.duckAttack shouldBe 0.1
        result?.duckDepth shouldBe 0.8
    }

    "ducking parameters transfer to VoiceData" {
        val strudelData = "bd".mini()
            .duck(0)
            .duckatt(0.15)
            .duckdepth(0.6)
            .queryFirstValue()

        val voiceData = strudelData?.toVoiceData()

        voiceData?.duckOrbit shouldBe 0
        voiceData?.duckAttack shouldBe 0.15
        voiceData?.duckDepth shouldBe 0.6
    }
})
```

### 4.2 Create Audio Engine Test

**File:** `audio_be/src/commonTest/kotlin/effects/DuckingSpec.kt` (NEW FILE)

```kotlin
package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class DuckingSpec : StringSpec({
    val sampleRate = 44100

    "Ducking reduces volume when sidechain is active" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.01,
            depth = 0.8
        )

        // Create input signal (constant level)
        val input = FloatArray(100) { 1.0f }

        // Create sidechain signal (loud trigger)
        val sidechain = FloatArray(100) { 0.8f }

        ducking.process(input, sidechain, 100)

        // Input should be reduced significantly
        val avgLevel = input.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Ducking returns to normal when sidechain stops" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.001, // Very fast return
            depth = 1.0
        )

        // Process with active sidechain
        val input1 = FloatArray(100) { 1.0f }
        val sidechain1 = FloatArray(100) { 1.0f }
        ducking.process(input1, sidechain1, 100)

        // Process with silent sidechain (should return to normal)
        val input2 = FloatArray(1000) { 1.0f }
        val sidechain2 = FloatArray(1000) { 0.0f }
        ducking.process(input2, sidechain2, 1000)

        // Should return close to full volume
        val endLevel = input2.takeLast(100).map { abs(it.toDouble()) }.average()
        endLevel shouldBe 1.0 plusOrMinus 0.1
    }

    "Depth parameter controls ducking amount" {
        val input1 = FloatArray(100) { 1.0f }
        val input2 = FloatArray(100) { 1.0f }
        val sidechain = FloatArray(100) { 1.0f }

        val duckingLight = Ducking(sampleRate, 0.01, depth = 0.3)
        val duckingHeavy = Ducking(sampleRate, 0.01, depth = 0.9)

        duckingLight.process(input1, sidechain, 100)
        duckingHeavy.process(input2, sidechain, 100)

        val avgLight = input1.average()
        val avgHeavy = input2.average()

        // Heavy ducking should reduce more
        avgHeavy shouldBeLessThan avgLight
    }

    "Reset clears internal state" {
        val ducking = Ducking(sampleRate, 0.01, 1.0)

        // Duck the signal
        val input = FloatArray(100) { 1.0f }
        val sidechain = FloatArray(100) { 1.0f }
        ducking.process(input, sidechain, 100)

        // Reset
        ducking.reset()

        // Should process at full volume immediately
        val input2 = FloatArray(10) { 1.0f }
        val sidechain2 = FloatArray(10) { 0.0f }
        ducking.process(input2, sidechain2, 10)

        input2[0].toDouble() shouldBe 1.0 plusOrMinus 0.01
    }
})

private infix fun Double.plusOrMinus(tolerance: Double) = object {
    infix fun shouldBe(expected: Double) {
        val diff = abs(this@plusOrMinus - expected)
        if (diff > tolerance) {
            throw AssertionError("Expected $expected ± $tolerance but got ${this@plusOrMinus}")
        }
    }
}
```

### 4.3 Manual Testing Example

Create a test pattern to verify ducking works end-to-end:

```javascript
// Kick drum on orbit 0 (trigger)
stack(
    s("bd").orbit(0),

    // Bass on orbit 1, ducked by orbit 0
    s("bass").n("0 2 5 3").orbit(1)
        .duck(0)          // Listen to orbit 0
        .duckdepth(0.7)   // 70% volume reduction
        .duckatt(0.15)    // 150ms return time
)
    .cpm(120)
```

Expected behavior:

- When kick drum hits, bass volume drops by 70%
- Bass volume returns smoothly over 150ms after kick ends
- Creates classic sidechain "pumping" effect

---

## 📝 Implementation Checklist

### Phase 1: Data Structures

- [ ] Add fields to `StrudelVoiceData`
- [ ] Update `StrudelVoiceData.empty`
- [ ] Update `StrudelVoiceData.merge()`
- [ ] Update `StrudelVoiceData.toVoiceData()`
- [ ] Add fields to `VoiceData`
- [ ] Update `VoiceData.empty`

### Phase 2: DSL Functions

- [ ] Implement `duckorbit()` / `duck()`
- [ ] Implement `duckattack()` / `duckatt()`
- [ ] Implement `duckdepth()`
- [ ] Add all pattern, standalone, and string extension variants
- [ ] Verify aliases work correctly

### Phase 3: Audio Engine

- [ ] Create `Ducking.kt` effect processor
- [ ] Add ducking fields to `Orbit.kt`
- [ ] Update `Orbit.init()` to initialize ducking
- [ ] Update `Orbits.processAndMix()` for cross-orbit ducking
- [ ] Test with manual audio playback

### Phase 4: Testing

- [ ] Create `LangDuckingSpec.kt`
- [ ] Create `DuckingSpec.kt`
- [ ] Verify DSL parameters set correctly
- [ ] Verify VoiceData mapping works
- [ ] Test ducking audio processing
- [ ] Test edge cases (invalid orbit IDs, zero depth, etc.)

### Phase 5: Documentation

- [ ] Update TODOS.MD to mark ducking as complete
- [ ] Add usage examples to documentation
- [ ] Document expected behavior and parameters

---

## 🎯 Key Design Decisions

### 1. Processing Order

Ducking is applied **after** all orbit effects (delay, reverb, phaser) but **before** mixing to master. This ensures:

- The sidechain signal includes all effects from the source orbit
- Ducking affects the final orbit mix including all effects

### 2. Attack vs Release

Unlike traditional compressors with separate attack/release:

- **Attack is instant** (immediate ducking when trigger hits)
- **Release uses `duckattack` parameter** (smooth return to normal)

This matches typical sidechain ducking behavior where you want instant response but smooth release.

### 3. Envelope Following

Uses simple RMS-like envelope following rather than true RMS calculation for efficiency. The sidechain level is smoothed
using an exponential moving average.

### 4. Default Values

- **duckattack**: 0.1 seconds (100ms return time)
- **duckdepth**: No default in DSL (must be set explicitly to enable ducking)
- Ducking only activates if both `duckOrbit` and `duckDepth > 0` are set

### 5. Error Handling

- Invalid orbit IDs are silently ignored (no ducking applied)
- Depth is clamped to 0.0-1.0 range
- Minimum attack time of 1ms to prevent instability

---

## 🚀 Implementation Time Estimate

- **Phase 1 (Data Structures)**: 30 minutes
- **Phase 2 (DSL Functions)**: 45 minutes
- **Phase 3 (Audio Engine)**: 2-3 hours
- **Phase 4 (Testing)**: 1-2 hours
- **Total**: 4-6 hours

---

## 📚 References

**Architecture Understanding:**

- Orbit system: `audio_be/src/commonMain/kotlin/orbits/Orbit.kt`
- Effect processing flow: `audio_be/src/commonMain/kotlin/orbits/Orbits.kt`
- Similar effects: `audio_be/src/commonMain/kotlin/effects/Phaser.kt`

**DSL Pattern Examples:**

- Numerical parameters: `lang_dynamics.kt` (orbit, gain, pan)
- Effect parameters: `lang_effects.kt` (tremolo, phaser)

**Testing Examples:**

- DSL tests: `strudel/src/commonTest/kotlin/lang/`
- Effect tests: `audio_be/src/commonTest/kotlin/effects/`
