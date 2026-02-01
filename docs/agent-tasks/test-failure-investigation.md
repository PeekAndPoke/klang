# Test Failure Investigation - Part/Whole Refactoring

## Overview

After implementing the part/whole refactoring, 103 out of 2476 tests are failing (95.8% pass rate).
This document systematically investigates each failure category with:

1. **Thesis**: Hypothesis about root cause
2. **Verification**: Unit tests to validate underlying operations
3. **Fix**: Resolution if thesis is confirmed

## Failure Categories

### Category 1: Euclidean Patterns (76 tests)

**Failing Tests:**

- JsCompatTests: Patterns 113-176 (Euclidean and EuclideanRot)
- JsCompatTests: Patterns 177-178, 180-181, 186, 188-190 (Euclid/EuclidRot/EuclidLegato functions)
- JsCompatTests: Patterns 245-249 (Euclidish patterns)

**Thesis:**
Euclidean patterns generate events with rotation. The rotation operation may be:

1. Not preserving `whole` when shifting events
2. Not correctly setting `part` and `whole` for initial event creation
3. Incorrectly handling onset detection after rotation

**Investigation Steps:**

1. Read `EuclideanPattern.kt` - check event creation sets part == whole
2. Read `EuclideanMorphPattern.kt` - check morphing preserves part/whole
3. Check rotation logic - ensure both part and whole are shifted
4. Pick one failing test (e.g., Pattern 113: Euclidean 1,4)
    - Compare JS output vs Kotlin output
    - Check if onset detection is correct
    - Verify part/whole values at each step

**Unit Tests Needed:**

```kotlin
"EuclideanPattern creates events with part == whole" {
    val pattern = EuclideanPattern(...)
    val events = pattern.queryArc(0.0, 1.0)
    events.forEach { event ->
        event.part shouldBe event.whole
        event.hasOnset() shouldBe true
    }
}

"EuclideanPattern rotation preserves whole" {
    // Test with rotation parameter
    // Verify whole is shifted by same amount as part
}
```

---

### Category 2: Struct Operations (7 tests)

**Failing Tests:**

- JsCompatTests: Pattern 253 (Binary Struct)
- JsCompatTests: Patterns 445-450 (Struct and StructAll)

**Thesis:**
StructurePattern clips events to structure bounds. The issue may be:

1. Not preserving `whole` during clipping
2. Incorrectly computing intersections between structure and pattern events
3. hasOnset() filtering removing events that should play

**Investigation Steps:**

1. Read `StructurePattern.kt` - verify clipping preserves whole
2. Pick failing test Pattern 445: Struct #1
    - Get expected JS output
    - Get actual Kotlin output
    - Compare part/whole values
    - Check hasOnset() filtering

**Unit Tests Needed:**

```kotlin
"StructurePattern preserves whole when clipping" {
    val source = testEvent(begin = 0.0, end = 1.0, ...)
    val pattern = StructurePattern(...)
    val events = pattern.queryArc(0.0, 1.0)
    events.forEach { event ->
        event.whole shouldBe source.whole // Original preserved
        event.part should beClippedTo(...) // Part clipped to structure
    }
}
```

---

### Category 3: Sample LoopAt (3 tests)

**Failing Tests:**

