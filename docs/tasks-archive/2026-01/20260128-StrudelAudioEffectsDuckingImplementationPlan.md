# Strudel Audio Effects - Ducking / Sidechain Implementation Plan

This document outlines the tasks required to complete the `Global Effects - Duck / Sidechain` section of the Strudel
Kotlin port.

## 🎯 Goal

Implement the DSL and data structures for cross-orbit sidechaining (ducking).

**Features:**

- `duckorbit` (alias: `duck`) - The target orbit ID to listen to (the source of the sidechain signal).
- `duckattack` (alias: `duckatt`) - How fast the volume returns to normal after ducking.
- `duckdepth` - How strongly the signal is ducked (0.0 = no ducking, 1.0 = full silence).

---

## 🛠️ Implementation Steps

### 1. Data Structure Updates

#### A. Modify `StrudelVoiceData`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Add the following fields to the `StrudelVoiceData` data class and its `empty` companion object:

- `val duckOrbit: Int?`
- `val duckAttack: Double?`
- `val duckDepth: Double?`

Update the `merge` function to include these new fields.

#### B. Modify `VoiceData` (Audio Bridge)

**File:** `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

Add the corresponding fields to the `VoiceData` data class and its `empty` companion object:

- `val duckOrbit: Int?`
- `val duckAttack: Double?`
- `val duckDepth: Double?`

#### C. Update Mapping Logic

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

Update the `toVoiceData()` function to map the new fields.

### 2. DSL Implementation

**File:** `strudel/src/commonMain/kotlin/lang/lang_effects.kt`
*(Or `lang_dynamics.kt` if created in the previous step)*

Implement the following DSL functions.

#### A. `duckorbit` / `duck`

- **Type:** Integer (Orbit Index)
- **Field:** `duckOrbit`
- **Functions:**
    - `StrudelPattern.duckorbit()`
    - `duckorbit()` (standalone)
    - `String.duckorbit()`
    - **Aliases:** `duck` (pattern, standalone, string)

#### B. `duckattack` / `duckatt`

- **Type:** Numerical (Time in seconds)
- **Field:** `duckAttack`
- **Functions:**
    - `StrudelPattern.duckattack()`
    - `duckattack()` (standalone)
    - `String.duckattack()`
    - **Aliases:** `duckatt` (pattern, standalone, string)

#### C. `duckdepth`

- **Type:** Numerical (0.0 to 1.0)
- **Field:** `duckDepth`
- **Functions:**
    - `StrudelPattern.duckdepth()`
    - `duckdepth()` (standalone)
    - `String.duckdepth()`

### 3. Verification

- Create a new test file `strudel/src/commonTest/kotlin/lang/LangDuckingSpec.kt`.
- Add tests to verify that:
    - `duck(0)` sets `duckOrbit` to `0`.
    - `duckattack` and `duckdepth` set their respective doubles.
    - `toVoiceData()` correctly transfers the values.

---

## 📝 Code Snippets (Reference)

**Data Fields:**

```kotlin
// StrudelVoiceData.kt & VoiceData.kt
val duckOrbit: Int?,
val duckAttack: Double?,
val duckDepth: Double?,
```

**DSL Example (`duck`):**

```kotlin
private val duckOrbitMutation = voiceModifier {
    copy(duckOrbit = it?.asInt())
}

private fun applyDuckOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = duckOrbitMutation,
        getValue = { duckOrbit },
        setValue = { v, _ -> copy(duckOrbit = v.toInt()) }, // Note conversion
    )
}
// ...
```
