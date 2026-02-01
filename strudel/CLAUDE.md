# Strudel Subproject - Claude Context

## What is Strudel?

Strudel is a Kotlin/Multiplatform port of the JavaScript Strudel pattern language for live coding music. It's a
sophisticated temporal pattern system that schedules musical events based on cyclic time.

**Key Concepts:**

- **Patterns**: Generative sequences of musical events (notes, samples, effects)
- **Cycles**: Time is organized in cycles (typically 1 cycle = 1 measure)
- **Events**: Discrete musical events with timing information
- **Mini-notation**: Compact DSL for expressing patterns: `note("c d e f")`

## Critical Architectural Changes (2026-01-31)

### The Part/Whole Refactoring ✅ COMPLETED

**Problem**: Original `StrudelPatternEvent` only had `begin`/`end`/`dur` fields, but JavaScript Strudel has `part` (
visible portion) and `whole` (complete event). This prevented correct onset detection and playback.

**Solution**: Complete refactoring to match JavaScript semantics:

```kotlin
// OLD (WRONG)
data class StrudelPatternEvent(
    val begin: Rational,
    val end: Rational,
    val dur: Rational,
    val data: StrudelVoiceData
)

// NEW (CORRECT)
data class StrudelPatternEvent(
    val part: TimeSpan,      // Visible portion (may be clipped)
    val whole: TimeSpan?,    // Complete original event (null for continuous)
    val data: StrudelVoiceData
) {
    // Convenience accessors (DEPRECATED - being removed)
    val begin: Rational get() = part.begin
    val end: Rational get() = part.end
    val dur: Rational get() = part.duration

    // Onset detection (critical!)
    fun hasOnset(): Boolean = whole != null && whole.begin == part.begin
}
```

**Status**: Implementation complete, 40+ files updated, compiles successfully
**Test Status**: 98 out of 2476 tests failing (96% pass rate)

### TimeSpan Helper Class

```kotlin
data class TimeSpan(
    val begin: Rational,
    val end: Rational
) {
    val duration: Rational get() = end - begin

    fun shift(offset: Rational): TimeSpan = TimeSpan(begin + offset, end + offset)
    fun scale(factor: Rational): TimeSpan = TimeSpan(begin * factor, end * factor)
    fun clipTo(bounds: TimeSpan): TimeSpan? = // clips to bounds, returns null if no overlap
}
```

## Current Priority: Accessor Removal 🚧 IN PROGRESS

**Why**: Convenience accessors (`begin`, `end`, `dur`) hide whether we're accessing `part` or `whole`, making
correctness verification impossible.

**Strategy**:

1. Proactively replace all ~113 usages with explicit `part.begin`/`part.end`/`part.duration`
2. Comment out accessors to catch any missed usages
3. Verify compilation and tests

**Status**: Plan created, ready to execute
**Documents**: See `docs/agent-tasks/accessor-replacement-execution.md`

## THE SIX GOLDEN RULES (Critical - Never Break These!)

### Rule 1: Event Creation - Set Both Part and Whole

**Discrete events** (notes, samples):

```kotlin
val timeSpan = TimeSpan(begin, end)
StrudelPatternEvent(
    part = timeSpan,
    whole = timeSpan,  // part == whole for new events
    data = data
)
```

**Continuous patterns** (sine, saw):

```kotlin
StrudelPatternEvent(
    part = TimeSpan(begin, end),
    whole = null,  // continuous patterns have no onset
    data = data
)
```

### Rule 2: Clipping Operations - Preserve Whole!

**CRITICAL**: When clipping events (BindPattern, StructurePattern, Pick patterns):

```kotlin
// ✅ CORRECT
val clippedPart = event.part.clipTo(bounds)
event.copy(
    part = clippedPart,
    // whole is automatically preserved by .copy()
)

// ❌ WRONG - Never do this!
event.copy(
    part = clippedPart,
    whole = clippedPart  // WRONG! Loses onset information
)
```

### Rule 3: Scaling Operations - Scale Both

**Fast, Slow, Hurry patterns**:

```kotlin
event.copy(
    part = event.part.scale(factor),
    whole = event.whole?.scale(factor)  // Same factor
)
```

### Rule 4: Time Shifting - Shift Both

**Early, Late, RepeatCycles patterns**:

```kotlin
event.copy(
    part = event.part.shift(offset),
    whole = event.whole?.shift(offset) ?: event.part.shift(offset)
)
```

Note: `?: event.part.shift(offset)` handles continuous patterns (whole=null) by setting whole=part.

### Rule 5: Data-Only Operations - Don't Touch Part/Whole

**Gain, Note, Sound, all control patterns**:

```kotlin
event.copy(
    data = newData
    // part and whole inherited automatically
)
```

### Rule 6: Pass-Through Patterns - Don't Modify Events

**MapPattern, ChoicePattern, ContextModifier**: Events flow through unchanged.

## Common Mistakes to Avoid

