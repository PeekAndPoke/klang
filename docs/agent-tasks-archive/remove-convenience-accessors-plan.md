# Remove StrudelPatternEvent Convenience Accessors - Systematic Plan

## Problem Statement

The convenience accessors in `StrudelPatternEvent`:

```kotlin
val begin: Rational get() = part.begin
val end: Rational get() = part.end
val dur: Rational get() = part.duration
```

These hide whether we're accessing `part` or `whole`, making it impossible to verify correctness. We need to explicitly
use `part.begin`/`part.end`/`part.duration` everywhere to ensure proper handling of part/whole semantics.

## Strategy

1. **Comment out the accessors** to cause compilation errors
2. **Systematically fix each error** by explicitly choosing `part` or `whole`
3. **Verify correctness** at each location based on the operation type
4. **Document patterns** for future reference

## Step 1: Comment Out Accessors

In `StrudelPatternEvent.kt`, comment out:

```kotlin
/** Convenience accessor for begin time */
// val begin: Rational get() = part.begin

/** Convenience accessor for end time */
// val end: Rational get() = part.end

/** Convenience accessor for duration */
// val dur: Rational get() = part.duration
```

This will cause ~200-300 compilation errors showing all usage sites.

## Step 2: Categorize and Fix All Usages

### Phase A: Pattern Classes (Event Creation & Transformation)

For each pattern class, determine the operation type and fix accordingly:

#### A1. Event Creation Patterns (Set part == whole)

Files: `AtomicPattern.kt`, `AtomicInfinitePattern.kt`, `EuclideanPattern.kt`, etc.

**Decision Rule**: When creating events, use `part.begin`/`part.end`
**Example**:

```kotlin
// BEFORE
val event = StrudelPatternEvent(...)
if (event.begin < queryStart) { ... }

// AFTER
val event = StrudelPatternEvent(...)
if (event.part.begin < queryStart) { ... }
```

#### A2. Clipping Operations (Preserve whole, modify part)

Files: `BindPattern.kt`, `StructurePattern.kt`, `PickRestartPattern.kt`, etc.

**Decision Rule**:

- For intersection/bounds checks: use `part.begin`/`part.end`
- For onset checks: use `whole.begin`

**Example**:

```kotlin
// BEFORE
val intersectStart = maxOf(from, event.begin)
val intersectEnd = minOf(to, event.end)

// AFTER
val intersectStart = maxOf(from, event.part.begin)
val intersectEnd = minOf(to, event.part.end)
```

#### A3. Time Transformation Operations (Scale/shift both)

Files: `TempoModifierPattern.kt`, `SwingPattern.kt`, `RepeatCyclesPattern.kt`, etc.

**Decision Rule**: Access both `part` and `whole` explicitly
**Example**:

```kotlin
// BEFORE
event.copy(begin = newBegin, end = newEnd)

// AFTER (if scaling)
event.copy(
    part = event.part.scale(factor),
    whole = event.whole?.scale(factor)
)

// AFTER (if shifting)
event.copy(
    part = event.part.shift(offset),
    whole = event.whole?.shift(offset)
)
```

#### A4. Pass-Through Operations

Files: `MapPattern.kt`, `ChoicePattern.kt`, `EmptyPattern.kt`, etc.

**Decision Rule**: These shouldn't access begin/end at all - they just pass events through
**Action**: If they do access begin/end, determine why and fix

### Phase B: Helper Methods & DSL Functions

Files: `StrudelPattern.kt`, `lang_*.kt` files

#### B1. Query/Filter Operations

**Decision Rule**: Use `part.begin`/`part.end` for time range checks

```kotlin
// BEFORE
events.filter { it.begin >= from && it.end <= to }

// AFTER
events.filter { it.part.begin >= from && it.part.end <= to }
```

#### B2. Event Comparison/Sorting

**Decision Rule**: Use `part.begin` for sorting (visible time)

```kotlin
// BEFORE
events.sortedBy { it.begin }

// AFTER
events.sortedBy { it.part.begin }
```

#### B3. Duration Calculations

**Decision Rule**: Use `part.duration` for visible duration

```kotlin
// BEFORE
val duration = event.dur

// AFTER
val duration = event.part.duration
```

### Phase C: Test Files

Files: All `*Spec.kt` files

#### C1. Explicit Part/Whole Tests

**Decision Rule**: Always be explicit about what's being tested

