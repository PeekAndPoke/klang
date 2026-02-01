# LangEarlySpec Test Fixes - Part/Whole Verification

## Summary

Updated LangEarlySpec to add comprehensive part/whole timespan verification and multi-cycle testing (following the
LangSwingSpec pattern).

### Results

- **Tests Fixed**: 2 out of 3 failing tests now pass
- **Tests Remaining**: 1 test still failing (sine test)
- **Overall Progress**: 103 → 101 failing tests (2 tests fixed)

## Key Insights Discovered

### Understanding early() Implementation

The `early()` function (in `lang_tempo.kt:188-190`) shifts events backward in time by:

1. Using `applyTimeShift` with `factor = -1`
2. Shifting query time: `pattern._withQueryTime { t -> t - offset }`
3. Shifting both part AND whole: `e.copy(part = shiftedPart, whole = shiftedWhole)`

### Critical Discovery: Query Boundaries Create Clipped Events

When querying time ranges with shifted patterns, **cycle boundaries produce clipped events**:

**Example**: `note("c d").early(0.25)` queried for cycle (0, 1):

1. **Query transformation**: Query (0, 1) becomes query (0.25, 1.25) in original pattern time
2. **Events returned from pattern**:
    - Partial c from cycle 0: part=(0.25, 0.5), whole=(0, 0.5)
    - Full d from cycle 0: part=(0.5, 1.0), whole=(0.5, 1.0)
    - Partial c from cycle 1: part=(1.0, 1.25), whole=(1.0, 1.5)

3. **After time shifting by -0.25**:
    - Event 0: part=(0, 0.25), whole=(-0.25, 0.25) → **hasOnset()=false** (clipped)
    - Event 1: part=(0.25, 0.75), whole=(0.25, 0.75) → **hasOnset()=true**
    - Event 2: part=(0.75, 1.0), whole=(0.75, 1.25) → **hasOnset()=true**

**Result**: 3 events total, but only 2 have onset (will be played in actual playback)

### Important: `queryArc()` vs Playback

- **`queryArc()`**: Returns ALL events (including clipped ones without onset)
- **`StrudelPlayback`**: Filters by `hasOnset()` before scheduling

Tests call `queryArc()` directly, so they see all events including non-onset clipped fragments.

## Changes Made

### Test 1: "early() works as method on StrudelPattern"

**Before**:

```kotlin
events.size shouldBe 2  // Expected only onset events
events[0].data.note shouldBeEqualIgnoringCase "d"
events[1].data.note shouldBeEqualIgnoringCase "c"
// No part/whole checks
```

**After**:

```kotlin
// Query returns 3 events, only 2 have onset
events.size shouldBe 3
val onsetEvents = events.filter { it.hasOnset() }
onsetEvents.size shouldBe 2

// Event 0: Clipped "c" from previous cycle (NO onset)
events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl - 0.25) plusOrMinus EPSILON)
events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
events[0].hasOnset() shouldBe false  // part.begin != whole.begin

// Event 1: "d" with onset
events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
events[1].hasOnset() shouldBe true  // part.begin == whole.begin

// Event 2: "c" with onset
events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
events[2].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
events[2].hasOnset() shouldBe true

// Test across 12 cycles (like LangSwingSpec)
repeat(12) { cycle -> ... }
```

**Key Additions**:

- Multi-cycle testing (12 cycles)
- Explicit part.begin, part.end checks
- Explicit whole.begin, whole.end checks
- hasOnset() verification for each event
- Verification that clipped events have `hasOnset()=false`

### Test 2: "early() works as string extension in compiled code"

Same pattern as Test 1, updated to:

- Test across 12 cycles
- Check all 3 events (1 without onset, 2 with onset)
- Verify part/whole timespans
- Verify hasOnset() for each event

### Test 3: "early() with continuous pattern like sine" (Still Failing)

**Current Implementation**:

