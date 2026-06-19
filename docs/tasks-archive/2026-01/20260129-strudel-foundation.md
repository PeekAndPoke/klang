This is the implementation of the "Solid Foundation".

I have broken it down into **two layers**, just like the JS version:

1. **The Primitive (`bindPattern`)**: This is the pure "Inner Join" logic. It handles the difficult time-intersection
   math once and for all.
2. **The Registry (`lift`)**: This is the high-level DSL builder. It handles the "Patternification" of arguments,
   extracting values, and preserving pattern metadata (steps/weights).

This will allow you to implement functions like `fast(pattern)` or `zoom(pattern, pattern)` in 1-3 lines of code.

### 1. The Foundation File

Create this file. It contains the core architectural glue.

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational

/**
 * THE SOLID FOUNDATION
 *
 * This file contains the monadic and applicative primitives required to build
 * complex pattern functions without reimplementing time-intersection logic.
 */

// =============================================================================
// 1. The Monadic Bind (Inner Join)
// =============================================================================

/**
 * Creates a new pattern by applying [transform] to every event in this pattern.
 *
 * This is the fundamental "Glue" of Strudel.
 * 1. It queries 'this' pattern (the "outer" or "control" pattern).
 * 2. For each event, it generates a new "inner" pattern via [transform].
 * 3. It queries that inner pattern *constrained* to the outer event's time window.
 *
 * @param transform Function to generate an inner pattern from an outer event.
 *                  Return null to produce silence for that event.
 */
fun StrudelPattern.bindPattern(
    transform: (StrudelPatternEvent) -> StrudelPattern?
): StrudelPattern = object : StrudelPattern {
    // By default, a bound pattern's structure is unknown/new, so we reset metadata.
    // Use 'lift' if you want to preserve the source's metadata.
    override val weight: Double get() = 1.0
    override val numSteps: Rational? get() = null

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // We reuse the robust logic already implemented in StrudelPattern.kt
        return this@bindPattern.bind(from, to, ctx, transform)
    }
}

// =============================================================================
// 2. The Applicative Lifters (The "Register" replacement)
// =============================================================================

/**
 * Lifts a function that accepts a [Double] to work with a Pattern of numbers.
 *
 * Equivalent to `register(name, func)` in JS where arity is 1.
 *
 * @param control The control pattern (e.g. `fast("<2 4>")`)
 * @param transform The function to apply (e.g. `pat.fast(val)`)
 */