### ❌ Mistake 1: Using Convenience Accessors

```kotlin
// BAD (being phased out)
if (event.begin >= from && event.end <= to)

// GOOD (explicit)
    if (event.part.begin >= from && event.part.end <= to)
```

### ❌ Mistake 2: Modifying Whole During Clipping

```kotlin
// BAD
event.copy(part = clippedPart, whole = clippedPart)

// GOOD
event.copy(part = clippedPart)  // whole preserved
```

### ❌ Mistake 3: Scaling Only Part

```kotlin
// BAD
event.copy(part = event.part.scale(factor))

// GOOD
event.copy(
    part = event.part.scale(factor),
    whole = event.whole?.scale(factor)
)
```

### ❌ Mistake 4: Assuming All Events Have Onset

```kotlin
// BAD - clipped events may not have onset
events.forEach {
    assert(it.hasOnset()) // Can fail!
}

// GOOD - check explicitly
val onsetEvents = events.filter { it.hasOnset() }
```

## Pattern Operation Types

When working with patterns, identify the operation type:

| Type               | Examples                              | Part Rule           | Whole Rule                                |
|--------------------|---------------------------------------|---------------------|-------------------------------------------|
| **Event Creation** | AtomicPattern, EuclideanPattern       | Set to event bounds | Set same as part (or null for continuous) |
| **Clipping**       | BindPattern, StructurePattern, Pick*  | Clip to bounds      | **PRESERVE** original                     |
| **Scaling**        | TempoModifierPattern, HurryPattern    | Scale by factor     | Scale by **same** factor                  |
| **Shifting**       | RepeatCyclesPattern, TimeShiftPattern | Shift by offset     | Shift by **same** offset                  |
| **Data Transform** | ControlPattern, Gain, Note            | **Don't touch**     | **Don't touch**                           |
| **Pass-Through**   | MapPattern, ChoicePattern             | **Don't modify**    | **Don't modify**                          |

## Key Files & Their Roles

### Core Event System

- `StrudelPatternEvent.kt` - Event definition with part/whole
- `StrudelPattern.kt` - Pattern interface and helper methods
- `TimeSpan.kt` - Part of StrudelPatternEvent.kt, helper class for time ranges

### Critical Pattern Classes

- `BindPattern.kt` - Inner join (clipping) - **MOST CRITICAL**
- `TempoModifierPattern.kt` - Fast/slow (scaling)
- `StructurePattern.kt` - Struct operation (clipping)
- `RepeatCyclesPattern.kt` - Cycle repetition (shifting)
- `AtomicPattern.kt` - Basic event creation

### Playback System

- `StrudelPlayback.kt` - Schedules events for playback, applies `hasOnset()` filter
- `VoiceScheduler.kt` - Low-level scheduling

### DSL (Domain Specific Language)

- `lang_*.kt` files - User-facing API for pattern creation

### Tests

- `pattern/*Spec.kt` - Pattern class tests
- `lang/*Spec.kt` - DSL tests
- `compat/JsCompatTests.kt` - JavaScript compatibility tests

## Testing Strategy

### Unit Tests

Each pattern should have tests verifying:

1. Part and whole are set correctly
2. hasOnset() works as expected
3. Clipped events don't have onset (when appropriate)
4. Multi-cycle behavior is consistent

### Test Structure (Follow LangSwingSpec Pattern)

```kotlin
"pattern operation test" {
    val subject = createPattern()

    assertSoftly {
        repeat(12) { cycle ->  // Test multiple cycles!
            withClue("Cycle $cycle") {
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)

                events.forEachIndexed { i, ev ->
                    // Check part explicitly
                    ev.part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                    ev.part.end.toDouble() shouldBe (expected plusOrMinus EPSILON)

                    // Check whole explicitly
                    ev.whole?.begin?.toDouble() shouldBe (expected plusOrMinus EPSILON)
                    ev.whole?.end?.toDouble() shouldBe (expected plusOrMinus EPSILON)

                    // Verify hasOnset()
                    ev.hasOnset() shouldBe expectedBoolean
                }
            }
        }
    }
}
```

### Important: queryArc() vs Playback

- `queryArc()` returns **all** events (including clipped ones without onset)
- `StrudelPlayback` filters by `hasOnset()` before scheduling
- Tests see more events than playback will actually play - this is correct!

## Current Test Status

**Overall**: 2476 tests, 98 failures (96% pass rate)

**Fixed Recently** (2026-01-31):

- ✅ LangEarlySpec - All 3 tests now pass
- ✅ Added comprehensive part/whole verification to early() tests

**Remaining Failures** (by category):

- 76 tests: Euclidean patterns
- 7 tests: Struct operations
- 5 tests: Swing operations
- 3 tests: Sample LoopAt
- 2 tests: Drop operations
- 2 tests: Pick operations (pickOut)
- 2 tests: Inside operations
- 1 test: Off operation
- 1 test: Take operation

