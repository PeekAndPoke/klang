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

---

**Last Updated**: 2026-02-04
**Recent Work**:

- Implemented `fmap` and `squeezeJoin` for pattern-of-patterns composition
- Added `StrudelVoiceValue.Pattern` variant
- Implemented `press()` and `pressBy()` DSL functions with control pattern support
- Documented `_innerJoin` pattern for control pattern support

**Test Status**: LangPressSpec - 8/8 tests passing ✅

**Next**: Continue with remaining DSL functions (`linger`, `ribbon`, etc.)
