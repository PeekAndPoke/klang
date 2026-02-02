Here is the detailed implementation plan for the Additive and FM synthesis parameters.

### Implementation Plan: Additive & FM Synthesis

This plan involves updating the core data structures, adding the JS-bridge mapping, implementing the DSL functions, and
adding tests.

#### 1. Update Data Model: `StrudelVoiceData`

**Target File:** `strudel/src/commonMain/kotlin/pattern/StrudelVoiceData.kt` (inferred path)

Add the new fields to the `StrudelVoiceData` data class. Since `partials` and `phases` can be dynamic lists or patterns,
we should store them as `StrudelVoiceValue?` to allow maximum flexibility (e.g. `StrudelVoiceValue.Seq` or `.Str`).

```kotlin
// Add these fields to the data class
data class StrudelVoiceData(
    // ... existing fields ...

    // Additive Synthesis
    val partials: StrudelVoiceValue? = null,
    val phases: StrudelVoiceValue? = null,

    // FM Synthesis
    val fmh: Double? = null, // Harmonicity Ratio
    val fmAttack: Double? = null,
    val fmDecay: Double? = null,
    val fmSustain: Double? = null,
    val fmEnv: Double? = null,

    // ... existing fields ...
)
```

#### 2. Update JS Bridge: `GraalStrudelPattern`

**Target File:** `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt`

Update the `toStrudelEvent` function to read these properties from the JS object and map them to `StrudelVoiceData`.

**Implementation details:**

* For `partials` and `phases`: Use `safeGetMember` and check if it's an array to convert it to `StrudelVoiceValue.Seq` (
  or use `asVoiceValue()` extension if available).
* For FM params: Use `safeNumberOrNull()`.

```kotlin
// In Value.toStrudelEvent():

// ... existing code ...

// ///////////////////////////////////////////////////////////////////////////////////
// Additive Synthesis
val partials = value.safeGetMember("partials")?.let {
    // Logic to convert JS Array/Value to StrudelVoiceValue
    // Suggest reusing the logic used for 'value' field at the bottom of the file
    // e.g. it.asVoiceValue() 
}
val phases = value.safeGetMember("phases")?.let { it.asVoiceValue() }

// ///////////////////////////////////////////////////////////////////////////////////
// FM Synthesis
val fmh = value.safeGetMember("fmh").safeNumberOrNull()
val fmAttack = value.safeGetMember("fmattack").safeNumberOrNull()
val fmDecay = value.safeGetMember("fmdecay").safeNumberOrNull()
val fmSustain = value.safeGetMember("fmsustain").safeNumberOrNull()
val fmEnv = value.safeGetMember("fmenv").safeNumberOrNull()

// ... existing code ...

// In the StrudelVoiceData constructor call:
data = StrudelVoiceData(
    // ... existing args ...
    partials = partials,
    phases = phases,
    fmh = fmh,
    fmAttack = fmAttack,
    fmDecay = fmDecay,
    fmSustain = fmSustain,
    fmEnv = fmEnv,
    // ... existing args ...
)
```

#### 3. Implement DSL Functions: `lang_synthesis.kt`

**Target File:** Create `strudel/src/commonMain/kotlin/lang/lang_synthesis.kt`

Create a new file for synthesis-related functions to avoid cluttering `lang_effects.kt`.

**Implementation Requirements:**

1. **Additive Synthesis (`partials`, `phases`)**:
    * Should accept: `List<Number>`, `vararg Number`, or `String` (space-separated).
    * Should allow control patterns.
    * Implement as `voiceModifier` that converts inputs to `StrudelVoiceValue.Seq` (for lists) or stores the pattern.
2. **FM Params**:
    * Implement exactly like `distort` or `attack` in `lang_effects.kt`.
    * Standard aliases if applicable (none specified, but `fmmod` might be `fmenv`).

**Draft Content for `lang_synthesis.kt`:**

```kotlin
package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue

// Flag for initialization
var strudelLangSynthesisInit = false

// -- partials() ----------------------------------------------------------------

private val partialsMutation = voiceModifier { input ->
    val newVal = when (input) {
        is List<*> -> input.mapNotNull { (it as? Number)?.toDouble()?.asVoiceValue() }.asVoiceValue()
        else -> input?.asVoiceValue() // Handle other cases or parsing
    }
    copy(partials = newVal)
}

// Implement: StrudelPattern.partials, partials (function), String.partials
// Hint: Look at `ratio` in lang_structural.kt for handling string parsing if we want "1 0.5" support
// Or just support List/Varargs.

// -- phases() ------------------------------------------------------------------

// Similar to partials

// -- FM Parameters -------------------------------------------------------------

// fmh() - Harmonicity Ratio
// fmattack(), fmdecay(), fmsustain(), fmenv()
// Implement using the standard applyNumericalParam pattern found in lang_effects.kt
```

#### 4. Tests: `LangSynthesisSpec.kt`

**Target File:** Create `strudel/src/commonTest/kotlin/lang/LangSynthesisSpec.kt`

Write tests that verify the DSL functions correctly set the `StrudelVoiceData` properties.

**Test Cases to Implement:**

1. **Additive Synthesis**:
    * `s("sine").partials(listOf(1.0, 0.5, 0.25))` -> Check `firstEvent.data.partials` is a Sequence of
      `1.0, 0.5, 0.25`.
    * `s("sine").phases(listOf(0.0, 3.14))` -> Check `firstEvent.data.phases`.

2. **FM Synthesis**:
    * `note("c3").fmh(2)` -> Check `fmh` is 2.0.
    * `note("c3").fmattack(0.1).fmdecay(0.2).fmsustain(0.5).fmenv(100)` -> Check all envelope fields.

3. **Control Patterns (if supported)**:
    * `note("c3").fmh(sine.range(1, 4))` -> Check if it compiles and produces a ControlValue (if your implementation
      supports dynamic parameters for FM).

**Test Style Reference:**
Look at `LangAdsrSpec.kt` (if it exists) or `LangGainSpec` in `index_commonTest.kt` (inferred).

```kotlin
// Example test style
class LangSynthesisSpec : FunSpec({
    test("partials should set partials list") {
        val pat = s("sine").partials(listOf(1, 0.5))
        val event = pat.query(0.0, 1.0).first()

        // Assert event.data.partials contains [1.0, 0.5]
    }

    test("fmh should set harmonicity ratio") {
        val pat = note("a4").fmh(1.5)
        val event = pat.query(0.0, 1.0).first()

        event.data.fmh shouldBe 1.5
    }
})
```
