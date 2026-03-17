# Task: Make StrudelVoiceData Mutable

## Goal

Convert `StrudelVoiceData` from an immutable `data class` with `val` properties and `copy()`
to a mutable class with `var` properties and a `clone()` method. This eliminates the massive
overhead of Kotlin's generated `copy()` on a class with 100+ fields.

## Current State

- **File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`
- `@Serializable data class StrudelVoiceData(val ..., val ..., ...)`
- ~100 nullable properties (Double?, String?, Int?, Boolean?, one StrudelVoiceValue?)
- Has a `companion object { val empty = StrudelVoiceData(...all nulls...) }`
- Has methods: `merge()`, `isTruthy()`, `isNotTruthy()`, `toVoiceData()`, `resolveVowelBands()`
- Every property modification goes through `copy()` which copies ALL ~100 fields

## Target State

- `@Serializable class StrudelVoiceData(var ..., var ..., ...)` (NOT a data class)
- All properties become `var`
- Remove `data` keyword (prevents auto-generated `copy()`, `equals()`, `hashCode()`, `toString()`)
- Add explicit `clone()` method
- Add explicit `equals()` and `hashCode()` if needed (check if used in sets/maps - likely not needed)

---

## Step-by-Step Implementation

### Step 1: Modify StrudelVoiceData class definition

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

1. Change `data class` to `class`
2. Change all `val` properties to `var`
3. Add a `clone()` method that creates a new instance copying all fields
4. The `empty` companion object stays — but now returns a shared instance. Since it's mutable,
   callers must NEVER mutate `empty` directly. Instead they must call `empty.clone()` first.
   **Better approach:** Keep `empty` as-is but make all call sites that modify it use `clone()`.
   Actually, the safest approach: make a `fun empty(): StrudelVoiceData` factory that returns
   a fresh all-nulls instance each time. This prevents accidental mutation of a shared singleton.

**Decision on `empty`:** Replace `val empty` with `fun empty()` that returns a new instance each time.
This is the safest approach. The cost of creating one all-nulls instance is negligible compared to
the copy() savings.

```kotlin
@Serializable
class StrudelVoiceData(
    // note, scale, freq
    var note: String? = null,
    var freqHz: Double? = null,
    var scale: String? = null,
    // ... all other fields with = null defaults ...
    var value: StrudelVoiceValue? = null,
) {
    companion object {
        /** Creates a new empty instance with all fields null */
        fun empty(): StrudelVoiceData = StrudelVoiceData()
    }

    /** Creates a shallow copy of this voice data */
    fun clone(): StrudelVoiceData = StrudelVoiceData(
        note = note,
        freqHz = freqHz,
        scale = scale,
        // ... all fields ...
        value = value,
    )

    // merge(), isTruthy(), toVoiceData() etc. stay as-is
}
```

Since all parameters now have defaults (`= null`), the constructor can be called with no args
for an empty instance, or with named args for partial construction.

### Step 2: Update `merge()` to mutate in-place

Currently `merge()` returns a new instance. Change to mutate `this` in-place and return `this`:

```kotlin
fun mergeFrom(other: StrudelVoiceData): StrudelVoiceData {
    if (other.note != null) note = other.note
    if (other.freqHz != null) freqHz = other.freqHz
    // ... all fields except patternId ...
    // patternId is NEVER merged (preserve original source ID)
    if (other.value != null) value = other.value
    return this
}
```

Keep the old `merge()` name but change its semantics to mutate-in-place. All current call sites
already use the return value, so `a.mergeFrom(b)` will still work in expression context.

### Step 3: Update VoiceModifierFn type and mutations

**File:** `strudel/src/commonMain/kotlin/lang/lang.kt` (line ~81)

- `VoiceModifierFn` is `StrudelVoiceData.(Any?) -> StrudelVoiceData`
- This stays the same signature — the lambdas just mutate `this` and return `this` instead of `copy()`

**All mutation lambdas** (60+ across lang_dynamics.kt, lang_effects.kt, lang_filters.kt,
lang_sample.kt, lang_synthesis.kt, lang_tonal.kt, lang_vowel.kt) change from:

```kotlin
// OLD
private val gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }
```

to:

```kotlin
// NEW — mutate in place, return this
private val gainMutation = voiceModifier {
    gain = it?.asDoubleOrNull()
    this
}
```

**Complete list of simple single-field mutations to update:**

| File                | Mutation                | Field(s)                                                                                                                                 |
|---------------------|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `lang_dynamics.kt`  | `gainMutation`          | `gain`                                                                                                                                   |
| `lang_dynamics.kt`  | `panMutation`           | `pan`                                                                                                                                    |
| `lang_dynamics.kt`  | `velocityMutation`      | `velocity`                                                                                                                               |
| `lang_dynamics.kt`  | `postgainMutation`      | `postGain`                                                                                                                               |
| `lang_dynamics.kt`  | `compressorMutation`    | `compressor`                                                                                                                             |
| `lang_dynamics.kt`  | `unisonMutation`        | `voices`                                                                                                                                 |
| `lang_dynamics.kt`  | `detuneMutation`        | `freqSpread`                                                                                                                             |
| `lang_dynamics.kt`  | `spreadMutation`        | `panSpread` (check actual field)                                                                                                         |
| `lang_dynamics.kt`  | `densityMutation`       | `density` (check actual field)                                                                                                           |
| `lang_dynamics.kt`  | `attackMutation`        | `attack`                                                                                                                                 |
| `lang_dynamics.kt`  | `decayMutation`         | `decay`                                                                                                                                  |
| `lang_dynamics.kt`  | `sustainMutation`       | `sustain`                                                                                                                                |
| `lang_dynamics.kt`  | `releaseMutation`       | `release`                                                                                                                                |
| `lang_dynamics.kt`  | `orbitMutation`         | `orbit`                                                                                                                                  |
| `lang_dynamics.kt`  | `duckOrbitMutation`     | `duckOrbit`                                                                                                                              |
| `lang_dynamics.kt`  | `duckAttackMutation`    | `duckAttack`                                                                                                                             |
| `lang_dynamics.kt`  | `duckDepthMutation`     | `duckDepth`                                                                                                                              |
| `lang_effects.kt`   | `distortMutation`       | `distort`                                                                                                                                |
| `lang_effects.kt`   | `crushMutation`         | `crush`                                                                                                                                  |
| `lang_effects.kt`   | `coarseMutation`        | `coarse`                                                                                                                                 |
| `lang_effects.kt`   | `roomMutation`          | `room`                                                                                                                                   |
| `lang_effects.kt`   | `roomSizeMutation`      | `roomSize`                                                                                                                               |
| `lang_effects.kt`   | `roomFadeMutation`      | `roomFade`                                                                                                                               |
| `lang_effects.kt`   | `roomLpMutation`        | `roomLp`                                                                                                                                 |
| `lang_effects.kt`   | `roomDimMutation`       | `roomDim`                                                                                                                                |
| `lang_effects.kt`   | `iResponseMutation`     | `iResponse`                                                                                                                              |
| `lang_effects.kt`   | `delayMutation`         | (multi-field, see below)                                                                                                                 |
| `lang_effects.kt`   | `delayTimeMutation`     | `delayTime`                                                                                                                              |
| `lang_effects.kt`   | `delayFeedbackMutation` | `delayFeedback`                                                                                                                          |
| `lang_effects.kt`   | `phaserMutation`        | `phaserRate`                                                                                                                             |
| `lang_effects.kt`   | `phaserDepthMutation`   | `phaserDepth`                                                                                                                            |
| `lang_effects.kt`   | `phaserCenterMutation`  | `phaserCenter`                                                                                                                           |
| `lang_effects.kt`   | `phaserSweepMutation`   | `phaserSweep`                                                                                                                            |
| `lang_effects.kt`   | `tremoloSyncMutation`   | `tremoloSync`                                                                                                                            |
| `lang_effects.kt`   | `tremoloDepthMutation`  | `tremoloDepth`                                                                                                                           |
| `lang_effects.kt`   | `tremoloSkewMutation`   | `tremoloSkew`                                                                                                                            |
| `lang_effects.kt`   | `tremoloPhaseMutation`  | `tremoloPhase`                                                                                                                           |
| `lang_effects.kt`   | `tremoloShapeMutation`  | `tremoloShape` (check, may be via _applyControlFromParams)                                                                               |
| `lang_filters.kt`   | `resonanceMutation`     | `resonance`                                                                                                                              |
| `lang_filters.kt`   | `hresonanceMutation`    | `hresonance`                                                                                                                             |
| `lang_filters.kt`   | `bandqMutation`         | `bandq`                                                                                                                                  |
| `lang_filters.kt`   | `lpattackMutation`      | `lpattack`                                                                                                                               |
| `lang_filters.kt`   | `lpdecayMutation`       | `lpdecay`                                                                                                                                |
| `lang_filters.kt`   | `lpsustainMutation`     | `lpsustain`                                                                                                                              |
| `lang_filters.kt`   | `lpreleaseMutation`     | `lprelease`                                                                                                                              |
| `lang_filters.kt`   | `lpenvMutation`         | `lpenv`                                                                                                                                  |
| `lang_filters.kt`   | `hpattackMutation`      | `hpattack`                                                                                                                               |
| `lang_filters.kt`   | `hpdecayMutation`       | `hpdecay`                                                                                                                                |
| `lang_filters.kt`   | `hpsustainMutation`     | `hpsustain`                                                                                                                              |
| `lang_filters.kt`   | `hpreleaseMutation`     | `hprelease`                                                                                                                              |
| `lang_filters.kt`   | `hpenvMutation`         | `hpenv`                                                                                                                                  |
| `lang_filters.kt`   | `bpattackMutation`      | `bpattack`                                                                                                                               |
| `lang_filters.kt`   | `bpdecayMutation`       | `bpdecay`                                                                                                                                |
| `lang_filters.kt`   | `bpsustainMutation`     | `bpsustain`                                                                                                                              |
| `lang_filters.kt`   | `bpreleaseMutation`     | `bprelease`                                                                                                                              |
| `lang_filters.kt`   | `bpenvMutation`         | `bpenv`                                                                                                                                  |
| `lang_sample.kt`    | `beginMutation`         | `begin`                                                                                                                                  |
| `lang_sample.kt`    | `endMutation`           | `end`                                                                                                                                    |
| `lang_sample.kt`    | `speedMutation`         | `speed`                                                                                                                                  |
| `lang_sample.kt`    | `unitMutation`          | `unit`                                                                                                                                   |
| `lang_sample.kt`    | `loopMutation`          | `loop`                                                                                                                                   |
| `lang_sample.kt`    | `loopBeginMutation`     | `loopBegin`                                                                                                                              |
| `lang_sample.kt`    | `loopEndMutation`       | `loopEnd`                                                                                                                                |
| `lang_sample.kt`    | `loopAtSpeedMutation`   | multi-field (`loop`, `speed`)                                                                                                            |
| `lang_sample.kt`    | `cutMutation`           | `cut`                                                                                                                                    |
| `lang_synthesis.kt` | `fmhMutation`           | `fmh`                                                                                                                                    |
| `lang_synthesis.kt` | `fmattackMutation`      | `fmAttack`                                                                                                                               |
| `lang_synthesis.kt` | `fmdecayMutation`       | `fmDecay`                                                                                                                                |
| `lang_synthesis.kt` | `fmsustainMutation`     | `fmSustain`                                                                                                                              |
| `lang_synthesis.kt` | `fmenvMutation`         | `fmEnv`                                                                                                                                  |
| `lang_tonal.kt`     | various                 | `scale`, `bank`, `legato`, `vibrato`, `vibratoMod`, `pAttack`, `pDecay`, `pRelease`, `pEnv`, `pCurve`, `pAnchor`, `accelerate`, `freqHz` |
| `lang_vowel.kt`     | vowelMutation           | `vowel`                                                                                                                                  |

**Multi-field mutations** (these use `copy()` with multiple fields):

| File                     | Location                     | Fields                                |
|--------------------------|------------------------------|---------------------------------------|
| `lang_filters.kt:22-26`  | `lpfMutation` (colon form)   | `cutoff`, `resonance`, `lpenv`        |
| `lang_filters.kt:36-40`  | `applyLpf` combine           | `cutoff`, `resonance`, `lpenv`        |
| `lang_filters.kt:404+`   | `hpfMutation` (colon form)   | `hcutoff`, `hresonance`, `hpenv`      |
| `lang_filters.kt:699+`   | `bandfMutation` (colon form) | `bandf`, `bandq`, `bpenv`             |
| `lang_effects.kt:1632+`  | `delayMutation` (colon form) | `delay`, `delayTime`, `delayFeedback` |
| `lang_effects.kt:3763`   | `tremoloShapeMutation`       | `tremoloShape`                        |
| `lang_dynamics.kt:1397+` | `warmthMutation`             | `warmth`                              |

For multi-field mutations, change from:

```kotlin
copy(cutoff = x, resonance = y, lpenv = z)
```

to:

```kotlin
cutoff = x; resonance = y; lpenv = z; this
```

### Step 4: Update `_liftNumericField` / `_liftStringField` — CRITICAL CLONE POINT

**File:** `strudel/src/commonMain/kotlin/StrudelPattern.kt`

These functions do:

```kotlin
sourceEvent.copy(data = sourceEvent.data.update(value))
```

The `update` lambda is `StrudelVoiceData.(Double?) -> StrudelVoiceData`. With mutable data,
this lambda will mutate the voice data in-place. But `sourceEvent.data` might be shared
across events, so we MUST clone before mutating.

**New pattern:**

```kotlin
val clonedData = sourceEvent.data.clone()
clonedData.update(value)
sourceEvent.copy(data = clonedData)
```

Wait — `sourceEvent` is also a `data class` (`StrudelPatternEvent`). Its `.copy(data = ...)`
is cheap (only 4 fields). So `StrudelPatternEvent` stays as a data class.

**But even better:** since the update lambda now mutates in-place, we need to understand
whether the same StrudelVoiceData instance is ever shared. Let's trace:

- `queryArcContextual()` returns a `List<StrudelPatternEvent>`
- Each event has its own `data: StrudelVoiceData`
- Events from `AtomicPattern` all share the SAME `data` instance (one AtomicPattern = one data)
- Events can also share data through `_squeezeJoin` / `_innerJoin` copy patterns

**Therefore: we MUST clone before any mutation.** The key insight is that the clone happens
much less frequently than current copy() calls, because:

- Current: `copy()` creates a full clone every time, even for single-field changes
- New: `clone()` once, then mutate multiple fields cheaply

**Update `_liftNumericField`** (line ~994-1007):

```kotlin
fun StrudelPattern._liftNumericField(
    args: List<StrudelDslArg<Any?>>,
    update: StrudelVoiceData.(Double?) -> StrudelVoiceData,
): StrudelPattern {
    if (args.isEmpty()) return this
    val control = args.toPattern()
    return _outerJoin(control) { sourceEvent, controlEvent ->
        val value = controlEvent?.data?.value?.asDouble
        val newData = sourceEvent.data.clone().update(value)
        sourceEvent.copy(data = newData)
            .prependLocations(controlEvent?.sourceLocations)
    }
}
```

Same pattern for `_liftStringField` (line ~1039-1052) and `_liftOrReinterpretNumericalField`
and `_liftOrReinterpretStringField`.

### Step 5: Update `reinterpretVoice`

**File:** `strudel/src/commonMain/kotlin/pattern/ReinterpretPattern.kt` (line 21-22)

```kotlin
fun StrudelPattern.reinterpretVoice(interpret: (voice: StrudelVoiceData) -> StrudelVoiceData): StrudelPattern =
    ReinterpretPattern(this, { evt, _ -> evt.copy(data = interpret(evt.data)) })
