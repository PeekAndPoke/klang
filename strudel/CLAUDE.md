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
**Test Status**: 85 out of 2476 tests failing (97% pass rate) - improved after struct fixes

### Critical Discovery: Struct and Shared Whole Semantics (2026-02-01)

**Problem**: Understanding how `struct()` affects `whole` and onset detection.

**Key Findings**:

1. **Struct sets whole to mask boundaries** (verified against JS Strudel)
   ```kotlin
   note("c e").struct("x")  // "x" covers [0, 1)
   // Event c: part=[0, 0.5), whole=[0, 1.0)
   // Event e: part=[0.5, 1.0), whole=[0, 1.0)  ← Same whole!
   ```

2. **Multiple events can share the same whole**
   - Both events have `whole=[0, 1.0)` (the struct mask)
   - But only first event has onset: `hasOnset() = (whole.begin == part.begin)`
   - Event c: 0.0 == 0.0 → true ✓ plays
   - Event e: 0.0 != 0.5 → false ✗ doesn't play

3. **Only the first event in a shared whole triggers**
   - This is correct behavior! Struct can filter/gate events
   - The mask creates a temporal boundary, and only events starting at the boundary trigger

4. **Independent pulses create independent wholes**
   ```kotlin
   note("c").struct("x*2")  // Two separate "x" pulses
   // Event 0: part=[0, 0.5), whole=[0, 0.5), hasOnset=true ✓
   // Event 1: part=[0.5, 1.0), whole=[0.5, 1.0), hasOnset=true ✓
   // Both play because each pulse is independent!
   ```

5. **Use case: late() creates tails without onset**
   ```kotlin
   note("c").late(0.5)
   // Cycle 0: part=[0.5, 1.0), whole=[0.5, 1.5), hasOnset=true ✓
   // Cycle 1: part=[1.0, 1.5), whole=[0.5, 1.5), hasOnset=false ✗ (tail)
   ```

**Implementation**: `StructurePattern.kt` sets `whole = maskEvent.whole` for all clipped events.

**Verification**: Live strudel.cc testing confirmed this behavior matches JavaScript.

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

## Recent Achievements ✅ COMPLETED (2026-02-01)

**Status**: 2489 tests, 12 failures (99.5% pass rate) - Down from 82 failures!

### Fixed: Continuous Pattern Property Setting

- **Problem**: `sine.slow(8)` returned null when used with `pan()`, `gain()`, etc.
- **Root cause**: `scaleTimeRangeWithEpsilon` was shrinking query ranges, inverting them for small queries
- **Solution**:
    - Use `scaleTimeRange()` for simple scaling (no epsilon manipulation)
    - Use `hasOverlapWithEpsilon()` for precision-tolerant overlap checks
    - Removed `scaleTimeRangeWithEpsilon` function
- **Result**: All continuous pattern tests pass, 3 tests fixed

### Completed: Accessor Removal

- **Removed**: All convenience accessor usages (`event.begin`, `event.end`, `event.dur`)
- **Replaced with**: Explicit `event.part.begin`, `event.part.end`, `event.part.duration`
- **Files fixed**: ~100+ files (40+ main code, 60+ test code)
- **Accessors**: Commented out in `StrudelPatternEvent.kt`
- **Impact**: Zero new test failures - all code compiles and runs

**Remaining failures**: 12 tests (5 Swing, 3 LoopAt, 2 EuclidLegatoRot, 2 pickOut)

## Future Refactoring TODO

**Replace DropPattern and TakePattern with functional implementations** (2026-02-01)

JavaScript Strudel implements `drop()` and `take()` using `stepJoin()` for structural transformations:

```javascript
export const drop = stepRegister('drop', function (i, pat) {
   if (!pat.hasSteps) return nothing;
   i = Fraction(i);
   if (i.lt(0)) {
      return pat.take(pat._steps.add(i));
   }
   return pat.take(Fraction(0).sub(pat._steps.sub(i)));
});
```

Instead of dedicated pattern classes, these should be:

1. Implement `StrudelPattern._stepJoin()` helper (structural join operation)
2. Replace `DropPattern` class with a function that uses `take()` + `_stepJoin()`
3. Replace `TakePattern` class with a simpler functional implementation

This would match JavaScript architecture and simplify the codebase.

**Status**: Postponed - current implementations work correctly, refactor later for architectural consistency

## Accessor Removal ✅ COMPLETED (2026-02-01)

**Goal**: Remove convenience accessors to make part/whole usage explicit and verifiable.

**Result**: All accessor usages replaced across entire codebase

- Main code: ~40 files fixed
- Test code: ~60 files fixed
- Accessors commented out in StrudelPatternEvent.kt
- All code compiles with explicit `part.begin`, `part.end`, `part.duration`
- Zero new test failures introduced

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

**Overall**: 2479 tests, 82 failures (97% pass rate) - up from 98 failures!

**Fixed Recently** (2026-02-01):

- ✅ LangDropSpec - Fixed DropPattern scaling bug, added comprehensive test
- ✅ LangOffSpec - Fixed test assertions for part/whole
- ✅ LangTakeSpec - Added EPSILON tolerance
- ✅ LangInsideSpec - Fixed accessor usages (2 of 4 tests passing)
- ✅ JsCompatTest "Drop basic" - Now passing with DropPattern fix
- ✅ Struct operations (2026-01-31) - Fixed struct whole semantics

**Fixed Earlier** (2026-01-31):

- ✅ LangEarlySpec - All 3 tests now pass
- ✅ Added comprehensive part/whole verification to early() tests

**Remaining Failures** (by category):

- ~60 tests: Euclidean patterns
- 5 tests: Swing operations (implementation error mentioned)
- 3 tests: Sample LoopAt
- 2 tests: Inside operations (still need investigation)
- 2 tests: Song tests (effects)
- Others: Various patterns

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

**Phase**: Part/Whole Refactoring - ✅ COMPLETE
**Test Status**: 2489 tests, 12 failures (99.5% pass rate)

**Major Milestones Completed**:

1. ✅ Part/whole event structure implemented
2. ✅ All patterns updated to use part/whole correctly
3. ✅ Continuous pattern property setting fixed (sine.slow(8) bug)
4. ✅ Accessor removal complete (~100 files)
5. ✅ TempoModifierPattern epsilon handling fixed

**Remaining Work**: 12 test failures (unrelated to part/whole)

- 5 Swing tests (known implementation issue)
- 3 LoopAt tests (sample loading)
- 2 EuclidLegatoRot tests
- 2 pickOut tests

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

**Last Updated**: 2026-02-01
**Status**: Part/whole refactoring complete, DropPattern bug fixed, test failure investigation ongoing
**Test Status**: 2479 tests, 82 failures (97% pass rate) - 16 tests fixed today!
**Next**: Continue investigating remaining failures (Inside, Swing, Euclidean, etc.)
