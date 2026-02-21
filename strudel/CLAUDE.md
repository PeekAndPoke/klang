# Strudel Subproject - Claude Context

## What is Strudel?

Strudel is a Kotlin/Multiplatform port of the JavaScript Strudel pattern language for live coding music. It's a
sophisticated temporal pattern system that schedules musical events based on cyclic time.

**Key Concepts:**

- **Patterns**: Generative sequences of musical events (notes, samples, effects)
- **Cycles**: Time is organized in cycles (typically 1 cycle = 1 measure)
- **Events**: Discrete musical events with timing information (part/whole)
- **Mini-notation**: Compact DSL for expressing patterns: `note("c d e f")`

## Event Structure

```kotlin
data class StrudelPatternEvent(
    val part: TimeSpan,      // Visible portion (may be clipped)
    val whole: TimeSpan,     // Complete original event (non-nullable!)
    val data: StrudelVoiceData
) {
    // Onset detection (critical!)
    val isOnset: Boolean = whole.begin == part.begin
}
```

**Important**: `whole` is **non-nullable** (despite the comment in the code saying it can be null for continuous
patterns - this is outdated).

## Control Patterns and _innerJoin (CRITICAL!)

**JavaScript `register()` vs Kotlin `_innerJoin`:**

In JavaScript Strudel, the `register()` function automatically handles control patterns (patterns used as arguments). In
Kotlin, this must be done **explicitly** using `_innerJoin`.

**Rule**: Any DSL function that accepts pattern arguments MUST wrap the operation in `_innerJoin` to support control
patterns.

```kotlin
// ❌ WRONG - doesn't support control patterns
fun applyPressBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern
    return pattern.fmap { value ->
        val atomicPattern = AtomicPattern.value(value)
        applyCompress(atomicPattern, listOf(rArg, StrudelDslArg.of(1.0)))
    }.squeezeJoin()
}

// ✅ CORRECT - supports control patterns via _innerJoin
fun applyPressBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asDouble ?: return@_innerJoin src

        src.fmap { value ->
            val atomicPattern = AtomicPattern.value(value)
            applyCompress(atomicPattern, listOf(StrudelDslArg.of(r), StrudelDslArg.of(1.0)))
        }.squeezeJoin()
    }
}
```

**Why this matters:**

- `s("bd sd").pressBy(0.5)` - works without `_innerJoin` (static value)
- `s("bd sd").pressBy("<0 0.5 0.75>")` - ONLY works with `_innerJoin` (control pattern)

The `_innerJoin` combines the source pattern with the control pattern, evaluating the control pattern for each event in
the source.

## Pattern-of-Patterns: fmap and squeezeJoin

**fmap**: Maps over values in a pattern, transforming each value into a pattern (creating pattern-of-patterns)

```kotlin
fun StrudelPattern.fmap(
    transform: (StrudelVoiceValue?) -> StrudelPattern,
): StrudelPattern {
    return this.mapEvents { event ->
        event.copy(
            data = event.data.copy(
                value = StrudelVoiceValue.Pattern(transform(event.data.value))
            )
        )
    }
}
```

**squeezeJoin**: Flattens pattern-of-patterns by squeezing inner patterns into outer event timespans

```kotlin
fun StrudelPattern.squeezeJoin(): StrudelPattern = object : StrudelPattern {
    override fun queryArcContextual(...): List<StrudelPatternEvent> {
        val outerEvents = this@squeezeJoin.queryArcContextual(from, to, ctx)
        val results = mutableListOf<StrudelPatternEvent>()

        for (outerEvent in outerEvents) {
            val innerPattern = (outerEvent.data.value as? StrudelVoiceValue.Pattern)?.pattern ?: continue
            val targetSpan = outerEvent.whole ?: outerEvent.part
            val focusedPattern = innerPattern._focusSpan(targetSpan)
            val innerEvents = focusedPattern.queryArcContextual(...)

            // CRITICAL: Merge outer event's data with inner event's value
            val mergedEvents = innerEvents.map { innerEvent ->
                innerEvent.copy(
                    data = outerEvent.data.copy(value = innerEvent.data.value)
                )
            }
            results.addAll(mergedEvents)
        }
        return results
    }
}
```

