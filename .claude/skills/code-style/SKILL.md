---
name: code-style
description: Use when someone asks to apply code style rules, check code style, clean up code style, or follow project code conventions.
---

## What This Skill Does

Loads the code style and coding convention rules for the Klang project.
Apply these rules whenever writing or editing code.

---

## Formatting Rules

### 1. Always Use Curly Braces

All `if`, `else`, `for`, `while`, and `when` branch bodies must use `{ }`, even for one-liners.

**Wrong:**

```kotlin
if (condition) doSomething()
if (condition) doSomething() else doOther()
```

**Correct:**

```kotlin
if (condition) {
    doSomething()
}
if (condition) {
    doSomething()
} else {
    doOther()
}
```

### 2. File Names Use PascalCase

All `.kt` files must be named in PascalCase matching their primary declaration.

**Wrong:** `clipping_functions.kt`, `index_common.kt`
**Correct:** `ClippingFunctions.kt`, `IndexCommon.kt`

---

## No Duplication Rules

### 3. No Duplicated Utility Functions

Shared DSP utilities (`flushDenormal`, shape resolution, etc.) must live in exactly one place
and be imported. Never copy a utility function into another file as a `private` copy.

**Canonical location:** `DspUtil.kt` in the module root package.

### 4. No Duplicated Data Classes or Resolution Logic

If two files need the same data class (e.g., `ResolvedShape`) or lookup logic (e.g., distortion
shape resolution), extract it to a shared file. Both callers import from the same source.

---

## Kotlin/JS Performance Rules

### 5. No `Long` in Per-Sample Audio Loops

`Long` is boxed in Kotlin/JS, causing severe performance degradation (object allocation on every
operation). In audio hot paths:

- Use `Int` for frame counts (max ~2.1B frames = ~12.4 hours at 48kHz)
- Use `Double` for time values
- Convert `Long` to `Int` at the **block boundary** (once per block), not inside per-sample loops
- `Long` is acceptable in per-block code (once per ~128 samples), just not per-sample

**Wrong:**

```kotlin
var absPos = ctx.blockStart - startFrame  // Long arithmetic per sample
for (i in 0 until ctx.length) {
    doWork(absPos)  // Long in hot loop
    absPos++
}
```

**Correct:**

```kotlin
var absPos = ((ctx.blockStart + ctx.offset) - startFrame).toInt()  // Convert once
for (i in 0 until ctx.length) {
    doWork(absPos)  // Int in hot loop
    absPos++
}
```

### 6. No Allocations in Audio Hot Paths

No `FloatArray()`, `listOf()`, `toList()`, `Pair()`, `String` concatenation, or object creation
in per-sample or per-block audio processing code. Pre-allocate buffers at construction time.

**Wrong:**

```kotlin
override fun process(buffer: FloatArray, offset: Int, length: Int) {
    val temp = FloatArray(length)  // Allocation every block!
}
```

**Correct:**

```kotlin
private var temp: FloatArray = FloatArray(0)

override fun process(buffer: FloatArray, offset: Int, length: Int) {
    if (temp.size < length) {
        temp = FloatArray(length)
    }  // Resize only when needed
}
```

### 7. No Exceptions in Audio Hot Paths

Never use `require()`, `check()`, or throw exceptions in audio processing code.
These allocate strings and can kill the AudioWorklet thread.

---

## DSP Rules

### 8. Flush Denormals in All IIR Filter State

Every IIR filter (SVF, one-pole, allpass, DC blocker) must flush denormals from its
state variables after each update. Use the shared `flushDenormal()` from `DspUtil.kt`.

**Why:** Denormal floats can cause 10-100x CPU spikes on some platforms.

```kotlin
ic1eq = flushDenormal(2.0 * v1 - ic1eq)
ic2eq = flushDenormal(2.0 * v2 - ic2eq)
```

### 9. Band-Limit Discontinuous Waveforms

All oscillators with signal discontinuities (saw, square, pulse) must use PolyBLEP
anti-aliasing. Triangle is exempt (aliasing at -12dB/oct from derivative discontinuity
is acceptable).

### 10. Wrap Oscillator Phase

LFO and oscillator phase accumulators must be wrapped to prevent unbounded growth.
Use `if (phase >= TWO_PI) phase -= TWO_PI` or the `wrapPhase()` helper.

**Why:** Unbounded phase loses `sin()` precision as the mantissa runs out of bits.

---

## Naming Rules

### 11. Single-Letter DSP Variable Names Are Acceptable

Standard DSP coefficient names (`a`, `k`, `q`, `g`, `v`, `p`) are accepted when they
match established mathematical or DSP textbook conventions.

### 12. `.toFloat()` / `.toDouble()` at Buffer Boundaries Are Expected

Audio code computes in `Double` for precision and stores in `FloatArray` for memory/perf.
The conversions at the boundary are intentional — do not flag them as unnecessary casts.

---

## Annotation Rules

### 13. `@Suppress("NOTHING_TO_INLINE")` Is Accepted on Audio Hot-Path Inline Functions

The Kotlin compiler warns that inlining is unnecessary for non-lambda functions. In audio code,
avoiding call overhead is intentional. The suppression is accepted.

### 14. `@Suppress("unused")` Is Accepted on API Surface Libraries

Collections of utility functions (e.g., `ClippingFuncs`) may have members that aren't all
currently referenced but form a coherent API. The suppression is accepted.

---

## Boolean and Null Rules

### 15. `== true` on `Boolean?` Is Correct Kotlin Idiom

When a Boolean is nullable, `x == true` is the correct and idiomatic null-safe check.
Do not flag this as a style issue.

---

## Comment Rules

### 16. Algorithm Constants May Be Inline Literals

Well-known algorithm tuning constants (Freeverb delay lengths, PolyBLEP thresholds, etc.)
are acceptable as inline numeric literals **when documented with a source comment**.

```kotlin
// Freeverb standard comb tunings (designed for 44100 Hz)
private val combTuning = intArrayOf(1116, 1188, 1277, 1356, ...)
```
