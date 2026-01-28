# Audio Warmth Parameter Implementation

## Context

We want to expose a `warmth` parameter in the Strudel DSL to control the "analog warmth" (one-pole low-pass filtering)
of oscillators. This will replace the hardcoded `warmthFactor` currently in `supersawFn` and allow it to be applied to
other oscillators as well.

## Implementation Plan

### 1. Add `warmth()` DSL Function

**File:** `strudel/src/commonMain/kotlin/lang/addons/lang_misc.kt`

* Define the `warmth` mutation and DSL functions.
* **Name:** `warmth`
* **Type:** Double (0.0 to 1.0) - 0.0 is bright (raw), 1.0 is muffled.

```kotlin
// ... existing imports ...
// -- warmth() ---------------------------------------------------------------------------------------------------------
private val warmthMutation = voiceModifier { copy(warmth = it?.asDoubleOrNull()) }
private fun applyWarmth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern { return source.applyNumericalParam( args = args, modify = warmthMutation, getValue = { warmth }, setValue = { v, _ -> copy(warmth = v) }, ) }
/** Controls the oscillator warmth (low-pass filtering amount). 0.0 = bright, 1.0 = muffled. _/ @StrudelDsl val StrudelPattern.warmth by dslPatternExtension { p, args, /_ callInfo */ _ -> applyWarmth(p, args) }
/** Controls the oscillator warmth. / @StrudelDsl val warmth by dslFunction { args, / callInfo */ _ -> args.toPattern(warmthMutation) }
/** Controls the oscillator warmth on a string. */ @StrudelDsl val String.warmth by dslStringExtension { p, args, callInfo -> p.warmth(args, callInfo) }
```

### 2. Update `StrudelVoiceData`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

* Add `val warmth: Double?` to the data class.
* Update `empty`, `merge`, and `toVoiceData` methods.
* In `toVoiceData`, map it to the `VoiceData` object (which also needs updating).

### 3. Update `VoiceData` (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt` (You will need to find this file, it wasn't in attachments
but is implied)

* Add `val warmth: Double?` to `VoiceData`.

### 4. Implement `OscFn.withWarmth` Wrapper

**File:** `audio_be/src/commonMain/kotlin/osci/Oscillators.kt`

* Add the `withWarmth` extension function to `OscFn`.

```kotlin
/**
Wraps an [OscFn] with a one-pole low-pass filter to tame harsh harmonics.
@param warmthFactor Amount of filtering (0.0 = none, 1.0 = full). */ fun OscFn.withWarmth(warmthFactor: Double): OscFn { if (warmthFactor <= 0.0) return this // Optimization: skip if no warmth
// State is captured here, unique to this wrapper instance var lastSample = 0.0 val alpha = warmthFactor.coerceIn(0.0, 0.99)
return OscFn { buffer, offset, length, phase, phaseInc, phaseMod -> // 1. Generate raw waveform val nextPhase = this.process(buffer, offset, length, phase, phaseInc, phaseMod)
// 2. Apply One-Pole LPF
    val end = offset + length
    for (i in offset until end) {
        val raw = buffer[i]
        val smoothed = raw + alpha * (lastSample - raw)
        buffer[i] = smoothed
        lastSample = smoothed
    }

    nextPhase
} }
    
```

### 5. Remove Hardcoded Warmth from `supersawFn`

**File:** `audio_be/src/commonMain/kotlin/osci/Oscillators.kt`

* Remove the manual `lastSample` and filtering loop from `supersawFn`. Return the raw PolyBLEP output.

### 6. Update `VoiceScheduler` to Apply Warmth

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

* Update `createOscillator` to check for `warmth` in `VoiceData` and wrap the oscillator.

```kotlin
fun VoiceData.createOscillator(oscillators: Oscillators, freqHz: Double): OscFn { val e = this
// Create base oscillator
    val rawOsc = oscillators.get(
        name = e.sound,
        freqHz = freqHz,
        density = e.density,
        voices = e.voices,
        freqSpread = e.freqSpread,
        panSpread = e.panSpread,
    )

    // Apply Warmth Wrapper if needed
    // Default warmth could be 0.0, or small (e.g. 0.25) if we want "default" to be warmer
    val warmthAmount = e.warmth ?: 0.0
    return if (warmthAmount > 0.0) {
        rawOsc.withWarmth(warmthAmount)
    } else {
        rawOsc
    }
}
```

*Note: You might need to add `warmth` property to `VoiceData` class inside `VoiceScheduler` if it's defined there, or
just use the updated `io.peekandpoke.klang.audio_bridge.VoiceData`.*

## Verification

* Compile the project.
* Verify that `s("supersaw")` behaves as a raw, bright oscillator.
* Verify that `s("supersaw").warmth(0.5)` produces a filtered, warmer tone.