**Key Insight**: Data merging in squeezeJoin

- Outer event has: `sound`, `note`, etc., but `value = Pattern`
- Inner event has: correct `value` from original pattern, but no `sound`/`note`
- Solution: `outerEvent.data.copy(value = innerEvent.data.value)`
- This preserves the outer event's musical properties while using the inner event's value

## Pattern Operation Categories

When working with patterns, identify the operation type:

| Type               | Examples                              | Part Rule           | Whole Rule               |
|--------------------|---------------------------------------|---------------------|--------------------------|
| **Event Creation** | AtomicPattern, EuclideanPattern       | Set to event bounds | Set same as part         |
| **Clipping**       | BindPattern, StructurePattern, Pick*  | Clip to bounds      | **PRESERVE** original    |
| **Scaling**        | TempoModifierPattern, HurryPattern    | Scale by factor     | Scale by **same** factor |
| **Shifting**       | RepeatCyclesPattern, TimeShiftPattern | Shift by offset     | Shift by **same** offset |
| **Data Transform** | ControlPattern, Gain, Note            | **Don't touch**     | **Don't touch**          |
| **Pass-Through**   | MapPattern, ChoicePattern             | **Don't modify**    | **Don't modify**         |

## Key Files & Their Roles

### Core Event System
- `StrudelPatternEvent.kt` - Event definition with part/whole
- `StrudelPattern.kt` - Pattern interface and helper methods
- `StrudelVoiceData.kt` - Voice data structure
- `StrudelVoiceValue.kt` - Values (Num, Text, Bool, Seq, **Pattern**)

### Critical Pattern Classes

- `BindPattern.kt` - Inner join (clipping)
- `TempoModifierPattern.kt` - Fast/slow (scaling)
- `StructurePattern.kt` - Struct operation (clipping)
- `RepeatCyclesPattern.kt` - Cycle repetition (shifting)
- `AtomicPattern.kt` - Basic event creation

### Playback System

- `StrudelPlayback.kt` - Schedules events for playback, filters by `isOnset`
- `VoiceScheduler.kt` - Low-level scheduling

### DSL
- `lang_*.kt` files - User-facing API for pattern creation

## Testing Strategy

Test across multiple cycles (at least 12) to catch compounding timing bugs:

```kotlin
"pattern operation test" {
    val subject = createPattern()

    assertSoftly {
        repeat(12) { cycle ->
            withClue("Cycle $cycle") {
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    .filter { it.isOnset }  // Test onset filtering

                // Verify part and whole explicitly
                events[0].part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[0].whole.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
            }
        }
    }
}
```

**Important**: `queryArc()` returns ALL events (including non-onset), but playback filters by `isOnset`.

## JavaScript Compatibility

Test compatibility: `compat/JsCompatTests.kt` compares outputs directly with JS implementation.

Key differences to watch:

- Kotlin `_innerJoin` vs JavaScript `register()`
- Event structure (part/whole) must match JS `Hap` objects
- Onset logic: `whole.begin == part.begin`

## Common Operations Cheat Sheet

### Creating Events
```kotlin
// Discrete
val ts = TimeSpan(begin, end)
StrudelPatternEvent(part = ts, whole = ts, data = data)
```

### Clipping
```kotlin
val clipped = event.part.clipTo(bounds)
if (clipped != null) {
    event.copy(part = clipped)  // whole preserved
}
```

### Scaling
```kotlin
event.copy(
    part = event.part.scale(factor),
    whole = event.whole.scale(factor)
)
```

### Shifting
```kotlin
event.copy(
    part = event.part.shift(offset),
    whole = event.whole.shift(offset)
)
```

## Notes for Future Sessions

1. **Always check this file first** when working in strudel subproject
2. **Control patterns require `_innerJoin`** - never forget this!
3. **Test across multiple cycles** - timing bugs compound
4. **Be explicit about part vs whole** - both are non-nullable
5. **Don't guess** - create theses, write tests, verify
6. **When stuck**: Check similar patterns (BindPattern, TempoModifierPattern) or read JavaScript source

