# Strudel Audio Effects - Dynamics Implementation Plan

This document outlines the tasks required to complete the `Dynamics` section of the Strudel Kotlin port.

## 🎯 Goal

Implement `velocity`, `postgain`, and `compressor` parameters.

**Features:**

- `velocity` - Volume scaling (0-1), acts as a multiplier to `gain`.
- `postgain` - Gain applied after voice processing, before the orbit bus.
- `compressor` - Dynamic range compression settings for the orbit.

---

## 🛠️ Implementation Steps

### 1. Data Structure Updates

#### A. Modify `StrudelVoiceData`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Add the following fields to the `StrudelVoiceData` data class and its `empty` companion object:

- `val velocity: Double?`
- `val postGain: Double?`
- `val compressor: String?` (Stores the "threshold:ratio:knee:attack:release" string)

Update the `merge` function to include these new fields.

#### B. Modify `VoiceData` (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add the corresponding fields to the `VoiceData` data class and its `empty` companion object:

- `val velocity: Double?`
- `val postGain: Double?`
- `val compressor: String?`

#### C. Update Mapping Logic

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Update the `toVoiceData()` function to map the new fields.

### 2. DSL Implementation

**File:** `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`
*(Note: Check if this file exists and contains `gain`. If not, create it or add to `lang_effects.kt`)*

Implement the following DSL functions.

#### A. `velocity`

- **Type:** Numerical
- **Field:** `velocity`
- **Functions:**
    - `StrudelPattern.velocity()`
    - `velocity()` (standalone)
    - `String.velocity()`

#### B. `postgain`

- **Type:** Numerical
- **Field:** `postGain`
- **Functions:**
    - `StrudelPattern.postgain()`
    - `postgain()` (standalone)
    - `String.postgain()`

#### C. `compressor`

- **Type:** String (Control Pattern)
- **Field:** `compressor`
- **Implementation:** Use `applyControlFromParams` (similar to `tremoloShape` or `vowel`) to handle string values.
- **Functions:**
    - `StrudelPattern.compressor()`
    - `compressor()` (standalone)
    - `String.compressor()`

### 3. Verification

- Create a new test file `strudel/src/commonTest/kotlin/lang/LangDynamicsSpec.kt`.
- Add tests to verify that:
    - The DSL functions correctly set the values in the pattern.
    - `toVoiceData()` correctly transfers the values.

---

## 📝 Code Snippets (Reference)

**DSL Example (`compressor`):**

```kotlin
private val compressorMutation = voiceModifier { shape ->
    copy(compressor = shape?.toString())
}

private fun applyCompressor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyControlFromParams(args, compressorMutation) { src, ctrl ->
        src.copy(compressor = ctrl.compressor)
    }
}
```