Most failures are timing edge cases in complex patterns, not fundamental part/whole issues.

## How to Approach Changes

### Before Making Changes

1. **Identify operation type** (creation, clipping, scaling, shifting, data-only, pass-through)
2. **Read the pattern** - understand what it does
3. **Check existing part/whole handling** - is it already correct?
4. **Look for `.begin`, `.end`, `.dur`** - these need explicit `part.` or `whole.`

### When Fixing Issues

1. **Don't guess** - create a thesis about the root cause
2. **Write unit tests** - verify the underlying operation
3. **Test across multiple cycles** - timing bugs compound over cycles
4. **Check hasOnset()** - verify onset detection is correct

### After Making Changes

1. **Compile** - `./gradlew :strudel:compileKotlinJvm`
2. **Run tests** - `./gradlew :strudel:jvmTest`
3. **Check test count** - should not increase failures
4. **Verify logic** - did you follow the appropriate rule?

## Quick Reference: When to Use What

| Scenario                           | Use              | Example                                            |
|------------------------------------|------------------|----------------------------------------------------|
| Checking if event intersects query | `part.begin/end` | `event.part.begin >= from && event.part.end <= to` |
| Querying sub-pattern               | `part.begin/end` | `pattern.queryArc(ev.part.begin, ev.part.end)`     |
| Calculating visible duration       | `part.duration`  | `val dur = event.part.duration`                    |
| Sorting events                     | `part.begin`     | `events.sortedBy { it.part.begin }`                |
| Checking cycle position            | `part.begin`     | `val cycle = event.part.begin.floor()`             |
| Creating new event                 | Set both         | See Rule 1                                         |
| Clipping event                     | Modify part only | See Rule 2                                         |
| Scaling time                       | Scale both       | See Rule 3                                         |
| Shifting time                      | Shift both       | See Rule 4                                         |
| Modifying data                     | Touch neither    | See Rule 5                                         |

## Key Documentation Files

All in `docs/agent-tasks/`:

- `strudel-part-whole-refactoring.md` - Complete implementation history
- `strudel-part-whole-verification-plan.md` - Verification methodology
- `lang-early-spec-fixes.md` - Example of systematic test fixing
- `accessor-replacement-execution.md` - Current priority work plan
- `test-failure-investigation.md` - Approach for fixing remaining 98 tests

## JavaScript Compatibility

Strudel Kotlin must match JavaScript Strudel behavior:

- Event structure (part/whole) matches JS `Hap` objects
- hasOnset() logic: `whole != null && whole.begin == part.begin`
- Playback filtering: `events.filter { it.whole == null || it.hasOnset() }`

Test compatibility: `compat/JsCompatTests.kt` compares outputs directly with JS implementation.

## Common Operations Cheat Sheet

### Creating Events

```kotlin
// Discrete
val ts = TimeSpan(begin, end)
StrudelPatternEvent(part = ts, whole = ts, data = data)

// Continuous
StrudelPatternEvent(part = TimeSpan(begin, end), whole = null, data = data)
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
    whole = event.whole?.scale(factor)
)
```

### Shifting

```kotlin
event.copy(
    part = event.part.shift(offset),
    whole = event.whole?.shift(offset) ?: event.part.shift(offset)
)
```

### Querying

```kotlin
pattern.queryArcContextual(event.part.begin, event.part.end, ctx)
```

### Filtering

```kotlin
events.filter { it.part.begin >= from && it.part.end <= to }
    .filter { it.hasOnset() }  // if you want only onset events
```

## Project Status Summary

**Phase**: Accessor Removal (Week 1)
**Goal**: Replace all convenience accessor usages with explicit part/whole access
**Files**: ~20 files, ~113 usages
**Timeline**: 6-9 days
**Next**: Start with StrudelPattern.kt (9 usages, most critical)

**Long-term Goal**: Get from 98 failing tests to 0 failing tests

## Notes for Future Claude Sessions

1. **Always check this file first** when working in strudel subproject
2. **The six golden rules are non-negotiable** - follow them religiously
3. **Test across multiple cycles** - timing bugs compound
4. **Be explicit about part vs whole** - never use convenience accessors
5. **Don't guess** - create theses, write tests, verify
6. **Commit after each major fix** - makes rollback easier if needed
7. **Check test count** - should decrease or stay same, never increase

## When You're Stuck

1. Check if it's one of the six golden rules
2. Look at similar patterns that are already fixed (BindPattern, TempoModifierPattern, RepeatCyclesPattern)
3. Check the documentation files listed above
4. Read the JavaScript Strudel source for comparison
5. Write a unit test to isolate the issue

## Contact

When reporting issues or progress, note:

- Current file being worked on
- Operation type (creation, clipping, scaling, etc.)
- Thesis about the issue
- Test status before/after

---

**Last Updated**: 2026-01-31
**Status**: Part/whole refactoring complete, accessor removal in progress
**Test Status**: 2476 tests, 98 failures (96% pass rate)
