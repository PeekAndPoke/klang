# Strudel Factory Functions Implementation Plan

## Overview

Implement 5 advanced pattern operations in `lang_structural.kt`:

1. **stepcat/timeCat** - Weighted concatenation
2. **within** - Conditional time selection
3. **chunk** - Time-based segmentation (depends on within)
4. **echo/stut** - Echo effect with decay
5. **echoWith/stutWith** - Echo with custom transform

## Implementation Order

1. stepcat (independent)
2. within (independent)
3. chunk (depends on within)
4. echo (independent)
5. echoWith (independent)

---

## 1. stepcat / timeCat

**Purpose**: Concatenates patterns where each segment has a defined duration, then speeds up the result to fit exactly
one cycle.

**Aliases**: `timeCat`, `timecat`, `s_cat`

**Signature**: `stepcat([duration, pattern], ...)` or `stepcat(pattern, ...)` (defaults duration to 1)

**Implementation Strategy**:

```kotlin
1.Parse args into List < Pair < Double, StrudelPattern>>
2.Use applyArrange () to create sequence
        3.Calculate totalDuration = sum (durations)
4.Apply.fast(totalDuration) to compress into 1 cycle
```

**Example**:

```kotlin
timeCat([1, "a"], [3, "b"])
// "a" takes 25% (1/4), "b" takes 75% (3/4) of one cycle
```

**Location**: After `arrange()` in lang_structural.kt (around line 177)

---

## 2. within

**Purpose**: Applies a transformation function to events within a specific time window of the cycle, leaving other
events untouched.

**Signature**: `pattern.within(start, end, transform)`

**Parameters**:

- `start`: Double (0.0 to 1.0) - window start
- `end`: Double (0.0 to 1.0) - window end
- `transform`: (StrudelPattern) -> StrudelPattern - function to apply

**Implementation Strategy**:

```kotlin
1.Create window :
val window = AtomicPattern(...).compress(start, end)
2.Isolate inner events: val inner = this.maskAll(window)
3.Isolate outer events: val outer = this.bypass(window)
4.Transform inner :
val transformed = transform(inner)
5.Combine: StackPattern(listOf(outer, transformed))
```

**Example**:

```kotlin
n("0 1 2 3").within(0.5, 1.0) { it.rev() }
// First half unchanged, second half reversed
```

**Location**: After `zoom()` in lang_structural.kt (around line 907)

---

## 3. chunk / slowchunk

**Purpose**: Divides pattern into n parts and applies a function to one part per cycle (cycling through parts).

**Aliases**: `slowchunk`, `slowChunk`

**Signature**: `pattern.chunk(n, transform)`

**Parameters**:

- `n`: Int - number of chunks
- `transform`: (StrudelPattern) -> StrudelPattern - function to apply

**Implementation Strategy**:

```kotlin
1.Generate n patterns
2.For each i in 0 until n:
-start = i / n
-end = (i + 1) / n
-patterns[i] = this.within(start, end, transform)
3.Return applyCat (patterns)
```

**Example**:

```kotlin
n("0 1 2 3").chunk(4) { it.add(12) }
// Cycle 0: "12 1 2 3"
// Cycle 1: "0 13 2 3"
// Cycle 2: "0 1 14 3"
// Cycle 3: "0 1 2 15"
```

**Location**: After `within()` in lang_structural.kt

---

## 4. echo / stut

**Purpose**: Superimposes delayed and decayed versions of the pattern.

**Alias**: `stut`

**Signature**: `pattern.echo(times, delay, decay)`

**Parameters**:

- `times`: Int - number of echoes
- `delay`: Double - time offset for each echo (in cycles)
- `decay`: Double - gain multiplier for each echo (0.0 to 1.0)

**Implementation Strategy**:

```kotlin
1.Create times layers
2.For each i in 0 until times:
-layer[i] = this.late(delay * i).gain(decay.pow(i))
3.Return StackPattern (layers)
```

**Example**:

```kotlin
n("0").stut(4, 0.5, 0.5)
// Original + 3 echoes at 0.5, 1.0, 1.5 cycles with gain 1.0, 0.5, 0.25, 0.125
```

**Location**: After `chunk()` in lang_structural.kt

---

## 5. echoWith / stutWith

**Purpose**: Superimposes versions of the pattern modified by recursively applying a transform function.

**Aliases**: `stutWith`, `stutwith`, `echowith`

**Signature**: `pattern.echoWith(times, delay, transform)`

**Parameters**:

- `times`: Int - number of layers
- `delay`: Double - time offset for each layer
- `transform`: (StrudelPattern) -> StrudelPattern - function applied cumulatively

**Implementation Strategy**:

```kotlin
1.Initialize layers = mutableListOf (this)
2.
var current = this
3.repeat(times - 1):
-current = transform(current)
-layers.add(current.late(delay * (it + 1)))
4.Return StackPattern (layers)
```

**Example**:

```kotlin
n("0").stutWith(4, 0.125) { it.add(2) }
// Layer 0: 0
// Layer 1: 2 (at +0.125)
// Layer 2: 4 (at +0.25)
// Layer 3: 6 (at +0.375)
```

**Location**: After `echo()` in lang_structural.kt

---

## Testing Plan

Add to `JsCompatTestData.kt`:

```kotlin
// Factory functions
Example("stepcat basic", """timeCat([1, "c"], [2, "e"], [1, "g"])"""),
Example("stepcat with patterns", """stepcat([1, note("c")], [2, note("e g")])"""),
Example("stepcat with s_cat alias", """s_cat([1, "bd"], [3, "hh sd"])"""),

Example("within basic", """n("0 1 2 3").within(0.5, 1) { it.rev() }""", skip = true),
Example("within overlap", """n("0 1 2 3").within(0.2, 0.3) { it.add(12) }""", skip = true),

Example("chunk basic", """n("0 1 2 3").chunk(4) { it.add(12) }""", skip = true),
Example("chunk with 2 slices", """s("bd sd hh").chunk(2) { it.fast(2) }""", skip = true),

Example("echo basic", """n("0").stut(4, 0.5, 0.5)""", skip = true),
Example("echo short delay", """s("bd").echo(3, 0.125, 0.7)""", skip = true),

Example("echoWith basic", """n("0").stutWith(4, 0.125) { it.add(2) }""", skip = true),
Example("echoWith fast", """s("bd").echoWith(3, 0.25) { it.fast(1.5) }""", skip = true),
```

Note: `skip = true` for functions that take lambdas (JS can't compile these in Graal context)

---

## Implementation Notes

1. **Helper functions needed**:
    - `compress()` - already exists (see `zoom()` implementation)
    - `maskAll()` - already exists (line 609)
    - `bypass()` - already exists (line 806)
    - `late()` - need to check if exists (equivalent to `.early(-amount)`)
    - `gain()` - need to check if exists

2. **Pattern imports**:
    - StackPattern - already imported
    - TimeShiftPattern - might be needed for late()
    - AtomicPattern - already imported

3. **Kotlin.math imports needed**:
    - `kotlin.math.pow` for decay calculations

4. **Error handling**:
    - Validate n > 0 for chunk/echo
    - Validate start < end for within
    - Handle edge cases (n=1, empty patterns)

---

## File Locations

**Primary file**: `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/lang/lang_structural.kt`

**Test file**: `/opt/dev/peekandpoke/klang/strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt`

**Estimated lines of code**:

- stepcat: ~80 lines
- within: ~60 lines
- chunk: ~50 lines
- echo: ~50 lines
- echoWith: ~60 lines
- Tests: ~40 lines
- **Total**: ~340 lines

**Estimated time**: 2-3 hours