- JsCompatTests: Patterns 367-369 (Sample LoopAt #2, #3, #4)

**Thesis:**
LoopAt likely involves tempo modification and clipping. Potential issues:

1. Tempo scaling not applied to both part and whole
2. Loop boundaries not preserving whole
3. Sample begin/end calculation affecting part/whole

**Investigation Steps:**

1. Find loopAt implementation
2. Check if it uses TempoModifierPattern or similar
3. Verify scaling logic applies to both part and whole
4. Test Pattern 367 - compare outputs

---

### Category 4: Pick Operations (2 tests)

**Failing Tests:**

- JsCompatTests: Pattern 91 (pickOut() no clipping)
- JsCompatTests: Pattern 93 (pickmodOut() no clipping)

**Thesis:**
These tests specifically mention "no clipping". The pickOut operations may be:

1. Using wrong clip parameter value
2. Still clipping when they shouldn't
3. hasOnset() filtering incorrectly for unclipped picks

**Investigation Steps:**

1. Find pickOut implementation in lang_pattern_picking.kt
2. Check if it uses BindPattern with clip=false (outer join behavior)
3. Verify events pass through without clipping
4. Test Pattern 91 - check if events are being incorrectly clipped

---

### Category 5: Drop Operations (2 tests)

**Failing Tests:**

- JsCompatTests: Pattern 536 (Drop basic)
- LangDropSpec: drop() skips first n steps and stretches remaining

**Thesis:**
DropPattern drops initial portion and shifts remaining. Issues may be:

1. Shifting only `part` without shifting `whole`
2. Incorrect window calculation after dropping
3. hasOnset() issues after time shifting

**Investigation Steps:**

1. Read `DropPattern.kt` - check shift logic
2. Verify both part and whole are shifted
3. Test with clipped events to ensure whole is preserved
4. Run LangDropSpec test - capture actual vs expected

**Unit Tests Needed:**

```kotlin
"DropPattern shifts both part and whole" {
    val original = testEvent(
        begin = 0.5.toRational(),
        end = 1.0.toRational(),
        whole = TimeSpan(0.0.toRational(), 2.0.toRational())
    )
    val pattern = DropPattern(...)
    val events = pattern.queryArc(...)
    events.forEach { event ->
        // Both part and whole should be shifted by same offset
        event.part.begin shouldBe original.part.begin + expectedShift
        event.whole!!.begin shouldBe original.whole!!.begin + expectedShift
    }
}
```

---

### Category 6: Early Operations (3 tests)

**Failing Tests:**

- LangEarlySpec: early() works as method on StrudelPattern
- LangEarlySpec: early() works as string extension in compiled code
- LangEarlySpec: early() with continuous pattern like sine

**Thesis:**
Early shifts events backward in time. Issues:

1. Shifting only `part` without shifting `whole`
2. Continuous patterns (whole = null) being affected differently
3. hasOnset() detection after backward shift

**Investigation Steps:**

1. Find early() implementation
2. Check if TimeShiftPattern is used
3. Verify shift applies to both part and whole
4. Special handling for continuous patterns (whole = null)

---

### Category 7: Inside Operations (2 tests)

**Failing Tests:**

- LangInsideSpec: p.inside(2, x => x.late(0.1))
- LangInsideSpec: p.inside(2, x => x.late("0 0.1"))

**Thesis:**
Inside applies transformation to specific cycles. Combined with late():

1. Time shifting in inside() not applied to whole
2. Clipping to cycle bounds not preserving whole
3. Nested transformations corrupting part/whole relationship

---

### Category 8: Off Operations (1 test)

**Failing Tests:**

- LangOffSpec: off() supports custom delay time

**Thesis:**
Off creates delayed copies. Issue may be:

1. Delayed events not having whole shifted
2. Overlay/stack operation affecting part/whole
3. hasOnset() filtering removing delayed copies

---

### Category 9: Swing Operations (5 tests)

**Failing Tests:**

- LangSwingSpec: swing(2), swing(4), swingBy(0.5, 2), swingBy("[0.5 0.0]", 2), swingBy(-0.5, 2)

**Thesis:**
Swing modifies timing of alternating events. Issues:

1. Timing modification not applied to whole
2. Rhythmic shift calculations incorrect
3. hasOnset() detection broken by micro-timing adjustments

**Investigation Steps:**

1. Read `SwingPattern.kt` (already updated in Phase 1-7)
2. Verify the swing calculation applies to both part and whole
3. Check if rhythm subdivision preserves onset relationship

---

### Category 10: Take Operations (1 test)

**Failing Tests:**

- LangTakeSpec: take() with fractional steps

**Thesis:**
Take extracts a time window. With fractional steps:

1. Window calculation may clip whole incorrectly
2. Fractional boundaries causing precision issues
3. hasOnset() filtering at window edges

---

### Category 11: Complex Pattern (1 test)

**Failing Tests:**

- Song 4: Stranger Things Main Theme | Bass line

**Thesis:**
Complex pattern combining multiple operations. Likely combination of issues from above categories.
Investigate after resolving simpler categories.

---

## Investigation Priority

### Phase 1: Foundational Operations (High Impact)

1. **Euclidean Patterns** (76 tests) - Most failures, likely simple fix
2. **Struct Operations** (7 tests) - Core clipping operation
3. **Drop/Take/Early** (6 tests) - Time shifting operations

### Phase 2: Specialized Operations (Medium Impact)

4. **Swing** (5 tests) - Timing modification
5. **Pick Operations** (2 tests) - Outer join behavior
6. **Inside/Off** (3 tests) - Nested transformations

### Phase 3: Edge Cases (Low Impact)

7. **Sample LoopAt** (3 tests) - Sample-specific logic
8. **Complex Pattern** (1 test) - Integration test

---

## Investigation Methodology

For each category:

### Step 1: Read Implementation

```bash
# Find the pattern class
find . -name "*PatternName*.kt"
# Read the implementation
# Look for:
# - Event creation: are part and whole set correctly?
# - Event copying: is whole preserved or transformed correctly?
# - Time operations: are both part and whole affected?
```

### Step 2: Extract Test Case

```kotlin
// Get the failing test
// Extract expected output (from JS)
// Extract actual output (from Kotlin)
// Compare event by event
```

### Step 3: Create Hypothesis

- What operation is being performed?
- What should happen to part and whole?
- What is actually happening?

### Step 4: Write Unit Test

```kotlin
"PatternName correctly handles part/whole" {
    // Create test event with known part/whole
    val input = testEvent(...)

    // Apply operation
    val result = pattern.queryArc(...)

    // Verify part/whole transformation
    result.forEach { event ->
        // Check part transformation
        // Check whole transformation
        // Check hasOnset()
    }
}
```

### Step 5: Fix if Needed

- If unit test fails, fix the pattern implementation
- Re-run integration test
- Move to next category

---

## Success Criteria

- All 103 tests pass
- New unit tests verify part/whole correctness for each pattern
- Documentation updated with findings
- No performance regression

---

## Notes

- Start with one test per category
- Don't guess - verify with unit tests first
- Document findings for each category
- Update this document as investigation progresses