```kotlin
// BEFORE
event.begin shouldBe (expected plusOrMinus EPSILON)
event.end shouldBe (expected plusOrMinus EPSILON)

// AFTER - for part
event.part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.part.end.toDouble() shouldBe (expected plusOrMinus EPSILON)

// AND also verify whole if relevant
event.whole?.begin?.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.whole?.end?.toDouble() shouldBe (expected plusOrMinus EPSILON)
```

#### C2. Test Helpers

Files: `TestHelpers.kt`, `verifyPattern.kt`
**Decision Rule**: Update helpers to be explicit about part/whole

### Phase D: Other Components

#### D1. Playback System

Files: `StrudelPlayback.kt`, `VoiceScheduler.kt`

**Decision Rule**:

- For scheduling time: use `part.begin`/`part.end` (visible time)
- For onset filtering: use `hasOnset()` or check `whole.begin == part.begin`

```kotlin
// BEFORE
.filter { it.begin >= fromRational && it.begin < toRational }

// AFTER
.filter { it.part.begin >= fromRational && it.part.begin < toRational }
```

#### D2. JavaScript Interop

Files: `GraalStrudelPattern.kt`

**Decision Rule**: Extract both part and whole explicitly
**Status**: Already updated in Phase 1-7 of refactoring

#### D3. Serialization/Formatting

Files: Any files that format or serialize events

**Decision Rule**: Be explicit about what's being serialized

## Step 3: Verification Procedure

For each fixed file:

### 3.1 Read Context

```bash
# Check what the file does
grep -n "class\|fun\|object" <file>
```

### 3.2 Classify Operations

Determine if the file contains:

- Event creation → use part.begin/end
- Clipping → preserve whole, modify part
- Scaling → scale both part and whole
- Shifting → shift both part and whole
- Filtering → use part.begin/end for visible time
- Pass-through → shouldn't access begin/end

### 3.3 Apply Fix Pattern

Based on classification, apply the appropriate fix pattern from Phase A/B/C/D above.

### 3.4 Verify Correctness

For each fix, ask:

- **For clipping operations**: Is `whole` preserved?
- **For scaling operations**: Are both `part` and `whole` scaled?
- **For shifting operations**: Are both `part` and `whole` shifted?
- **For queries**: Am I checking visible time (`part`) or onset time (`whole`)?

### 3.5 Compile Check

```bash
./gradlew :strudel:compileKotlinJvm
```

Fix any remaining compilation errors in that file.

## Step 4: Systematic Execution

### Week 1: Pattern Classes (High Risk)

1. Day 1: Event creation patterns (10-15 files)
2. Day 2: Clipping operations (5-7 files)
3. Day 3: Time transformation patterns (8-10 files)
4. Day 4: Pass-through and utility patterns (8-10 files)
5. Day 5: Verify all pattern files compile

### Week 2: Helper Methods & DSL

1. Day 1: StrudelPattern.kt helper methods
2. Day 2: lang_tempo.kt and lang_structural.kt
3. Day 3: Remaining lang_*.kt files
4. Day 4: Utility functions
5. Day 5: Verify all compiles

### Week 3: Tests & Other Components

1. Day 1-2: Update test files
2. Day 3: Update test helpers
3. Day 4: Update playback system
4. Day 5: Verify all compiles and tests run

### Week 4: Final Verification

1. Day 1: Run full test suite
2. Day 2: Fix any test failures
3. Day 3: Performance testing
4. Day 4: Integration testing
5. Day 5: Documentation and code review

## Step 5: Common Patterns Reference

### Pattern 1: Query/Filter by Time Range

```kotlin
// ❌ WRONG
events.filter { it.begin >= from && it.end <= to }

// ✅ CORRECT
events.filter { it.part.begin >= from && it.part.end <= to }
```

### Pattern 2: Sort Events by Time

```kotlin
// ❌ WRONG
events.sortedBy { it.begin }

// ✅ CORRECT
events.sortedBy { it.part.begin }
```

### Pattern 3: Calculate Duration

```kotlin
// ❌ WRONG
val duration = event.dur

// ✅ CORRECT
val duration = event.part.duration
```

### Pattern 4: Check Intersection

```kotlin
// ❌ WRONG
val intersectStart = maxOf(from, event.begin)
val intersectEnd = minOf(to, event.end)

// ✅ CORRECT
val intersectStart = maxOf(from, event.part.begin)
val intersectEnd = minOf(to, event.part.end)
```

### Pattern 5: Event Creation (Discrete)

