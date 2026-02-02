# Synthesis Functions Implementation Plan

## Overview

Implement additive and FM synthesis parameters for the Strudel Kotlin port:

- **Additive synthesis**: `partials()`, `phases()` - Define harmonic content and phase offsets
- **FM synthesis**: `fmh()`, `fmattack()`, `fmdecay()`, `fmsustain()`, `fmenv()` - FM modulation control

## Implementation Order

1. Update data models (StrudelVoiceData, VoiceData)
2. Update JS bridge (GraalStrudelPattern)
3. Create DSL functions (lang_synthesis.kt)
4. Add tests (LangSynthesisSpec.kt)
5. Update TODOS.MD

---

## 1. Update StrudelVoiceData

**File**: `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

**Add fields** (after existing synthesis parameters, around line 100):

```kotlin
// Additive Synthesis
val partials: StrudelVoiceValue? = null,
val phases: StrudelVoiceValue? = null,

// FM Synthesis
val fmh: Double? = null,           // Harmonicity ratio
val fmAttack: Double? = null,
val fmDecay: Double? = null,
val fmSustain: Double? = null,
val fmEnv: Double? = null,
```

**Update companion object `empty`**:

```kotlin
partials = null,
phases = null,
fmh = null,
fmAttack = null,
fmDecay = null,
fmSustain = null,
fmEnv = null,
```

**Update `merge()` function**:

```kotlin
partials = other.partials ?: partials,
phases = other.phases ?: phases,
fmh = other.fmh ?: fmh,
fmAttack = other.fmAttack ?: fmAttack,
fmDecay = other.fmDecay ?: fmDecay,
fmSustain = other.fmSustain ?: fmSustain,
fmEnv = other.fmEnv ?: fmEnv,
```

**Update `toVoiceData()` function**:

```kotlin
partials = partials,
phases = phases,
fmh = fmh,
fmAttack = fmAttack,
fmDecay = fmDecay,
fmSustain = fmSustain,
fmEnv = fmEnv,
```

---

## 2. Update VoiceData (Audio Bridge)

**File**: `audio_bridge/src/commonMain/kotlin/VoiceData.kt`

**Add fields** (after existing synthesis parameters):

```kotlin
val partials: StrudelVoiceValue? = null,
val phases: StrudelVoiceValue? = null,
val fmh: Double? = null,
val fmAttack: Double? = null,
val fmDecay: Double? = null,
val fmSustain: Double? = null,
val fmEnv: Double? = null,
```

**Update companion object `empty`**:

```kotlin
partials = null,
phases = null,
fmh = null,
fmAttack = null,
fmDecay = null,
fmSustain = null,
fmEnv = null,
```

---

## 3. Update GraalStrudelPattern (JS Bridge)

**File**: `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt`

**Add parsing in `toStrudelEvent()` function** (in the voice data extraction section):

```kotlin
// Additive Synthesis
val partials = value.safeGetMember("partials")?.asVoiceValue()
val phases = value.safeGetMember("phases")?.asVoiceValue()

// FM Synthesis
val fmh = value.safeGetMember("fmh").safeNumberOrNull()
val fmAttack = value.safeGetMember("fmattack").safeNumberOrNull()
val fmDecay = value.safeGetMember("fmdecay").safeNumberOrNull()
val fmSustain = value.safeGetMember("fmsustain").safeNumberOrNull()
val fmEnv = value.safeGetMember("fmenv").safeNumberOrNull()
```

**Add to StrudelVoiceData constructor**:

```kotlin
partials = partials,
phases = phases,
fmh = fmh,
fmAttack = fmAttack,
fmDecay = fmDecay,
fmSustain = fmSustain,
fmEnv = fmEnv,
```

---

## 4. Create DSL Functions

**File**: Create `strudel/src/commonMain/kotlin/lang/lang_synthesis.kt`

### partials()

- Accepts: List<Number>, String (space-separated), or pattern
- Converts to StrudelVoiceValue.Seq for lists
- Supports control patterns

### phases()

- Similar to partials()
- Accepts phase offsets in radians

### FM Functions

All follow standard numerical parameter pattern:

- `fmh()` - FM harmonicity ratio
- `fmattack()` - FM envelope attack time
- `fmdecay()` - FM envelope decay time
- `fmsustain()` - FM envelope sustain level
- `fmenv()` - FM modulation depth

**Pattern**: Each function needs:

- Mutation function
- Apply function using applyNumericalParam
- Pattern extension
- Function extension
- String extension

---

## 5. Create Tests

**File**: Create `strudel/src/commonTest/kotlin/lang/LangSynthesisSpec.kt`

**Test cases**:

### Additive Synthesis

```kotlin
"partials() with list" - Set[1.0, 0.5, 0.25] and verify
"partials() with string" - Parse "1 0.5 0.25" and verify
"phases() with list" - Set[0.0, 1.57, 3.14] and verify
```

### FM Synthesis

```kotlin
"fmh() sets harmonicity ratio" - Verify fmh = 2.0
"fmattack() sets attack time" - Verify fmAttack = 0.1
"fmdecay() sets decay time" - Verify fmDecay = 0.2
"fmsustain() sets sustain level" - Verify fmSustain = 0.7
"fmenv() sets modulation depth" - Verify fmEnv = 100.0
"FM envelope chain" - Test all parameters together
```

---

## 6. Add JS Compatibility Tests

**File**: `strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt`

Add examples:

```kotlin
// Additive Synthesis
Example("partials basic", """s("sine").partials([1, 0.5, 0.25])"""),
Example("phases basic", """s("sine").phases([0, 1.57, 3.14])"""),

// FM Synthesis
Example("fmh basic", """note("c3").fmh(2)"""),
Example("FM envelope", """note("c3").fmattack(0.1).fmdecay(0.2).fmsustain(0.7).fmenv(100)"""),
```

---

## Implementation Notes

1. **List/Array Handling**: For `partials` and `phases`, need to handle:
    - Kotlin lists: `listOf(1.0, 0.5)`
    - JS arrays from GraalVM
    - String parsing: "1 0.5 0.25"

2. **StrudelVoiceValue.Seq**: Use for storing list of numbers
    - Check existing usage in codebase
    - Follow pattern from other list-based parameters

3. **Control Patterns**: Consider if FM parameters should support dynamic patterns
    - Standard approach: support control patterns for all numerical parameters
    - Use `applyNumericalParam` helper

4. **Audio Engine**: No DSP changes needed in this phase
    - Data structures only
    - Audio processing can be implemented later

---

## Estimated Effort

- Data model updates: ~30 minutes (straightforward field additions)
- JS bridge updates: ~20 minutes (follow existing patterns)
- DSL functions: ~2 hours (7 functions, each ~20 lines with extensions)
- Tests: ~1 hour (12+ test cases)
- Documentation: ~15 minutes

**Total**: ~4 hours

---

## Success Criteria

- ✅ All fields accessible via DSL
- ✅ JS bridge correctly parses parameters
- ✅ All tests passing
- ✅ No compilation errors
- ✅ JS compatibility examples added (even if marked skip)