fun StrudelPattern.lift(
    control: StrudelPattern,
    transform: (Double, StrudelPattern) -> StrudelPattern
): StrudelPattern = object : StrudelPattern {
    // Preserve metadata from the SOURCE pattern (this@lift), matching JS 'preserveSteps=true'
    override val weight: Double get() = this@lift.weight
    override val numSteps: Rational? get() = this@lift.numSteps

    // The logic: Control drives the structure (via bind), Source provides the content
    private val joined = control.bindPattern { event ->
        val value = event.data.value?.asDouble ?: return@bindPattern null
        transform(value, this@lift)
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Lifts a function that accepts two [Double]s to work with two control Patterns.
 *
 * Equivalent to `register` with 2 arguments (e.g. `range(min, max)`).
 * Automatically handles the intersection of `control1`, `control2`, and `this`.
 */
fun StrudelPattern.lift(
    control1: StrudelPattern,
    control2: StrudelPattern,
    transform: (Double, Double, StrudelPattern) -> StrudelPattern
): StrudelPattern = object : StrudelPattern {
    override val weight: Double get() = this@lift.weight
    override val numSteps: Rational? get() = this@lift.numSteps

    // Nested Bind = Intersection of Control 1 AND Control 2
    private val joined = control1.bindPattern { e1 ->
        val v1 = e1.data.value?.asDouble ?: return@bindPattern null

        control2.bindPattern { e2 ->
            val v2 = e2.data.value?.asDouble ?: return@bindPattern null

            transform(v1, v2, this@lift)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Lifts a function that accepts a raw [StrudelVoiceValue] (String/Bool/Num).
 * Use this for string-based controls like `sound` or `vowel`.
 */
fun StrudelPattern.liftValue(
    control: StrudelPattern,
    transform: (StrudelVoiceValue, StrudelPattern) -> StrudelPattern
): StrudelPattern = object : StrudelPattern {
    override val weight: Double get() = this@lift.weight
    override val numSteps: Rational? get() = this@lift.numSteps

    private val joined = control.bindPattern { event ->
        val value = event.data.value ?: return@bindPattern null
        transform(value, this@lift)
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}
```

### 2. Example: Refactoring `fast` and `range`

Now, look how clean the implementations become. You implement the math *once*, and the `lift` handles the pattern logic.

**Refactoring `fast` (Time Modification):**

```kotlin
// 1. The Kernel (Pure Math)
fun StrudelPattern.fast(amount: Double): StrudelPattern {
    if (amount == 0.0) return silence
    // Existing logic...
    return this.withQueryTime { it * amount }.withHapTime { it / amount }
}

// 2. The DSL Function (The Glue)
// Now supports fast("<2 4 8>") automatically!
fun StrudelPattern.fast(amount: StrudelPattern): StrudelPattern =
    lift(amount) { a, pat -> pat.fast(a) }
```

**Implementing `range` (Arithmetic):**

```kotlin
// 1. The Kernel
fun StrudelPattern.range(min: Double, max: Double): StrudelPattern {
    // Assuming simple linear mapping on numeric values
    return this.mapEvents { event ->
        val input = event.data.value?.asDouble ?: return@mapEvents event
        // scale input (0..1) to (min..max)
        val scaled = input * (max - min) + min
        event.copy(data = event.data.copy(value = StrudelVoiceValue.Num(scaled)))
    }
}

// 2. The DSL Function
// Now supports range("<0 10>", "<100 200>")!
fun StrudelPattern.range(min: StrudelPattern, max: StrudelPattern): StrudelPattern =
    lift(min, max) { a, b, pat -> pat.range(a, b) }
```

### 3. Why this is "Solid"

1. **No more manual loops**: You never write `for (event in controlEvents)` again. `bindPattern` does it.
2. **No more time math errors**: The intersection and clipping logic (`clippedBegin`, `clippedEnd`) is isolated in one
   place (`StrudelPattern.bind` in `index_common.kt`), so fixing a bug there fixes it everywhere.
3. **Preserved Metadata**: The `lift` helpers automatically ensure `numSteps` and `weight` are passed through from the
   source pattern, which is a common source of bugs when manually implementing wrapper objects.

You are absolutely right. The previous solution focused heavily on `Double` values (arithmetic and timing) but neglected
the critical **resource loading** functions like `sound()`, `bank()`, and `vowel()`.

These functions are trickier because:

1. They deal with **Strings**, not Numbers.
2. They often parse "mini-notation" inside their arguments (e.g. `s("bd:2")`).
3. They need to merge with existing voice data, not just transform a single field.

Here is the plan to extend the "Solid Foundation" to cover resource functions like `sound()`.

### 1. Extend `PatternBind.kt` with `liftVoice`

We need a lifter specifically designed for modifying the **Voice Data** (the `StrudelVoiceData` object inside the
event).

```kotlin
// ... existing code ...

// =============================================================================
// 3. The Voice Data Lifters
// =============================================================================

/**
 * Lifts a function that modifies [StrudelVoiceData] based on a control pattern.
 *
 * This is the foundation for functions like `sound()`, `bank()`, `vowel()`, `note()`.
 * It handles:
 * 1. Binding the control pattern (intersection).
 * 2. extracting the value (and handling potential mini-notation parsing if the control was a string).
 * 3. Merging the new data into the existing event.
 *
 * @param control The control pattern (e.g. `s("bd sd")`)
 * @param transform A function that takes the control value and returns a PARTIAL [StrudelVoiceData].
 *                  This partial data will be merged into the source event.
 */
fun StrudelPattern.liftVoice(
    control: StrudelPattern,
    transform: (StrudelVoiceValue) -> StrudelVoiceData
): StrudelPattern = object : StrudelPattern {
    override val weight: Double get() = this@liftVoice.weight
    override val numSteps: Rational? get() = this@liftVoice.numSteps

    // The logic:
    // 1. Bind to the control pattern (structure comes from intersection)
    private val joined = control.bindPattern { controlEvent ->
        val controlValue = controlEvent.data.value ?: return@bindPattern null

        // 2. Compute the new voice data attributes (e.g. { sound: "bd" })
        val partialData = transform(controlValue)

        // 3. Apply this to the SOURCE pattern
        // We use 'reinterpretVoice' or just mapEvents on the source
        this@liftVoice.mapEvents { sourceEvent ->
            // 4. Merge: Source + Partial
            // Note: In Strudel, "setting" a value usually overrides the previous one,
            // but we might want to keep other fields. StrudelVoiceData.merge handles this.
            val mergedData = sourceEvent.data.merge(partialData)

            sourceEvent.copy(data = mergedData)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}
```

### 2. Implementing `sound()` with the Foundation

Now `sound()` becomes a simple mapping from a value to a `StrudelVoiceData` object.

There is one catch: `sound("bd:2")` parsing. In the JS version, this parsing often happens *before* the pattern is
created (in the `s()` function itself) or *inside* the pattern query.

With `liftVoice`, we can handle it elegantly.

#### The `sound` Kernel

First, we need logic to parse "bd:2" into `{ sound: "bd", soundIndex: 2 }`.

```kotlin
// In a helper file or StrudelVoiceData companion
fun parseSound(input: StrudelVoiceValue): StrudelVoiceData {
    val text = input.asString
    val parts = text.split(":")

    return if (parts.size > 1) {
        // "bd:2" -> sound="bd", index=2
        // "bd:2:0.5" -> sound="bd", index=2, gain=0.5 (optional feature)
        StrudelVoiceData.empty.copy(
            sound = parts[0],
            soundIndex = parts[1].toIntOrNull()
        )
    } else {
        // "bd" -> sound="bd"
        StrudelVoiceData.empty.copy(sound = text)
    }
}
```

#### The `sound` DSL Function

Now the DSL function is trivial:

```kotlin
// ... existing imports ...
import io.peekandpoke.klang.strudel.pattern.liftVoice

// ... existing code ...

/**
 * Sets the sound (sample name) for the pattern.
 * Supports "sound:index" notation (e.g. "bd:2").
 */
@StrudelDsl
val StrudelPattern.sound by dslPatternExtension { p, args, /* callInfo */ _ ->
    // 1. Convert args to a pattern (handles "bd sd" mini-notation automatically)
    val control = args.toPattern(defaultModifier)

    // 2. Lift the voice modification
    p.liftVoice(control) { value ->
        parseSound(value)
    }
}

// Alias 's'
@StrudelDsl
val StrudelPattern.s by dslPatternExtension { p, args, callInfo -> p.sound(args, callInfo) }
```

### 3. Implementing `bank()` and `vowel()`

The pattern repeats. These functions just map a value to a specific field in `StrudelVoiceData`.

**Bank:**

```kotlin
@StrudelDsl
val StrudelPattern.bank by dslPatternExtension { p, args, _ ->
    p.liftVoice(args.toPattern(defaultModifier)) { value ->
        StrudelVoiceData.empty.copy(bank = value.asString)
    }
}
```

**Vowel:**

```kotlin
@StrudelDsl
val StrudelPattern.vowel by dslPatternExtension { p, args, _ ->
    p.liftVoice(args.toPattern(defaultModifier)) { value ->
        StrudelVoiceData.empty.copy(vowel = value.asString)
    }
}
```

### 4. Special Case: `note()` and `n()`

These are slightly more complex because they might involve `scale` logic or `transpose` logic, but they still fit the
model.

**Note (n):**

```kotlin
@StrudelDsl
val StrudelPattern.n by dslPatternExtension { p, args, _ ->
    p.liftVoice(args.toPattern(defaultModifier)) { value ->
        // 'n' usually sets the note index OR the note name depending on context
        // For now, let's assume it sets the 'note' field raw
        StrudelVoiceData.empty.copy(note = value.asString)
    }
}
```

### Summary of the Plan

1. **Add `liftVoice` to `PatternBind.kt`**: This function specializes in merging `StrudelVoiceData`.
2. **Define Parsers**: Helper functions (like `parseSound`) that take a raw `StrudelVoiceValue` and return a
   `StrudelVoiceData` patch.
3. **Refactor DSL**: Update `sound`, `bank`, `vowel`, etc., to use `liftVoice` + `args.toPattern()`.

This unifies structural functions (`fast`) and data functions (`sound`) under the same "Lift" architecture.

Yes, `args.toPattern()` is **very solid**.

In fact, it is the perfect complement to the `lift` / `bind` architecture.

* **`args.toPattern()`** is your **Input Normalizer**. It handles the "Variable Arguments" problem (converting
  `s("bd")`, `s("bd", "sd")`, and `s("<bd sd>")` into a consistent object).
* **`lift()`** is your **Execution Engine**. It handles the "Pattern Application" problem (merging that normalized
  pattern into your music).

### The Missing Piece: `liftData`

Since `args.toPattern()` already allows you to pass a `modifier` (which parses strings into `StrudelVoiceData`), you
don't need to parse inside the lifter. You just need a lifter that **merges** the data.

Add this to `PatternBind.kt`:

```kotlin
// ... existing code ...

/**
 * Lifts a control pattern that ALREADY contains the target VoiceData.
 * Use this when the parsing happened during 'toPattern()'.
 *
 * This is perfect for sound(), bank(), vowel(), etc.
 */
fun StrudelPattern.liftData(
    control: StrudelPattern
): StrudelPattern = object : StrudelPattern {
    // Preserve source metadata
    override val weight: Double get() = this@liftData.weight
    override val numSteps: Rational? get() = this@liftData.numSteps

    private val joined = control.bindPattern { controlEvent ->
        // The control event already contains the data we want (e.g. sound="bd")
        // We just need to merge it into the source.

        this@liftData.mapEvents { sourceEvent ->
            // Merge control data ON TOP of source data
            // (control fields overwrite source fields if not null)
            val mergedData = sourceEvent.data.merge(controlEvent.data)

            sourceEvent.copy(data = mergedData)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}
```

### How to Implement `sound()` (and others)

Now, implementing these functions becomes a matter of defining **how to parse the string**, and then letting the
architecture handle the rest.

#### 1. Define the Parser (Modifier)

```kotlin
// Define how to parse "bd:2" into VoiceData
private val soundModifier = voiceModifier { value ->
    val text = value?.toString() ?: ""
    val parts = text.split(":")

    if (parts.size > 1) {
        // "bd:2" -> sound=bd, index=2
        copy(
            sound = parts[0],
            soundIndex = parts[1].toIntOrNull()
        )
    } else {
        // "bd" -> sound=bd
        copy(sound = text)
    }
}

@StrudelDsl
val StrudelPattern.sound by dslPatternExtension { p, args, _ ->
    // 1. Normalize Input:
    //    - Uses soundModifier to parse strings immediately
    //    - Handles multiple args as a sequence ("bd", "sd")
    val control = args.toPattern(soundModifier)

    // 2. Execute:
    //    - Merges the parsed data into the pattern structure
    p.liftData(control)
}

// Alias
@StrudelDsl
val StrudelPattern.s by dslPatternExtension { p, args, c -> p.sound(args, c) }
```

### Why this is Solid

This separation of concerns is excellent:

1. **`dslPatternExtension`**: Handles the Kotlin/JS interop boilerplate.
2. **`args.toPattern(...)`**: Handles the **Syntax** (mini-notation, sequences, argument lists).
3. **`soundModifier`**: Handles the **Semantics** (what does the string "bd:2" *mean*?).
4. **`liftData(...)`**: Handles the **Math** (time intersection, splitting events).

### Applying it to `vowel` and `bank`

It becomes trivial to add more functions.

**Vowel:**

```kotlin
private val vowelModifier = voiceModifier { value ->
    copy(vowel = value?.toString())
}

@StrudelDsl
val StrudelPattern.vowel by dslPatternExtension { p, args, _ ->
    p.liftData(args.toPattern(vowelModifier))
}
```

**Bank:**

```kotlin
private val bankModifier = voiceModifier { value ->
    copy(bank = value?.toString())
}

@StrudelDsl
val StrudelPattern.bank by dslPatternExtension { p, args, _ ->
    p.liftData(args.toPattern(bankModifier))
}
```

This is a very robust architecture that will scale to hundreds of functions without creating maintenance debt.