```

Callers of `reinterpretVoice` pass a lambda that currently returns a new `copy()`.
With mutable data, these lambdas will mutate in-place. The `interpret` function receives
the voice data and the caller is responsible for cloning if needed.

**Decision:** The `reinterpretVoice` implementation should clone before passing to the lambda:

```kotlin
fun StrudelPattern.reinterpretVoice(interpret: (voice: StrudelVoiceData) -> StrudelVoiceData): StrudelPattern =
    ReinterpretPattern(this) { evt, _ -> evt.copy(data = interpret(evt.data.clone())) }
```

This way, all `reinterpretVoice` callers can safely mutate without worrying about shared state.

### Step 6: Update `_squeezeJoin` and `_innerJoin`

**File:** `strudel/src/commonMain/kotlin/StrudelPattern.kt` (lines 695-696, 738-739)

Current:

```kotlin
innerEvent.copy(data = outerEvent.data.copy(value = innerEvent.data.value))
```

New:

```kotlin
val newData = outerEvent.data.clone()
newData.value = innerEvent.data.value
innerEvent.copy(data = newData)
```

### Step 7: Update `_liftData` (merge in bind)

**File:** `strudel/src/commonMain/kotlin/StrudelPattern.kt` (lines 950-958)

Current:

```kotlin
val mergedData = sourceEvent.data.merge(controlEvent.data)
sourceEvent.copy(data = mergedData)
```

New (since `mergeFrom` now mutates in-place):

```kotlin
val mergedData = sourceEvent.data.clone().mergeFrom(controlEvent.data)
sourceEvent.copy(data = mergedData)
```

### Step 8: Update `_applyControlFromParams`

**File:** `strudel/src/commonMain/kotlin/StrudelPattern.kt` (lines 1105-1120)

Current:

```kotlin
val mapper: (StrudelVoiceData) -> StrudelVoiceData = { data ->
    val value = data.value
    if (value != null) data.modify(value) else data
}
```

New (clone before modify):

```kotlin
val mapper: (StrudelVoiceData) -> StrudelVoiceData = { data ->
    val value = data.value
    if (value != null) data.clone().modify(value) else data
}
```

### Step 9: Update pattern creation sites (StrudelVoiceData.empty → empty())

All sites that do `StrudelVoiceData.empty.copy(...)` become `StrudelVoiceData.empty().apply { ... }`:

**Files and locations:**

| File                        | Line(s)       | Current                                                                            | New                                                                                                                                                                                                   |
|-----------------------------|---------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lang_helpers.kt`           | 96            | `StrudelVoiceData.empty.voiceValueModifier(text)`                                  | `StrudelVoiceData.empty().voiceValueModifier(text)`                                                                                                                                                   |
| `lang_helpers.kt`           | 314           | `StrudelVoiceData.empty.modify(text).copy(patternId = sourceId)`                   | `StrudelVoiceData.empty().apply { modify(text); patternId = sourceId }` — BUT modify returns a new/mutated instance. Better: `StrudelVoiceData.empty().modify(text).also { it.patternId = sourceId }` |
| `lang_structural.kt`        | 1346          | `StrudelVoiceData.empty.copy(value = value?.asVoiceValue())`                       | `StrudelVoiceData.empty().apply { this.value = value?.asVoiceValue() }`                                                                                                                               |
| `lang_structural.kt`        | 2498          | `StrudelVoiceData.empty.copy(value = it.asVoiceValue())`                           | `StrudelVoiceData.empty().apply { value = it.asVoiceValue() }`                                                                                                                                        |
| `lang_structural.kt`        | 3688          | same pattern                                                                       | same change                                                                                                                                                                                           |
| `lang_structural.kt`        | 3737          | same pattern                                                                       | same change                                                                                                                                                                                           |
| `lang_structural.kt`        | 3818          | `StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Seq(bitList))`              | `StrudelVoiceData.empty().apply { value = StrudelVoiceValue.Seq(bitList) }`                                                                                                                           |
| `lang_structural.kt`        | 4068, 4154    | `StrudelVoiceData.empty.copy(value = control.value)`                               | `StrudelVoiceData.empty().apply { value = control.value }`                                                                                                                                            |
| `lang_structural.kt`        | 4908          | `StrudelVoiceData.empty.copy(value = 1.asVoiceValue())`                            | `StrudelVoiceData.empty().apply { value = 1.asVoiceValue() }`                                                                                                                                         |
| `lang_structural_addons.kt` | 100           | `StrudelVoiceData.empty.copy(value = ...)`                                         | `StrudelVoiceData.empty().apply { value = ... }`                                                                                                                                                      |
| `SoloPattern.kt`            | 60-65         | `StrudelVoiceData.empty.copy(note=..., freqHz=..., sound=..., gain=..., solo=...)` | `StrudelVoiceData.empty().apply { note = ...; freqHz = ...; sound = ...; gain = ...; solo = ... }`                                                                                                    |
| `EuclideanPattern.kt`       | 175, 212, 292 | `StrudelVoiceData.empty.copy(value = ...)`                                         | `StrudelVoiceData.empty().apply { value = ... }`                                                                                                                                                      |
| `EuclideanMorphPattern.kt`  | 175           | same pattern                                                                       | same change                                                                                                                                                                                           |
| `AtomicPattern.kt`          | 32            | `StrudelVoiceData.empty.copy(value = value?.asVoiceValue())`                       | `StrudelVoiceData.empty().apply { value = value?.asVoiceValue() }`                                                                                                                                    |
| `ControlValueProvider.kt`   | 50            | `StrudelVoiceData.empty.copy(value = value)`                                       | `StrudelVoiceData.empty().apply { this.value = value }`                                                                                                                                               |
| `ContinuousPattern.kt`      | 55            | `StrudelVoiceData.empty.copy(value = value)`                                       | `StrudelVoiceData.empty().apply { this.value = value }`                                                                                                                                               |
| `RandrunPattern.kt`         | 67            | `StrudelVoiceData.empty.copy(value = value)`                                       | `StrudelVoiceData.empty().apply { this.value = value }`                                                                                                                                               |