```kotlin
// Use sine to vary the shift amount continuously
val subject = note("c d").early(sine.range(0.0, 0.5))

// Verify each event has:
events.forEach { ev ->
    ev.data.note.shouldNotBeNull()
    ev.part.shouldNotBeNull()
    ev.whole.shouldNotBeNull()
    ev.hasOnset() shouldBe true  // All should have onset
    ev.part.begin shouldBe ev.whole!!.begin  // Verify onset relationship
    ev.part.end shouldBe ev.whole!!.end
}
```

**Status**: FAILING - needs investigation

- Continuous control pattern (sine) may produce different behavior
- May need to adjust expectations based on actual sine sampling

## Verification Methodology Applied

Following the systematic approach requested:

### 1. Read Implementation

- Found `early()` in `lang_tempo.kt:188`
- Traced to `applyTimeShift()` function
- Verified it shifts both part and whole correctly

### 2. Create Hypothesis

- **Thesis**: early() creates clipped events at cycle boundaries
- **Verification**: Confirmed by analyzing query transformation

### 3. Write Tests

- Added explicit part/whole checks
- Added hasOnset() verification
- Added multi-cycle testing (12 cycles)
- Verified clipped events don't have onset

### 4. Document Findings

- Created this document
- Explained the clipping behavior
- Provided clear before/after examples

## Next Steps

### Remaining LangEarlySpec Work

1. Fix "early() with continuous pattern like sine" test
    - Investigate sine pattern behavior
    - Adjust expectations if needed

### Apply Same Pattern to Other Failing Tests

Following the same methodology, update:

1. **LangSwingSpec** (5 failures) - Already has multi-cycle testing, just needs part/whole checks
2. **LangInsideSpec** (2 failures) - Add part/whole verification
3. **LangOffSpec** (1 failure) - Add part/whole verification
4. **LangDropSpec** (1 failure) - Add part/whole verification
5. **LangTakeSpec** (1 failure) - Add part/whole verification

### Test Pattern Template

```kotlin
"operation() test name" {
    val subject = pattern.operation(args)

    assertSoftly {
        repeat(12) { cycle ->  // Test multiple cycles
            withClue("Cycle $cycle") {
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)

                // Verify event count (including non-onset events)
                events.size shouldBe expectedTotal
                val onsetEvents = events.filter { it.hasOnset() }
                onsetEvents.size shouldBe expectedOnset

                // For each event, verify:
                events[i].part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[i].part.end.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[i].whole.shouldNotBeNull()
                events[i].whole!!.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[i].whole!!.end.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[i].hasOnset() shouldBe expectedBoolean
            }
        }
    }
}
```

## Impact

- **Confidence Level**: High - Tests now verify the exact part/whole behavior
- **Documentation**: Clear understanding of how time shifting affects part/whole
- **Pattern Established**: Can now apply same approach to other failing tests
- **Progress**: 2 tests fixed, 101 failures remaining (was 103)

## Files Modified

1. `/opt/dev/peekandpoke/klang/strudel/src/commonTest/kotlin/lang/LangEarlySpec.kt`
    - Updated 3 test cases
    - Added comprehensive part/whole verification
    - Added multi-cycle testing
    - Added hasOnset() checks

## Technical Notes

### Why Tests See More Events Than Playback

Tests use `queryArc()` which returns raw pattern output including:

- Events with onset (`hasOnset()=true`) - will be played
- Clipped fragments (`hasOnset()=false`) - will NOT be played

Playback uses `StrudelPlayback.kt` which filters:

```kotlin
.filter { it.whole == null || it.hasOnset() }
```

This is correct behavior - the pattern layer returns all events, and the playback layer decides which to play.

### Importance of Multi-Cycle Testing

Testing across multiple cycles (like LangSwingSpec does with `repeat(12)`) verifies:

- Pattern behavior is consistent across cycles
- No accumulating errors or drift
- Cycle boundaries are handled correctly
- Time arithmetic remains precise

This is critical for patterns involving time shifting, as errors compound over cycles.