## When You're Stuck

1. Check if control patterns are involved (need `_innerJoin`)
2. Look at similar patterns that work (BindPattern, TempoModifierPattern, RepeatCyclesPattern)
3. Read the JavaScript Strudel source for comparison
4. Write a unit test to isolate the issue
5. Trace through what `fmap` and `squeezeJoin` do with the values

## DSL Documentation Refactoring Pattern

When refactoring an existing `@StrudelDsl` DSL function in `lang_*.kt` files, follow this pattern exactly.
Use `seq()` and `gap()` in `lang_structural.kt` as the reference implementation.

### Step-by-step

**1. Make existing `val` delegates private and prefix with `_`**

```kotlin
// Before
@StrudelDsl
val foo by dslFunction { args, _ -> applyFoo(args) }

@StrudelDsl
val StrudelPattern.foo by dslPatternExtension { p, args, _ -> applyFoo(p, args) }

@StrudelDsl
val String.foo by dslStringExtension { p, args, callInfo -> p.foo(args, callInfo) }

// After
private val _foo by dslFunction { args, _ -> applyFoo(args) }
private val StrudelPattern._foo by dslPatternExtension { p, args, _ -> applyFoo(p, args) }
private val String._foo by dslStringExtension { p, args, callInfo -> p._foo(args, callInfo) }
```

The private delegates still register the function name with KlangScript — that is intentional.

**2. Add public `fun` overloads with full KDoc**

```kotlin
// Private delegates - still register with KlangScript
private val _foo by dslFunction { ... }
private val StrudelPattern._foo by dslPatternExtension { ... }
private val String._foo by dslStringExtension { ... }

// ===== USER-FACING OVERLOADS =====

/**
 * One-line summary of what foo does.
 *
 * Longer explanation of the behaviour, when to use it, edge cases.
 *
 * @param patterns Description of parameter.
 * @return Description of return value.
 * @sample foo("a", "b").note()         // Short comment
 * @sample foo("bd", "sd").s()          // Another example
 * @alias bar, baz                      // Only if aliases exist
 * @category structural                 // Category for docs page
 * @tags foo, rhythm, timing            // Comma-separated searchable tags
 */
@StrudelDsl
fun foo(vararg patterns: PatternLike): StrudelPattern = _foo(patterns.toList())

/** One-line description for this receiver variant. */
@StrudelDsl
fun StrudelPattern.foo(vararg patterns: PatternLike): StrudelPattern = this._foo(patterns.toList())

/** One-line description for the String receiver variant. */
@StrudelDsl
fun String.foo(vararg patterns: PatternLike): StrudelPattern = this._foo(patterns.toList())
```

**Always call through the private delegate** — never call `applyFoo()` directly from the `fun` overloads.

### KDoc rules

| Tag         | Required        | Notes                                                         |
|-------------|-----------------|---------------------------------------------------------------|
| Description | ✅               | First sentence is shown in search results                     |
| `@param`    | ✅               | One per parameter                                             |
| `@return`   | ✅               | Describe the returned pattern                                 |
| Examples    | ✅               | At least 2 fenced ` ```KlangScript ``` ` blocks with comments |
| `@category` | ✅               | Single word: `structural`, `synthesis`, `effects`, etc.       |
| `@tags`     | ✅               | Comma-separated; drives tag-search in docs page               |
| `@alias`    | when applicable | Comma-separated; all aliases must point at each other         |

**Examples format** — use fenced `KlangScript` blocks, NOT `@sample` tags:

```kotlin
/**
 * ...
 *
 * ```KlangScript
 * seq("c d e f").note()             // four notes, one per quarter cycle
 * ```

*
* ```KlangScript
* note("c e g").stack(s("bd sd"))   // chord + beat layered
* ```
*
* @category structural
* @tags ...
  */

```

- Each block is one example (can be multi-line).
- `@sample` tags are **no longer used** — the KSP processor reads fenced blocks instead.

### Line length

