This refactoring plan is designed for a coding agent to modernize the `tones` sub-project, moving it from a direct
JavaScript port to idiomatic, high-performance Kotlin.

---

# Refactoring Plan: Tones Kotlin Modernization

## 1. Objectives

* **Idiomatic Kotlin:** Replace dynamic "any-type" arguments with method overloading and safe nullability.
* **Performance:** Implement caching for expensive parsing and musical object creation.
* **Type Safety:** Replace string-heavy logic with domain objects (Notes, Intervals, Chords).
* **Clean Code:** Use modern Kotlin features like destructuring, sealed classes, and collection transformations.

---

## 2. Global Strategy: Caching & Memoization

Most musical objects are finite and immutable. We should implement a `cache` pattern in `companion objects` for all
primary entities.

### Target Files for Caching:

* **`Interval.kt`**: Cache parsed interval strings (e.g., "P5", "-m3").
* **`Chord.kt`**: Cache results of `get(src: String)`.
* **`Scale.kt`**: Cache results of `get(src: String)`.
* **`DurationValue.kt`**: Cache parsed durations (e.g., "q..").

**Implementation Pattern:**

```kotlin
private val cache = mutableMapOf<String, T>()
fun get(name: String): T = cache.getOrPut(name) { parse(name) }
```

---

## 3. Idiomatic API Refinement

### A. Replace `Any?` with Overloading

Functions like `Note.get(src: Any?)` and `Interval.get(src: Any?)` should be split into explicit, type-safe overloads.

**Action:** Remove `get(src: Any?)` and ensure the following exist:

* `fun get(name: String): T`
* `fun get(pitch: Pitch): T`
* `fun get(named: NamedPitch): T`

### B. Replace "NoObject" with Nullability or Sealed Classes

Current classes use a `val empty: Boolean` flag with dummy constants (e.g., `Int.MIN_VALUE`).

**Action:**

1. Prefer returning `null` for invalid lookups in external APIs.
2. If an internal "Invalid" state is required, refactor to `sealed class`.
    * *Example for `TimeSignature`*: It is already a sealed class. Ensure `Valid` and `Invalid` are used consistently.
    * *Example for `Chord`*: Convert to `sealed class Chord` with `data class Valid` and `object Invalid`.

### C. Regex & String Parsing

Replace manual index access (`match.groupValues[1]`) with destructuring.

**Action (Note.kt, Interval.kt, TimeSignature.kt):**

```kotlin
val (letter, acc, oct, rest) = REGEX.matchEntire(str)?.destructured ?: return NoNote
```

---

## 4. File-Specific Refactoring Tasks

### `Note.kt`

* **Caching:** Already implemented, but ensure it is used in all `get` overloads.
* **Enharmonic Logic:** Simplify the `destOctOffset` logic using Kotlin's `when` expression (currently looks good, but
  check for redundant calculations).

### `Interval.kt`

* **Implement Caching:** Add a private cache for `Interval` objects.
* **Type Safety:** Ensure `add` and `subtract` accept `Interval` objects as well as `String`.

### `Chord.kt` & `Scale.kt`

* **Internal Data:** Change `notes: List<String>` to `notes: List<Note>` and `intervals: List<String>` to
  `intervals: List<Interval>`.
* **Lazy Computation:** Since calculating chords and scales involves dictionary lookups and transpositions, ensure the
  results are cached or computed lazily.

### `DurationValue.kt`

* **Memoize common values:** The `calcDots` function is called every time a duration with dots is requested.
* **Action:** Pre-calculate common dot variations (up to 3 dots) for the standard durations (whole to 64th) during
  initialization or cache them on first hit.

### `TimeSignature.kt`

* **Refinement:** `get(literal: List<Any>)` is used to handle `[numerator, denominator]`.
* **Action:** Replace with a specific `get(upper: Int, lower: Int)` function to avoid `Any` and `.toString()` overhead.

---

## 5. Performance Optimizations

### String Concatenation in Loops

In `Voicing.search` and `Chord.getChord`, look for opportunities to use `buildString` or `StringBuilder` if constructing
complex symbols in loops.

### Range Calculations

In `Voicing.kt`, the `search` function calls `Note.get(it)` inside filters.
**Action:** Extract `Note.get(it).chroma` once and use integer comparison to avoid repeated map lookups in the cache.

---

## 6. Definition of Done

1. All `Any?` parameters in public `get` methods are replaced by type-safe overloads.
2. `Note`, `Interval`, `Chord`, `Scale`, and `DurationValue` have internal caches.
3. All existing tests in `commonTest` pass.
4. Regex parsing uses destructuring declarations.
5. `List<String>` for musical notes is replaced by `List<Note>` where performance allows.