### Step 10: Update event-level data modifications

Sites that do `event.copy(data = event.data.copy(field = x))`:

| File                 | Line           | Current                                                       | New                                                                                                                                                                                         |
|----------------------|----------------|---------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lang_arithmetic.kt` | 32             | `event.copy(data = event.data.copy(value = newVal))`          | `event.copy(data = event.data.clone().apply { value = newVal })`                                                                                                                            |
| `lang_arithmetic.kt` | 52             | `srcData.copy(value = newValue)`                              | `srcData.clone().apply { value = newValue }` (inside reinterpretVoice — clone handled by Step 5 if we clone in reinterpretVoice; but this lambda returns, so just mutate and return `this`) |
| `lang_random.kt`     | 239            | `sourceData.copy(value = result.asVoiceValue())`              | Same — mutate + return this (context is combiner lambda)                                                                                                                                    |
| `lang_random.kt`     | 330            | same                                                          | same                                                                                                                                                                                        |
| `lang_random.kt`     | 1321           | `evt.copy(data = evt.data.copy(value = value))`               | `evt.copy(data = evt.data.clone().apply { this.value = value })`                                                                                                                            |
| `lang_structural.kt` | 4557           | `event.copy(data = event.data.copy(value = ...))`             | `event.copy(data = event.data.clone().apply { value = ... })`                                                                                                                               |
| `lang_tonal.kt`      | 2007           | `event.data.copy(note=..., freqHz=..., chord=null, gain=...)` | `event.data.clone().apply { note = ...; freqHz = ...; chord = null; gain = ... }`                                                                                                           |
| `lang_tonal.kt`      | 163            | `it.resolveNote().copy(soundIndex = null, value = null)`      | `it.resolveNote().also { it.soundIndex = null; it.value = null }` (if resolveNote already cloned) or `.clone().apply { soundIndex = null; value = null }`                                   |
| `lang_tonal.kt`      | 229, 314, 1454 | various `.copy(...)`                                          | `.clone().apply { ... }`                                                                                                                                                                    |
| `SoloPattern.kt`     | 85-89          | `event.data.copy(solo = ..., patternId = ...)`                | `event.data.clone().apply { solo = ...; patternId = ... }`                                                                                                                                  |
| `lang_effects.kt`    | 1448           | `src.copy(iResponse = ctrl.iResponse)`                        | `src.clone().apply { iResponse = ctrl.iResponse }` — BUT check context: is `src` already a clone?                                                                                           |
| `lang_effects.kt`    | 1632           | `src.copy(...)` multi-field                                   | `src.clone().apply { ... }`                                                                                                                                                                 |
| `lang_effects.kt`    | 3763           | `src.copy(tremoloShape = ctrl.tremoloShape)`                  | same pattern                                                                                                                                                                                |
| `lang_filters.kt`    | 36-40          | `src.copy(cutoff=..., resonance=..., lpenv=...)`              | same pattern                                                                                                                                                                                |
| `lang_filters.kt`    | 422, 717       | similar multi-field                                           | same pattern                                                                                                                                                                                |
| `lang_dynamics.kt`   | 1397           | `src.copy(warmth = ...)`                                      | same pattern                                                                                                                                                                                |

### Step 11: Update `MergeVoiceDataPattern`

**File:** `strudel/src/commonMain/kotlin/lang/addons/pattern/MergeVoiceDataPattern.kt` (line 38-40)

Current:

```kotlin
srcEvt.copy(data = srcEvt.data.merge(ctrlEvt.data))
```

New:

```kotlin
srcEvt.copy(data = srcEvt.data.clone().mergeFrom(ctrlEvt.data))
```

### Step 12: Update `ControlPattern`

**File:** `strudel/src/commonMain/kotlin/pattern/ControlPattern.kt` (line 38)

The `combiner` lambda receives `(src: StrudelVoiceData, ctrl: StrudelVoiceData)` and returns
a new StrudelVoiceData. The clone responsibility depends on the combiner — but since
`ControlPattern` creates the final data, it should clone:

```kotlin
val mappedControl = mapper(ctrl.data)
val newData = combiner(src.data.clone(), mappedControl)
src.copy(data = newData)
```

Or ensure all combiner lambdas clone internally.

### Step 13: Update `voiceValueModifier` (default modifier)

**File:** `strudel/src/commonMain/kotlin/lang/lang_helpers.kt` (line 106-110)

Current:

```kotlin
val voiceValueModifier = voiceModifier {
    val result = (it?.asRationalOrNull() ?: it)?.asVoiceValue()
    copy(value = result)
}
```

New:

```kotlin
val voiceValueModifier = voiceModifier {
    val result = (it?.asRationalOrNull() ?: it)?.asVoiceValue()
    value = result
    this
}
```

### Step 14: Update test files

**File:** `strudel/src/commonTest/kotlin/StrudelVoiceDataSpec.kt`

All test `.copy()` calls need to change to `.clone().apply { ... }` or use the constructor directly.
The immutability test (line ~237-245) should be updated or removed since the class is now mutable.

Other test files with `.copy()`: `LangBpenvSpec.kt`, `VoiceDataSpec.kt`, various `LangHp*Spec`,
`LangBp*Spec`, `LangLp*Spec` files. Search for `StrudelVoiceData` in test files and update.

### Step 15: Handle @Serializable

Kotlin serialization works with both `class` and `data class`. Since all properties have
defaults (`= null`), the serializer will handle it. But verify:

- `@Serializable` annotation stays
- Constructor with all defaults works for deserialization
- No issues with mutable properties and kotlinx.serialization

---

## Where clone() IS needed (shared-state boundaries)

These are the **critical clone points** where a StrudelVoiceData instance might be shared:

1. **`reinterpretVoice`** — clones before passing to interpret lambda (Step 5)
2. **`_liftNumericField` / `_liftStringField`** — clone before update lambda (Step 4)
3. **`_squeezeJoin` / `_innerJoin`** — clone outer data before merging inner value (Step 6)
4. **`_liftData`** — clone before merge (Step 7)
5. **`_applyControlFromParams`** — clone before modify (Step 8)
6. **Event-level `copy(data = ...)`** — clone when creating modified data from shared source (Step 10)
7. **`StrudelVoiceData.empty()`** — returns fresh instance each time (Step 1)
8. **Any `combine`/`combiner` lambda** — clone the source before mutating (Steps 11-12)

## Where clone() is NOT needed (safe to mutate)

- Inside mutation lambdas called from `_liftNumericField` etc. (clone already happened upstream)
- Inside `reinterpretVoice` lambdas (clone already happened upstream)
- After `StrudelVoiceData.empty()` (fresh instance)
- After another `clone()` call

---

## Verification

1. Run `./gradlew :strudel:jvmTest` — all tests must pass
2. Run `./gradlew :strudel:jsTest` — browser tests must pass
3. Manually test live coding to verify no shared-state corruption

## Risk Areas

- **Shared state bugs:** If a clone is missed, two events could share the same mutable data,
  leading to one mutation affecting both. Symptoms: random wrong values, non-deterministic behavior.
- **AtomicPattern:** Creates events with the same `data` reference. If downstream code mutates
  without cloning, all events from that atom get corrupted. The clone points in Steps 4-8
  should prevent this.
- **Thread safety:** If patterns are queried from multiple threads, mutable state could race.
  Check if this is a concern (likely not in JS/single-thread, but check JVM usage).