- **Max 120 characters per line** — applies to KDoc comments too.
- Multi-line KDoc blocks for anything that won't fit on one line.
- Single-line `/** ... */` only when the full comment including `/** */` fits within 120 chars.

### Aliases

When two or more functions are aliases of each other, every one of them must list all the others:

```kotlin
// hush lists both aliases
@alias bypass, mute

// bypass lists both aliases
@alias hush, mute

// mute lists both aliases
@alias hush, bypass
```

### Example: `gap()` (vararg PatternLike, same as seq/stack)

`gap()` uses `vararg PatternLike` because the underlying delegate accepts any pattern-like value
(numbers, strings, patterns). Pass the args list directly to the delegate:

```kotlin
@StrudelDsl
fun gap(vararg steps: PatternLike): StrudelPattern = _gap(steps.toList())

@StrudelDsl
fun StrudelPattern.gap(vararg steps: PatternLike): StrudelPattern = this._gap(steps.toList())

@StrudelDsl
fun String.gap(vararg steps: PatternLike): StrudelPattern = this._gap(steps.toList())
```

### KSP and docs generation

- The KSP processor (`strudel-ksp`) picks up all `@StrudelDsl`-annotated **functions** (`fun`)
  AND **properties** (`val`).
- After changing KDoc, run `./gradlew :strudel:jvmTest` — the KSP step regenerates docs automatically
  before compilation.
- The `StrudelDocsSpec` tests verify that docs are correctly registered.

### Documentation-only work rule

When the task is **only adding/improving KDoc** (no logic changes):

1. **Do not change any code** — only edit KDoc comments.
2. Add examples as fenced `KlangScript` blocks (see format above).
3. If unsure what a function does, check `LangXYZSpec.kt` in `commonTest`.
4. If still unsure, check https://strudel.cc/workshop/getting-started online docs.
5. Work file-by-file, function-by-function. After each file run `./gradlew :strudel:jvmTest`.

---

**Last Updated**: 2026-02-21
**Recent Work**:

- Implemented `fmap` and `squeezeJoin` for pattern-of-patterns composition
- Added `StrudelVoiceValue.Pattern` variant
- Implemented `press()` and `pressBy()` DSL functions with control pattern support
- Documented `_innerJoin` pattern for control pattern support
- DSL docs refactoring: `hush`, `bypass`, `mute`, `gap`, `seq` fully documented
- KSP processor extended with `@alias` tag support
- `StrudelDocsPage` smart search (`category:`, `tag:`, `function:` prefixes + logical AND)
- `@sample` replaced with fenced `KlangScript` blocks across all lang files (348 occurrences)
- KSP: `MethodTooLargeException` fixed by chunking generated map (8 entries/chunk)
- KSP: properties now emit samples correctly (`generatePropertyVariantDoc` fix)
- `createPerlin` / `createBerlin`: per-seed cache (fixes seed isolation)
- `chunk`/`slowchunk`/`slowChunk`: `transform` moved to last param (enables trailing lambda)
- Full KDoc added to all `@StrudelDsl` items in `lang_tempo.kt` and `lang_random.kt`

**Test Status**: All JVM tests passing ✅

**Documentation Status**: ALL `@StrudelDsl` items fully documented across all lang files:

- `lang_structural.kt` ✅
- `lang_arithmetic.kt` ✅
- `lang_conditional.kt` ✅
- `lang_tempo.kt` ✅ (slow, fast, rev, revv, palindrome, early, late, compress, focus,
  ply, plyWith, plyForEach, hurry, fastGap, densityGap, inside, outside, swingBy, swing, brak)
- `lang_random.kt` ✅ (seed, rand, rand2, randCycle, brand, brandBy, irand, degradeBy,
  degrade, degradeByWith, undegradeBy, undegrade, undegradeByWith, sometimesBy, sometimes,
  often, rarely, almostNever, almostAlways, never, always, someCyclesBy, someCycles,
  randL, randrun, shuffle, scramble, chooseWith, chooseInWith, choose, chooseIn,
  chooseCycles, randcat, wchoose, wchooseCycles, wrandcat)
- `lang_continuous.kt` ✅
