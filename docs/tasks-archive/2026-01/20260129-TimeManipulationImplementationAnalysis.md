# Time Manipulation Functions - Implementation Analysis

**Date:** 2026-01-29
**Author:** Claude (Sonnet 4.5)
**Status:** Implementation Complete but Semantically Incorrect
**Purpose:** Deep analysis for verification by another agent

---

## Executive Summary

The time manipulation functions (take, drop, repeatCycles, pace, extend, iter, iterBack) have been fully implemented in
Kotlin, compile successfully, and pass 11 out of 16 tests. However, **the implementations are fundamentally incorrect**
because they operate at the **cycle level** rather than the **step level** as specified in the Strudel documentation.

**Critical Finding:** Strudel's time manipulation functions work with **steps** (discrete events in mini-notation), not
**cycles** (complete pattern repetitions).

---

## 1. Understanding Steps vs Cycles

### 1.1 What Are Steps?

From the [official Strudel documentation](https://strudel.cc/learn/stepwise/):

> "steps are counted according to the 'top level' in mini-notation"

**Example:**

- Pattern `"a b c d"` → **4 steps** (4 top-level events)
- Pattern `"a [b c] d e"` → **4 steps** (bracketed `[b c]` counts as 1 step)
- Pattern `"a [^b c] d e"` → **5 steps** (with `^`, the bracket adds to step count)

**Key Insight:** Steps are the **structural divisions** of a pattern at the mini-notation level, not temporal cycles.

### 1.2 Steps in the Codebase

In `SequencePattern.kt`:

```kotlin
override val steps: Rational get() = patterns.size.toRational()
```

A sequence of 4 patterns has 4 steps, regardless of how many cycles those patterns span.

---

## 2. Function-by-Function Analysis

### 2.1 take(n) - "Takes n steps"

#### Official Behavior (from [docs](https://strudel.cc/learn/stepwise/#take)):

> "Selects a specific number of steps from a pattern. Positive numbers take steps from the pattern's beginning; negative
> numbers take from the end."

**Example:**

```javascript
"bd cp ht mt".take("2").sound()
// Result: "bd cp".sound()  (takes first 2 steps out of 4)
```

#### My Implementation (INCORRECT):

```kotlin
class TakePattern(
    private val source: StrudelPattern,
    private val cycles: Rational,  // ❌ Should be steps, not cycles!
)
```

**What I Did:** Filter events to only show first `n` **cycles**
**What Should Happen:** Filter to show first `n` **steps**

#### Correct Implementation Strategy:

1. Query the source pattern's `steps` property
2. Calculate how many cycles contain `n` steps: `n / source.steps`
3. Query source for that many cycles
4. Truncate events beyond step `n`

**Example Fix:**

```kotlin
class TakePattern(
    private val source: StrudelPattern,
    private val stepCount: Rational,  // ✓ Takes steps, not cycles
) {
    override fun queryArcContextual(...): List<StrudelPatternEvent> {
        val sourceSteps = source.steps ?: Rational.ONE
        val cyclesNeeded = stepCount / sourceSteps

        // Query enough cycles to get stepCount steps
        val events = source.queryArcContextual(from, from + cyclesNeeded, ctx)

        // Filter to keep only first stepCount steps
        return events.filter { /* check if event is within step range */ }
    }
}
```

---

### 2.2 drop(n) - "Drops n steps"

#### Official Behavior:

> "Drops the given number of steps from a pattern. Positive numbers remove steps from the start; negative numbers remove
> from the end."

**Example:**

```javascript
"tha dhi thom nam".drop("1").sound()
// Result: "dhi thom nam".sound()  (drops first step out of 4)
```

#### My Implementation (INCORRECT):

```kotlin
class DropPattern(
    private val source: StrudelPattern,
    private val cycles: Rational,  // ❌ Should be steps!
)
```

**What I Did:** Time-shift by skipping `n` **cycles**
**What Should Happen:** Skip first `n` **steps** and play the rest

#### Correct Implementation Strategy:

1. Calculate which cycle contains step `n`: `n / source.steps`
2. Calculate offset within that cycle: `(n % source.steps) / source.steps`
3. Query from that offset onward
4. Transform event times to start from cycle 0

---

### 2.3 pace(n) - "Fit pattern to n steps"

#### Official Behavior (from [docs](https://strudel.cc/learn/stepwise/#pace)):

> "Speeds a pattern up or down, to fit the given number of steps per cycle."

**Example:**

```javascript
sound("bd sd cp").pace(4)
// Equals: sound("{bd sd cp}%4")
```

#### My Implementation (PARTIALLY CORRECT):

```kotlin
private fun applyPace(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val currentSteps = source.steps?.toDouble() ?: 1.0

    val speedFactor = targetSteps / currentSteps  // ✓ This is correct!
    return source.fast(speedFactor)
}
```

**Assessment:** This implementation is **likely correct** because it:

1. Uses `source.steps` (✓)
2. Calculates speed ratio based on step counts (✓)
3. Applies `fast()` to adjust tempo (✓)

**Verification Needed:** Test with patterns of different step counts to confirm behavior matches JS implementation.

---

### 2.4 extend(factor) - "Slow down pattern"

#### Official Behavior:

> "Unlike `fast`, which creates nested brackets, extend maintains step relationships."

**Example:**

```javascript
"a b".extend(2)
// Equals: "a b a b" (stepwise)
```

#### My Implementation:

```kotlin
val extend by dslFunction { args, callInfo -> slow(args, callInfo) }
```

**Assessment:** This is an **alias for slow()**, which might be correct. However, the documentation suggests `extend`
might have step-aware behavior that differs from simple tempo slowing.

**Uncertainty:** Needs verification against JS implementation to confirm if `extend === slow` or if there's
step-specific logic.

---

### 2.5 repeatCycles(n) - "Repeat n times"

#### Expected Behavior (no official docs found):

Repeat the pattern `n` times, then silence.

#### My Implementation (CYCLE-BASED):

```kotlin
class RepeatCyclesPattern(
    private val source: StrudelPattern,
    private val repetitions: Rational,  // Cycles, not steps
)
```

**What I Did:** Repeat pattern for `n` cycles
**Issue:** Function name suggests **cycles**, but other functions use **steps**

**Uncertainty:** Is this function:

1. Correctly cycle-based (as name suggests)?
2. Actually step-based (to match other functions)?
3. Not a standard Strudel function (experimental/custom)?

**No official documentation found** for `repeatCycles` in Strudel docs.

---

### 2.6 iter(n) - "Rotate view each cycle"

#### Official Behavior (from [docs](https://strudel.cc/learn/stepwise/)):

Mentioned as implemented but no detailed documentation in stepwise section.

From search results:
> "`iter(4, ...)` and `iterBack` plays the subdivisions in reverse order"

#### My Implementation (COMPLEX, LIKELY INCORRECT):

```kotlin
class IterPattern(
    private val source: StrudelPattern,
    private val n: Rational,
    private val direction: Double = 1.0,
)
```

**What I Did:**

- Divide cycle into `n` slices
- Shift view by 1 slice each cycle
- Complex time transformation with wrap-around

**Problems:**

1. **Empty results** - Tests return no events (major bug)
2. **Time math errors** - Modulo and normalization logic is broken
3. **Not step-aware** - Works on cycle fractions, not steps

**Example of expected behavior (guessed):**

```javascript
note("c d e f").iter(4)
// Cycle 0: c d e f
// Cycle 1: d e f c  (rotated by 1 step)
// Cycle 2: e f c d  (rotated by 2 steps)
// Cycle 3: f c d e  (rotated by 3 steps)
```

**Critical Issue:** My implementation tries to rotate by **cycle fractions** (0.25, 0.5, 0.75) rather than by **step
positions** (step 1, step 2, step 3).

---

## 3. Root Cause Analysis

### 3.1 Fundamental Misconception

**My Mental Model:**

- Patterns repeat in **cycles** (time periods)
- `take(2)` means "take 2 cycles worth of events"
- Operations work on **temporal divisions**

**Correct Model:**

- Patterns have **steps** (structural divisions)
- `take(2)` means "take 2 steps (events)"
- Operations work on **structural positions**

### 3.2 Why This Happened

1. **Plan document ambiguity:** Original plan used "cycles" terminology
2. **Existing examples:** Other patterns (TimeShiftPattern, CompressPattern) work on cycle-time
3. **No JS source access:** Couldn't verify against original implementation
4. **Test-driven development:** Wrote tests based on incorrect understanding

### 3.3 Code Evidence of Cycle-Based Thinking

From `TakePattern.kt`:

```kotlin
// Only query events if we're within the take window
if (begin >= cycles) {  // ❌ Comparing time against cycles
    return emptyList()
}
```

Should be:

```kotlin
// Calculate which events are within the step window
val sourceSteps = source.steps ?: return emptyList()
val stepsPerCycle = sourceSteps.toDouble()
// ... step-based logic ...
```

---

## 4. Test Results Analysis

### 4.1 Passing Tests (11/16)

These tests pass but **may have incorrect expectations**:

1. ✓ `pace()` adjusts speed - Likely correct (uses `source.steps`)
2. ✓ `steps()` is alias for pace - Correct by definition
3. ✓ `take()` limits to first n cycles - **Wrong expectation** (should be steps)
4. ✓ `drop()` skips first n cycles - **Wrong expectation** (should be steps)
5. ✓ `repeatCycles()` repeats then stops - **May be correct** (if truly cycle-based)
6. ✓ `extend()` is alias for slow - **May be wrong** (might need step logic)
7. ✓ Pattern control works - Infrastructure correct, semantics wrong
8. ✓ Standalone functions work - Infrastructure correct
9. ✓ String extension works - Infrastructure correct
10. ✓ TakePattern filters events - **Wrong logic** (cycle-based)
11. ✓ DropPattern shifts time - **Wrong logic** (cycle-based)

### 4.2 Failing Tests (5/16)

1. ✗ `iter()` shifts pattern each cycle - **Empty results** (major bug)
2. ✗ `iterBack()` shifts backward - **Empty results** (major bug)
3. ✗ IterPattern shifts view - **Empty results** (major bug)
4. ✗ IterPattern with direction=-1 - **Empty results** (major bug)
5. ✗ Pattern control for various - **Broken due to iter bugs**

**Common issue:** `iter` implementation has critical bugs in time transformation logic, resulting in no events being
generated.

---

## 5. JavaScript Compatibility Tests

### 5.1 Test Status

JsCompatTest was added with these examples:

```kotlin
Example("Take basic", """note("c d e f").take(1)"""),
Example("Drop basic", """note("c d e f").drop(1)"""),
Example("Iter basic", """note("c d e f").iter(4)"""),
// ... etc
```

### 5.2 Expected vs Actual Behavior

**If JS Implementation is Step-Based:**

| Function  | Input       | Expected (Steps)        | My Implementation (Cycles)               |
|-----------|-------------|-------------------------|------------------------------------------|
| `take(1)` | `"c d e f"` | `"c"` (1 step)          | `"c d e f"` (1 cycle = all 4 steps)      |
| `take(2)` | `"c d e f"` | `"c d"` (2 steps)       | `"c d e f c d e f"` (2 cycles = 8 steps) |
| `drop(1)` | `"c d e f"` | `"d e f"` (skip 1 step) | Events from cycle 1 onward               |
| `drop(2)` | `"c d e f"` | `"e f"` (skip 2 steps)  | Events from cycle 2 onward               |

**Verdict:** If tests are run, **all take/drop tests will fail** because the behavior is completely different.

---

## 6. Correct Implementation Approach

### 6.1 Step-Aware Pattern Base

Need a helper to work with steps:

```kotlin
/**
 * Helper for step-based pattern operations.
 */
internal object StepHelper {
    /**
     * Converts step index to (cycle, offset) pair.
     * @param stepIndex The global step index (0-based)
     * @param stepsPerCycle Number of steps in one pattern cycle
     * @return Pair of (cycle number, offset within cycle 0..1)
     */
    fun stepToTime(stepIndex: Rational, stepsPerCycle: Rational): Pair<Rational, Rational> {
        val cycle = (stepIndex / stepsPerCycle).floor()
        val stepInCycle = stepIndex % stepsPerCycle
        val offset = stepInCycle / stepsPerCycle
        return cycle to offset
    }

    /**
     * Converts time to step index.
     */
    fun timeToStep(cycle: Rational, offset: Rational, stepsPerCycle: Rational): Rational {
        return cycle * stepsPerCycle + offset * stepsPerCycle
    }
}
```

### 6.2 Corrected TakePattern

```kotlin
class TakePattern(
    private val source: StrudelPattern,
    private val stepCount: Rational,  // ✓ Steps, not cycles
) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext
    ): List<StrudelPatternEvent> {
        val sourceSteps = source.steps ?: return source.queryArcContextual(from, to, ctx)

        // Convert step limit to time
        val (endCycle, endOffset) = StepHelper.stepToTime(stepCount, sourceSteps)
        val endTime = endCycle + endOffset

        // Don't query beyond the step limit
        if (from >= endTime) {
            return emptyList()
        }

        val clampedTo = minOf(to, endTime)
        val events = source.queryArcContextual(from, clampedTo, ctx)

        // Filter and clamp events at step boundary
        return events.filter { it.begin < endTime }.map { event ->
            if (event.end > endTime) {
                event.copy(end = endTime)
            } else {
                event
            }
        }
    }

    override val steps: Rational? = stepCount  // ✓ Report limited steps
}
```

### 6.3 Corrected DropPattern

```kotlin
class DropPattern(
    private val source: StrudelPattern,
    private val stepCount: Rational,  // ✓ Steps to drop
) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext
    ): List<StrudelPatternEvent> {
        val sourceSteps = source.steps ?: return source.queryArcContextual(from, to, ctx)

        // Convert step count to time offset
        val (startCycle, startOffset) = StepHelper.stepToTime(stepCount, sourceSteps)
        val dropTime = startCycle + startOffset

        // Query from drop point onward
        val shiftedFrom = from + dropTime
        val shiftedTo = to + dropTime

        val events = source.queryArcContextual(shiftedFrom, shiftedTo, ctx)

        // Shift events back to align with output time
        return events.map { event ->
            event.copy(
                begin = event.begin - dropTime,
                end = event.end - dropTime
            )
        }
    }

    override val steps: Rational? = source.steps?.let { it - stepCount }  // ✓ Reduced steps
}
```

### 6.4 Corrected IterPattern

```kotlin
class IterPattern(
    private val source: StrudelPattern,
    private val n: Rational,
    private val direction: Double = 1.0,
) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext
    ): List<StrudelPatternEvent> {
        val sourceSteps = source.steps ?: return source.queryArcContextual(from, to, ctx)

        val result = mutableListOf<StrudelPatternEvent>()
        val nInt = n.toInt()
        if (nInt == 0) return emptyList()

        var currentCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        while (currentCycle < endCycle) {
            val cycleStart = currentCycle.toRational()
            val cycleEnd = cycleStart + Rational.ONE

            // Calculate rotation for this cycle (in steps)
            val rotation = (currentCycle % nInt) * direction
            val rotationSteps = rotation.toRational()

            // Convert rotation to time offset
            val (rotCycle, rotOffset) = StepHelper.stepToTime(rotationSteps, sourceSteps)
            val timeOffset = rotCycle + rotOffset

            // Query source with rotation offset
            val queryStart = maxOf(from, cycleStart) - cycleStart + timeOffset
            val queryEnd = minOf(to, cycleEnd) - cycleStart + timeOffset

            val events = source.queryArcContextual(queryStart, queryEnd, ctx)

            // Map events back to output cycle
            events.forEach { event ->
                val outBegin = event.begin - timeOffset + cycleStart
                val outEnd = event.end - timeOffset + cycleStart

                // Handle wrap-around if needed
                if (outEnd > cycleEnd) {
                    // Split event
                    result.add(event.copy(begin = outBegin, end = cycleEnd))
                    // Wrap part goes to next cycle (handled by next iteration)
                } else if (outBegin < cycleStart) {
                    // Wrap from previous cycle
                    result.add(event.copy(begin = cycleStart, end = outEnd))
                } else {
                    result.add(event.copy(begin = outBegin, end = outEnd))
                }
            }

            currentCycle++
        }

        return result
    }

    override val steps: Rational? = source.steps
}
```

---

## 7. Recommendations for Next Agent

### 7.1 Verification Tasks

1. **Access JS Source Code**
    - Try alternate methods to fetch from Codeberg
    - Or use Strudel REPL to test behaviors interactively

2. **Test Each Function**
   ```javascript
   // In Strudel REPL
   note("c d e f").take(1).log()  // Expected: just "c"
   note("c d e f").take(2).log()  // Expected: "c d"
   note("c d e f").drop(1).log()  // Expected: "d e f"
   ```

3. **Verify Step Semantics**
    - Confirm that operations count top-level mini-notation elements
    - Test with nested patterns: `"a [b c] d e"`
    - Test with weighted patterns using `@`

### 7.2 Implementation Priority

**High Priority (Core Functions):**

1. ✅ `pace()` - Likely already correct
2. ❌ `take()` - Needs complete rewrite
3. ❌ `drop()` - Needs complete rewrite

**Medium Priority:**

4. ❓ `extend()` - Verify if it's truly just `slow()`
5. ❓ `repeatCycles()` - Verify if cycle-based or step-based
6. ❌ `iter()` - Needs step-aware rewrite

**Low Priority:**

7. ❌ `iterBack()` - Same as iter, different direction

### 7.3 Testing Strategy

1. **Unit Tests:** Rewrite tests with step-based expectations
2. **Compat Tests:** Enable JsCompatTest and fix until passing
3. **Integration Tests:** Test with complex mini-notation patterns

### 7.4 Documentation Needs

- [ ] Add step counting explanation to code comments
- [ ] Document the step → time conversion math
- [ ] Add examples showing step vs cycle differences
- [ ] Link to official Strudel docs for reference

---

## 8. Code Statistics

### 8.1 Files Created

- `TakePattern.kt` - 73 lines (needs rewrite)
- `DropPattern.kt` - 72 lines (needs rewrite)
- `RepeatCyclesPattern.kt` - 88 lines (may be correct)
- `IterPattern.kt` - 162 lines (has major bugs, needs rewrite)
- `TimeManipulationPatternsSpec.kt` - 104 lines (needs updated expectations)
- `LangTimeManipulationSpec.kt` - 124 lines (needs updated expectations)

### 8.2 Files Modified

- `lang_structural.kt` - Added ~280 lines of DSL functions (infrastructure is correct)
- `JsCompatTestData.kt` - Added 10 test cases (will fail with current implementation)

**Total New Code:** ~903 lines
**Estimated Rewrite Needed:** ~400-500 lines (pattern classes)

---

## 9. Conclusion

### 9.1 What Works

- ✅ DSL infrastructure (pattern extensions, standalone functions, string extensions)
- ✅ Pattern control integration
- ✅ Compilation and basic test infrastructure
- ✅ `pace()` likely correct (uses `source.steps`)

### 9.2 What's Broken

- ❌ **Fundamental misconception:** Implemented cycle-based instead of step-based
- ❌ `take()` operates on wrong unit (cycles vs steps)
- ❌ `drop()` operates on wrong unit (cycles vs steps)
- ❌ `iter()` has critical bugs (empty results) + wrong conceptual model
- ❌ Tests have incorrect expectations

### 9.3 Effort to Fix

**Estimated Work:**

- 4-6 hours to rewrite pattern classes with step semantics
- 2-3 hours to update and fix tests
- 1-2 hours to verify against JS implementation
- **Total: 7-11 hours of focused work**

### 9.4 Risk Assessment

**Low Risk:**

- Infrastructure is solid
- DSL layer won't need changes
- Other patterns (fast, slow, etc.) unaffected

**High Risk:**

- Might discover more semantic differences
- Step calculation edge cases with nested patterns
- Performance implications of step-to-time conversions

---

## 10. Sources & References

- [Strudel Stepwise Documentation](https://strudel.cc/learn/stepwise/) - Official docs on step-based functions
- [Strudel Technical Manual - Patterns](https://strudel.cc/technical-manual/patterns/) - Pattern architecture
- [Strudel Mini-Notation](https://strudel.cc/learn/mini-notation/) - How patterns are structured
- [Strudel Repository (moved to Codeberg)](https://codeberg.org/uzu/strudel) - Source code location
- [Strudel GitHub Mirror](https://github.com/tidalcycles/strudel) - Original repository (moved notice)

---

## Appendix A: Test Failure Examples

### A.1 Take Function Expected vs Actual

**Test Input:** `note("c d e f").take(2)`

**Expected (step-based):**

```
Events: ["c", "d"]
Time range: [0.0, 0.5)
```

**Actual (cycle-based):**

```
Events: ["c", "d", "e", "f", "c", "d", "e", "f"]
Time range: [0.0, 2.0)
```

### A.2 Iter Function Bug

**Test Input:** `note("c d e f").iter(4)`
**Expected:** Rotation each cycle
**Actual:** Empty list `[]`

**Root Cause:** Time transformation logic in IterPattern produces invalid time ranges that yield no events.

---

## Appendix B: Key Code Locations

### Pattern Implementations

- `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/pattern/TakePattern.kt`
- `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/pattern/DropPattern.kt`
- `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/pattern/RepeatCyclesPattern.kt`
- `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/pattern/IterPattern.kt`

### DSL Functions

- `/opt/dev/peekandpoke/klang/strudel/src/commonMain/kotlin/lang/lang_structural.kt` (lines 1670-1950)

### Tests

- `/opt/dev/peekandpoke/klang/strudel/src/commonTest/kotlin/pattern/TimeManipulationPatternsSpec.kt`
- `/opt/dev/peekandpoke/klang/strudel/src/commonTest/kotlin/lang/LangTimeManipulationSpec.kt`
- `/opt/dev/peekandpoke/klang/strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt` (lines 774-784)

---

**End of Analysis**