```kotlin
// ✅ CORRECT
val timeSpan = TimeSpan(begin, end)
StrudelPatternEvent(
    part = timeSpan,
    whole = timeSpan,  // part == whole for new events
    data = data
)
```

### Pattern 6: Event Creation (Continuous)

```kotlin
// ✅ CORRECT
StrudelPatternEvent(
    part = TimeSpan(begin, end),
    whole = null,  // continuous patterns have no onset
    data = data
)
```

### Pattern 7: Clipping (Preserve Whole)

```kotlin
// ✅ CORRECT
val clippedPart = event.part.clipTo(bounds)
if (clippedPart != null) {
    event.copy(
        part = clippedPart,
        // whole inherited = event.whole (PRESERVED!)
    )
}
```

### Pattern 8: Scaling (Scale Both)

```kotlin
// ✅ CORRECT
event.copy(
    part = event.part.scale(factor),
    whole = event.whole?.scale(factor)
)
```

### Pattern 9: Shifting (Shift Both)

```kotlin
// ✅ CORRECT
event.copy(
    part = event.part.shift(offset),
    whole = event.whole?.shift(offset) ?: event.part.shift(offset)
)
```

### Pattern 10: Test Assertions

```kotlin
// ✅ CORRECT - Be explicit about part and whole
event.part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.part.end.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.whole?.begin?.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.whole?.end?.toDouble() shouldBe (expected plusOrMinus EPSILON)
event.hasOnset() shouldBe expectedBoolean
```

## Tracking Progress

Create a tracking file: `docs/agent-tasks/accessor-removal-progress.md`

```markdown
# Accessor Removal Progress

## Pattern Classes
- [ ] AtomicPattern.kt
- [ ] AtomicInfinitePattern.kt
- [ ] BindPattern.kt
- [ ] ContinuousPattern.kt
- [ ] ControlPattern.kt
... (list all pattern files)

## Helper Methods
- [ ] StrudelPattern.kt
- [ ] lang_tempo.kt
- [ ] lang_structural.kt
... (list all lang files)

## Tests
- [ ] Test helpers
- [ ] Pattern specs
- [ ] Lang specs
... (list all test categories)

## Other
- [ ] StrudelPlayback.kt
- [ ] VoiceScheduler.kt
- [ ] Serialization
```

## Expected Outcomes

### Benefits

1. **Explicit semantics**: Every usage clearly shows if we're working with `part` or `whole`
2. **Easier verification**: Can grep for `\.begin` and verify each usage
3. **Prevents bugs**: Forces conscious decision about which timespan to use
4. **Better documentation**: Code is self-documenting about intent

### Risks

1. **Large change**: ~200-300 compilation errors to fix
2. **Time consuming**: 3-4 weeks of systematic work
3. **Risk of errors**: Each fix must be carefully considered

### Mitigation

1. **Systematic approach**: Follow the plan step by step
2. **Verify incrementally**: Compile after each file
3. **Test frequently**: Run tests after each phase
4. **Document patterns**: Reference guide for common cases
5. **Code review**: Have another developer review critical sections

## Success Criteria

- [ ] All compilation errors resolved
- [ ] All uses of `begin`/`end`/`dur` replaced with explicit `part.begin`/`part.end`/`part.duration`
- [ ] All tests pass (currently 98 failing, target 0 failing)
- [ ] No performance regression
- [ ] Documentation updated
- [ ] Code review completed

## Rollback Plan

If issues arise:

1. Uncommit the accessor removal
2. Keep individual fixes that improved correctness
3. Re-introduce accessors with deprecation warnings
4. Complete incrementally over longer period

## Next Immediate Steps

1. **Comment out the accessors** in `StrudelPatternEvent.kt`
2. **Compile** to see all errors: `./gradlew :strudel:compileKotlinJvm 2>&1 | grep "error:" | wc -l`
3. **Categorize errors** by file type (pattern, helper, test, other)
4. **Start with highest risk**: Begin with pattern classes that create/transform events
5. **Fix systematically**: One file at a time, compile after each
6. **Track progress**: Update progress document after each file

## Estimated Effort

- **Total Files**: ~50-80 files to modify
- **Time per File**: 10-30 minutes average
- **Total Time**: 3-4 weeks at steady pace
- **Can parallelize**: Pattern classes can be done independently

## Priority Order

1. **Critical** (Week 1): Pattern classes - these affect correctness
2. **Important** (Week 2): Helper methods and DSL - affect API usage
3. **Medium** (Week 3): Tests and playback - verification and runtime
4. **Low** (Week 4): Utilities and edge cases - completeness
