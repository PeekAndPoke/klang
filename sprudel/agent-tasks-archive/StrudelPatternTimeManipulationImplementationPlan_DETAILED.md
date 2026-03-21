# Strudel Pattern Time Manipulation - Detailed Implementation Plan

## Overview

This plan implements pattern time manipulation functions that alter timing, duration, or selection of events:

- `pace(n)` / `steps(n)` - Sets speed so pattern completes in n steps
- `take(n)` - Keeps only first n cycles
- `drop(n)` - Skips first n cycles
- `repeatCycles(n)` - Repeats pattern n times
- `extend(factor)` - Slows down/stretches pattern (alias for slow)
- `iter(n)` / `iterBack(n)` - Divides cycle and shifts view per cycle

---

## Phase 1: Core Pattern Classes

### 1.1 TakePattern - Limit to First N Cycles

**File:** `strudel/src/commonMain/kotlin/pattern/TakePattern.kt` (NEW FILE)

**Purpose:** Filters events to only show the first n cycles of a pattern.

**Implementation:**

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Keeps only the first [cycles] cycles of the source pattern.
 * Events with begin time >= cycles are filtered out.
 *
 * Example: note("c d e f").take(2) plays c,d,e,f,c,d,e,f then silence
 */
class TakePattern(
    private val source: StrudelPattern,
    private val cycles: Rational,
) : StrudelPattern {

    override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
        // Only query events if we're within the take window
        if (begin >= cycles) {
            return emptyList()
        }

        // Clamp the query end to the take boundary
        val clampedEnd = minOf(end, cycles)

        val events = source.queryArc(begin, clampedEnd)

        // Filter out events that start at or after the cycles boundary
        return events.filter { event ->
            event.begin < cycles
        }.map { event ->
            // Clamp event end to cycles boundary if it extends beyond
            if (event.end > cycles) {
                event.copy(end = cycles)
            } else {
                event
            }
        }
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val steps: Rational? = source.steps

    companion object {
        /**
         * Creates a TakePattern from a control pattern.
         * The cycles value is sampled from the control pattern each cycle.
         */
        fun control(
            source: StrudelPattern,
            cyclesPattern: StrudelPattern
        ): StrudelPattern {
            return object : StrudelPattern {
                override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
                    // Sample the cycles value at the start of the query
                    val cyclesEvents = cyclesPattern.queryArc(begin, begin + 1.0.toRational())
                    val cyclesValue = cyclesEvents.firstOrNull()?.data?.value?.asDoubleOrNull() ?: 1.0
                    val cycles = cyclesValue.toRational()

                    // Delegate to static TakePattern
                    return TakePattern(source, cycles).queryArc(begin, end)
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val steps: Rational? = source.steps
            }
        }
    }
}
```

---

### 1.2 DropPattern - Skip First N Cycles

**File:** `strudel/src/commonMain/kotlin/pattern/DropPattern.kt` (NEW FILE)

**Purpose:** Skips the first n cycles of a pattern, time-shifting everything else backward.

**Implementation:**

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Skips the first [cycles] cycles of the source pattern.
 * Events are time-shifted backward by [cycles] amount.
 *
 * Example: note("c d e f").drop(2) skips first 2 cycles, starts at cycle 2
 */
class DropPattern(
    private val source: StrudelPattern,
    private val cycles: Rational,
) : StrudelPattern {

    override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
        // Query the source at the time-shifted position
        val shiftedBegin = begin + cycles
        val shiftedEnd = end + cycles

        val events = source.queryArc(shiftedBegin, shiftedEnd)

        // Shift all events backward by cycles
        return events.map { event ->
            event.copy(
                begin = event.begin - cycles,
                end = event.end - cycles
            )
        }
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val steps: Rational? = source.steps

    companion object {
        /**
         * Creates a DropPattern from a control pattern.
         * The cycles value is sampled from the control pattern each cycle.
         */
        fun control(
            source: StrudelPattern,
            cyclesPattern: StrudelPattern
        ): StrudelPattern {
            return object : StrudelPattern {
                override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
                    // Sample the cycles value at the start of the query
                    val cyclesEvents = cyclesPattern.queryArc(begin, begin + 1.0.toRational())
                    val cyclesValue = cyclesEvents.firstOrNull()?.data?.value?.asDoubleOrNull() ?: 0.0
                    val cycles = cyclesValue.toRational()

                    // Delegate to static DropPattern
                    return DropPattern(source, cycles).queryArc(begin, end)
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val steps: Rational? = source.steps
            }
        }
    }
}
```

---

### 1.3 RepeatCyclesPattern - Loop Pattern N Times

**File:** `strudel/src/commonMain/kotlin/pattern/RepeatCyclesPattern.kt` (NEW FILE)

**Purpose:** Repeats the first cycle of a pattern n times, then silence.

**Implementation:**

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Repeats the first [repetitions] cycles of the source pattern.
 * After [repetitions] cycles, returns silence.
 *
 * Example: note("c d e f").repeatCycles(3) plays c,d,e,f 3 times then stops
 */
class RepeatCyclesPattern(
    private val source: StrudelPattern,
    private val repetitions: Rational,
) : StrudelPattern {

    override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
        // Check if we're past the repeat window
        if (begin >= repetitions) {
            return emptyList()
        }

        // Clamp query end to the repeat boundary
        val clampedEnd = minOf(end, repetitions)

        // Query the source within the original cycle span (modulo 1)
        // Each repetition queries cycle 0 to 1 repeatedly
        val result = mutableListOf<StrudelPatternEvent>()

        var currentCycle = floor(begin.toDouble()).toInt().toRational()
        val endCycle = floor(clampedEnd.toDouble()).toInt().toRational() + 1.0.toRational()

        while (currentCycle < endCycle && currentCycle < repetitions) {
            // Calculate query range within the current cycle
            val cycleBegin = maxOf(begin - currentCycle, 0.0.toRational())
            val cycleEnd = minOf(clampedEnd - currentCycle, 1.0.toRational())

            if (cycleBegin < cycleEnd) {
                // Query the first cycle of the source pattern
                val events = source.queryArc(cycleBegin, cycleEnd)

                // Time-shift events to the current repetition
                events.forEach { event ->
                    result.add(
                        event.copy(
                            begin = event.begin + currentCycle,
                            end = event.end + currentCycle
                        )
                    )
                }
            }

            currentCycle += 1.0.toRational()
        }

        return result
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val steps: Rational? = source.steps

    companion object {
        /**
         * Creates a RepeatCyclesPattern from a control pattern.
         */
        fun control(
            source: StrudelPattern,
            repetitionsPattern: StrudelPattern
        ): StrudelPattern {
            return object : StrudelPattern {
                override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
                    val repsEvents = repetitionsPattern.queryArc(begin, begin + 1.0.toRational())
                    val repsValue = repsEvents.firstOrNull()?.data?.value?.asDoubleOrNull() ?: 1.0
                    val repetitions = repsValue.toRational()

                    return RepeatCyclesPattern(source, repetitions).queryArc(begin, end)
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val steps: Rational? = source.steps
            }
        }
    }
}
```

---

### 1.4 IterPattern - Slice and Shift Pattern

**File:** `strudel/src/commonMain/kotlin/pattern/IterPattern.kt` (NEW FILE)

**Purpose:** Divides pattern into n slices and shifts the view forward by 1 slice each cycle.

**Implementation:**

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Divides the pattern into [n] slices and shifts the view by 1/n each cycle.
 *
 * Logic: For cycle C, view starts at offset (C % n) / n within the source pattern.
 *
 * Example: note("c d e f").iter(4)
 *   Cycle 0: c d e f
 *   Cycle 1: d e f c  (shifted by 1/4)
 *   Cycle 2: e f c d  (shifted by 2/4)
 *   Cycle 3: f c d e  (shifted by 3/4)
 *   Cycle 4: c d e f  (wraps back)
 *
 * @param direction 1.0 for forward (iter), -1.0 for backward (iterBack)
 */
class IterPattern(
    private val source: StrudelPattern,
    private val n: Rational,
    private val direction: Double = 1.0,
) : StrudelPattern {

    override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
        if (n <= 0.0.toRational()) {
            return emptyList()
        }

        val nDouble = n.toDouble()
        val sliceSize = 1.0 / nDouble

        val result = mutableListOf<StrudelPatternEvent>()

        // Process each cycle in the query range
        var currentCycle = floor(begin.toDouble()).toInt()
        val endCycle = floor(end.toDouble()).toInt() + 1

        while (currentCycle < endCycle) {
            val cycleRational = currentCycle.toDouble().toRational()

            // Calculate the shift offset for this cycle
            val cycleIndex = currentCycle % nDouble.toInt()
            val shiftOffset = (cycleIndex * sliceSize * direction).toRational()

            // Calculate query bounds for this cycle
            val cycleBegin = maxOf(begin, cycleRational)
            val cycleEnd = minOf(end, cycleRational + 1.0.toRational())

            if (cycleBegin < cycleEnd) {
                // Map query range into the shifted source view
                val sourceBegin = (cycleBegin - cycleRational + shiftOffset) % 1.0.toRational()
                val sourceEnd = (cycleEnd - cycleRational + shiftOffset) % 1.0.toRational()

                // Handle wrap-around when sourceEnd < sourceBegin
                val sourceEvents = if (sourceEnd < sourceBegin) {
                    // Query wraps around the cycle boundary
                    val events1 = source.queryArc(sourceBegin, 1.0.toRational())
                    val events2 = source.queryArc(0.0.toRational(), sourceEnd)
                    events1 + events2
                } else {
                    source.queryArc(sourceBegin, sourceEnd)
                }

                // Transform events back to output time
                sourceEvents.forEach { event ->
                    var outBegin = event.begin - shiftOffset
                    var outEnd = event.end - shiftOffset

                    // Normalize to [0, 1) range
                    while (outBegin < 0.0.toRational()) outBegin += 1.0.toRational()
                    while (outEnd < 0.0.toRational()) outEnd += 1.0.toRational()
                    while (outBegin >= 1.0.toRational()) outBegin -= 1.0.toRational()
                    while (outEnd > 1.0.toRational()) outEnd -= 1.0.toRational()

                    // Shift to the current cycle
                    outBegin += cycleRational
                    outEnd += cycleRational

                    // Only add events that fall within the query range
                    if (outBegin < cycleEnd && outEnd > cycleBegin) {
                        result.add(
                            event.copy(
                                begin = maxOf(outBegin, cycleBegin),
                                end = minOf(outEnd, cycleEnd)
                            )
                        )
                    }
                }
            }

            currentCycle++
        }

        return result
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val steps: Rational? = source.steps

    companion object {
        /**
         * Creates an IterPattern from a control pattern.
         */
        fun control(
            source: StrudelPattern,
            nPattern: StrudelPattern,
            direction: Double = 1.0
        ): StrudelPattern {
            return object : StrudelPattern {
                override fun queryArc(begin: Rational, end: Rational): List<StrudelPatternEvent> {
                    val nEvents = nPattern.queryArc(begin, begin + 1.0.toRational())
                    val nValue = nEvents.firstOrNull()?.data?.value?.asDoubleOrNull() ?: 1.0
                    val n = nValue.toRational()

                    return IterPattern(source, n, direction).queryArc(begin, end)
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val steps: Rational? = source.steps
            }
        }
    }
}
```

---

## Phase 2: DSL Functions in lang_structural.kt

**File:** `strudel/src/commonMain/kotlin/lang/lang_structural.kt`

Add these functions at the end of the file (before the final closing comments).

### 2.1 pace() / steps()

**Insert after:** Line ~1668 (after the `ratio` functions)

```kotlin
// -- pace() / steps() -------------------------------------------------------------------------------------------------

private fun applyPace(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val currentSteps = source.steps?.toDouble() ?: 1.0

    if (targetSteps <= 0.0 || currentSteps <= 0.0) {
        return source
    }

    // Calculate speed adjustment: fast(targetSteps / currentSteps)
    val speedFactor = targetSteps / currentSteps

    return source.fast(speedFactor)
}

/**
 * Sets the speed so the pattern completes in n steps.
 * Adjusts tempo relative to the pattern's natural step count.
 *
 * Example: note("c d e f").pace(8) speeds up to fit 8 steps per cycle
 */
@StrudelDsl
val pace by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyPace(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.pace by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPace(p, args)
}

@StrudelDsl
val String.pace by dslStringExtension { p, args, callInfo -> p.pace(args, callInfo) }

/** Alias for pace */
@StrudelDsl
val steps by dslFunction { args, callInfo -> pace(args, callInfo) }

/** Alias for pace */
@StrudelDsl
val StrudelPattern.steps by dslPatternExtension { p, args, callInfo -> p.pace(args, callInfo) }

/** Alias for pace */
@StrudelDsl
val String.steps by dslStringExtension { p, args, callInfo -> p.pace(args, callInfo) }
```

---

### 2.2 take()

**Insert after pace():**

```kotlin
// -- take() -----------------------------------------------------------------------------------------------------------

private fun applyTake(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val cyclesArg = args.firstOrNull()
    val cyclesVal = cyclesArg?.value

    val cyclesPattern: StrudelPattern = when (cyclesVal) {
        is StrudelPattern -> cyclesVal
        else -> parseMiniNotation(cyclesArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    val staticCycles = cyclesVal?.asDoubleOrNull()

    return if (staticCycles != null) {
        TakePattern(source, staticCycles.toRational())
    } else {
        TakePattern.control(source, cyclesPattern)
    }
}

/**
 * Keeps only the first n cycles of the pattern.
 * Events after n cycles are filtered out.
 *
 * Example: note("c d e f").take(2) plays 2 cycles then silence
 */
@StrudelDsl
val take by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyTake(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.take by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTake(p, args)
}

@StrudelDsl
val String.take by dslStringExtension { p, args, callInfo -> p.take(args, callInfo) }
```

---

### 2.3 drop()

**Insert after take():**

```kotlin
// -- drop() -----------------------------------------------------------------------------------------------------------

private fun applyDrop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val cyclesArg = args.firstOrNull()
    val cyclesVal = cyclesArg?.value

    val cyclesPattern: StrudelPattern = when (cyclesVal) {
        is StrudelPattern -> cyclesVal
        else -> parseMiniNotation(cyclesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    val staticCycles = cyclesVal?.asDoubleOrNull()

    return if (staticCycles != null) {
        DropPattern(source, staticCycles.toRational())
    } else {
        DropPattern.control(source, cyclesPattern)
    }
}

/**
 * Skips the first n cycles of the pattern.
 * Events are time-shifted backward by n cycles.
 *
 * Example: note("c d e f").drop(2) starts playing from cycle 2
 */
@StrudelDsl
val drop by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyDrop(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.drop by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDrop(p, args)
}

@StrudelDsl
val String.drop by dslStringExtension { p, args, callInfo -> p.drop(args, callInfo) }
```

---

### 2.4 repeatCycles()

**Insert after drop():**

```kotlin
// -- repeatCycles() ---------------------------------------------------------------------------------------------------

private fun applyRepeatCycles(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val repsArg = args.firstOrNull()
    val repsVal = repsArg?.value

    val repsPattern: StrudelPattern = when (repsVal) {
        is StrudelPattern -> repsVal
        else -> parseMiniNotation(repsArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    val staticReps = repsVal?.asDoubleOrNull()

    return if (staticReps != null) {
        RepeatCyclesPattern(source, staticReps.toRational())
    } else {
        RepeatCyclesPattern.control(source, repsPattern)
    }
}

/**
 * Repeats the pattern n times, then silence.
 * Useful for creating finite patterns from infinite ones.
 *
 * Example: note("c d e f").repeatCycles(3) plays 3 cycles then stops
 */
@StrudelDsl
val repeatCycles by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyRepeatCycles(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.repeatCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRepeatCycles(p, args)
}

@StrudelDsl
val String.repeatCycles by dslStringExtension { p, args, callInfo -> p.repeatCycles(args, callInfo) }
```

---

### 2.5 extend()

**Insert after repeatCycles():**

```kotlin
// -- extend() ---------------------------------------------------------------------------------------------------------

/**
 * Slows down / stretches the pattern by the given factor.
 * This is an alias for slow().
 *
 * Example: note("c d e f").extend(2) plays half as fast
 */
@StrudelDsl
val extend by dslFunction { args, callInfo -> slow(args, callInfo) }

@StrudelDsl
val StrudelPattern.extend by dslPatternExtension { p, args, callInfo -> p.slow(args, callInfo) }

@StrudelDsl
val String.extend by dslStringExtension { p, args, callInfo -> p.slow(args, callInfo) }
```

---

### 2.6 iter() / iterBack()

**Insert after extend():**

```kotlin
// -- iter() -----------------------------------------------------------------------------------------------------------

private fun applyIter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    val nVal = nArg?.value

    val nPattern: StrudelPattern = when (nVal) {
        is StrudelPattern -> nVal
        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    val staticN = nVal?.asDoubleOrNull()

    return if (staticN != null) {
        IterPattern(source, staticN.toRational(), direction = 1.0)
    } else {
        IterPattern.control(source, nPattern, direction = 1.0)
    }
}

/**
 * Divides the pattern into n slices and shifts the view forward by 1 slice each cycle.
 *
 * Logic: For cycle C, view starts at offset (C % n) / n.
 *
 * Example: note("c d e f").iter(4)
 *   Cycle 0: c d e f
 *   Cycle 1: d e f c
 *   Cycle 2: e f c d
 *   Cycle 3: f c d e
 */
@StrudelDsl
val iter by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyIter(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.iter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIter(p, args)
}

@StrudelDsl
val String.iter by dslStringExtension { p, args, callInfo -> p.iter(args, callInfo) }

// -- iterBack() -------------------------------------------------------------------------------------------------------

private fun applyIterBack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    val nVal = nArg?.value

    val nPattern: StrudelPattern = when (nVal) {
        is StrudelPattern -> nVal
        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    val staticN = nVal?.asDoubleOrNull()

    return if (staticN != null) {
        IterPattern(source, staticN.toRational(), direction = -1.0)
    } else {
        IterPattern.control(source, nPattern, direction = -1.0)
    }
}

/**
 * Like iter(), but shifts backward instead of forward.
 *
 * Example: note("c d e f").iterBack(4)
 *   Cycle 0: c d e f
 *   Cycle 1: f c d e
 *   Cycle 2: e f c d
 *   Cycle 3: d e f c
 */
@StrudelDsl
val iterBack by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)
    applyIterBack(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.iterBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIterBack(p, args)
}

@StrudelDsl
val String.iterBack by dslStringExtension { p, args, callInfo -> p.iterBack(args, callInfo) }
```

---

## Phase 3: Tests

### 3.1 Pattern Tests

**File:** `strudel/src/commonTest/kotlin/pattern/TimeManipulationPatternsSpec.kt` (NEW FILE)

```kotlin
package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class TimeManipulationPatternsSpec : StringSpec({

    "TakePattern keeps only first n cycles" {
        val source = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d"))
            )
        )

        val pattern = TakePattern(source, 1.5.toRational())

        // Query 3 cycles - should only get first 1.5 cycles
        val events = pattern.queryArc(0.0.toRational(), 3.0.toRational())

        events.filter { it.data.note == "c" } shouldHaveSize 1  // Cycle 0
        events.filter { it.data.note == "d" } shouldHaveSize 1  // Cycle 0.5
        events.filter { it.begin >= 2.0.toRational() } shouldHaveSize 0  // Nothing after cycle 1.5
    }

    "DropPattern skips first n cycles" {
        val source = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d"))
            )
        )

        val pattern = DropPattern(source, 1.0.toRational())

        // Query cycles 0-2, should get cycles 1-2 from source shifted to 0-1
        val events = pattern.queryArc(0.0.toRational(), 2.0.toRational())

        events shouldHaveSize 4  // 2 cycles worth
        events[0].data.note shouldBe "c"
        events[0].begin shouldBe 0.0.toRational()  // Cycle 1 of source, shifted to 0
    }

    "RepeatCyclesPattern repeats n times then silence" {
        val source = AtomicPattern(StrudelVoiceData.empty.copy(note = "c"))

        val pattern = RepeatCyclesPattern(source, 2.0.toRational())

        // Query 4 cycles - should only get first 2
        val events = pattern.queryArc(0.0.toRational(), 4.0.toRational())

        events shouldHaveSize 2  // Only 2 cycles
        events.all { it.data.note == "c" } shouldBe true
        events.all { it.begin < 2.0.toRational() } shouldBe true
    }

    "IterPattern shifts view each cycle" {
        val source = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "e")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "f"))
            )
        )

        val pattern = IterPattern(source, 4.0.toRational(), direction = 1.0)

        // Cycle 0: c d e f
        val cycle0 = pattern.queryArc(0.0.toRational(), 1.0.toRational())
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1: d e f c (shifted by 1/4)
        val cycle1 = pattern.queryArc(1.0.toRational(), 2.0.toRational())
        cycle1.map { it.data.note } shouldBe listOf("d", "e", "f", "c")

        // Cycle 2: e f c d (shifted by 2/4)
        val cycle2 = pattern.queryArc(2.0.toRational(), 3.0.toRational())
        cycle2.map { it.data.note } shouldBe listOf("e", "f", "c", "d")
    }

    "IterPattern with direction=-1 shifts backward" {
        val source = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "e")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "f"))
            )
        )

        val pattern = IterPattern(source, 4.0.toRational(), direction = -1.0)

        // Cycle 0: c d e f
        val cycle0 = pattern.queryArc(0.0.toRational(), 1.0.toRational())
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1: f c d e (shifted backward by 1/4)
        val cycle1 = pattern.queryArc(1.0.toRational(), 2.0.toRational())
        cycle1.map { it.data.note } shouldBe listOf("f", "c", "d", "e")
    }
})
```

---

### 3.2 DSL Tests

**File:** `strudel/src/commonTest/kotlin/lang/LangTimeManipulationSpec.kt` (NEW FILE)

```kotlin
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class LangTimeManipulationSpec : StringSpec({

    "pace() adjusts speed to match target steps" {
        // Pattern with 4 steps, pace to 8 should double speed
        val p = note("c d e f").pace(8)
        val events = p.queryArc(0.0, 1.0)

        // Should have more events (2x speed = 2 cycles in 1)
        events.size shouldBeGreaterThan 4
    }

    "steps() is alias for pace()" {
        val p1 = note("c d e f").pace(8)
        val p2 = note("c d e f").steps(8)

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
    }

    "take() limits to first n cycles" {
        val p = note("c d e f").take(1)

        val events1 = p.queryArc(0.0, 1.0)
        events1 shouldHaveSize 4  // First cycle

        val events2 = p.queryArc(1.0, 2.0)
        events2 shouldHaveSize 0  // Nothing after take boundary
    }

    "drop() skips first n cycles" {
        val p = note("c d e f").drop(1)

        // Query cycle 0 should get cycle 1 from source
        val events = p.queryArc(0.0, 1.0)
        events shouldHaveSize 4
        // Events should be time-shifted
        events[0].begin shouldBe 0.0
    }

    "repeatCycles() repeats then stops" {
        val p = note("c").repeatCycles(2)

        val cycle0 = p.queryArc(0.0, 1.0)
        cycle0 shouldHaveSize 1

        val cycle1 = p.queryArc(1.0, 2.0)
        cycle1 shouldHaveSize 1

        val cycle2 = p.queryArc(2.0, 3.0)
        cycle2 shouldHaveSize 0  // After repeat limit
    }

    "extend() is alias for slow()" {
        val p1 = note("c d e f").extend(2)
        val p2 = note("c d e f").slow(2)

        val events1 = p1.queryArc(0.0, 2.0)
        val events2 = p2.queryArc(0.0, 2.0)

        events1.size shouldBe events2.size
    }

    "iter() shifts pattern each cycle" {
        val p = note("c d e f").iter(4)

        // Cycle 0
        val cycle0 = p.queryArc(0.0, 1.0)
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1 should be shifted
        val cycle1 = p.queryArc(1.0, 2.0)
        cycle1.map { it.data.note } shouldBe listOf("d", "e", "f", "c")
    }

    "iterBack() shifts pattern backward each cycle" {
        val p = note("c d e f").iterBack(4)

        // Cycle 0
        val cycle0 = p.queryArc(0.0, 1.0)
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1 should be shifted backward
        val cycle1 = p.queryArc(1.0, 2.0)
        cycle1.map { it.data.note } shouldBe listOf("f", "c", "d", "e")
    }

    "time manipulation functions work with pattern control" {
        // take() with pattern control
        val p = note("c d e f").take("1 2")

        // First cycle: take 1
        val events1 = p.queryArc(0.0, 1.0)
        events1 shouldHaveSize 4

        // Second cycle: take 2, so still playing
        val events2 = p.queryArc(1.0, 2.0)
        events2 shouldHaveSize 4
    }

    "time manipulation functions work as standalone functions" {
        val p = take(2, note("c d"))

        val events = p.queryArc(0.0, 3.0)
        events.size shouldBeGreaterThan 0
        events.all { it.begin < 2.0 } shouldBe true
    }

    "time manipulation functions work with string extension" {
        val p = "c d e f".take(1)

        val events = p.queryArc(0.0, 2.0)
        events.all { it.begin < 1.0 } shouldBe true
    }
})
```

---

## Phase 4: Integration & Documentation

### 4.1 Update Pattern Imports

**File:** `strudel/src/commonMain/kotlin/pattern/patterns.kt`

Add imports for new pattern classes (if this file exists as a central import location):

```kotlin
// Time manipulation patterns
export { TakePattern } from './TakePattern'
export { DropPattern } from './DropPattern'
export { RepeatCyclesPattern } from './RepeatCyclesPattern'
export { IterPattern } from './IterPattern'
```

---

### 4.2 Test Compatibility Data

**File:** `strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt`

Add test cases for time manipulation functions (locate appropriate section around line 800+):

```kotlin
// Time manipulation
"take" to """
    note("c d e f").take(1)
""",
"drop" to """
    note("c d e f").drop(1)
""",
"repeatCycles" to """
    note("c d e f").repeatCycles(2)
""",
"pace" to """
    note("c d e f").pace(8)
""",
"extend" to """
    note("c d e f").extend(2)
""",
"iter" to """
    note("c d e f").iter(4)
""",
"iterBack" to """
    note("c d e f").iterBack(4)
""",
```

---

## Implementation Summary

### Files Created (7 new files):

1. `strudel/src/commonMain/kotlin/pattern/TakePattern.kt`
2. `strudel/src/commonMain/kotlin/pattern/DropPattern.kt`
3. `strudel/src/commonMain/kotlin/pattern/RepeatCyclesPattern.kt`
4. `strudel/src/commonMain/kotlin/pattern/IterPattern.kt`
5. `strudel/src/commonTest/kotlin/pattern/TimeManipulationPatternsSpec.kt`
6. `strudel/src/commonTest/kotlin/lang/LangTimeManipulationSpec.kt`

### Files Modified (2 files):

1. `strudel/src/commonMain/kotlin/lang/lang_structural.kt` - Add 6 function groups
2. `strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt` - Add test cases

### Functions Implemented:

- `pace(n)` / `steps(n)` - Speed adjustment based on step count
- `take(n)` - Limit to first n cycles
- `drop(n)` - Skip first n cycles
- `repeatCycles(n)` - Repeat pattern n times
- `extend(factor)` - Slow down (alias for slow)
- `iter(n)` - Slice and shift forward
- `iterBack(n)` - Slice and shift backward

### Test Coverage:

- 5 pattern-level tests (TimeManipulationPatternsSpec.kt)
- 11 DSL-level tests (LangTimeManipulationSpec.kt)
- Total: 16 tests covering all functions

---

## Testing Commands

```bash
# Run pattern tests
./gradlew :strudel:jvmTest --tests "TimeManipulationPatternsSpec"

# Run DSL tests
./gradlew :strudel:jvmTest --tests "LangTimeManipulationSpec"

# Run all strudel tests
./gradlew :strudel:jvmTest

# Run compatibility tests
./gradlew :strudel:jvmTest --tests "JsCompatTest"
```

---

## Usage Examples

```kotlin
// pace() - adjust speed to target steps
note("c d e f").pace(8)  // Speed up to 8 steps/cycle

// take() - limit to first n cycles
note("c d e f").take(2)  // Only first 2 cycles

// drop() - skip first n cycles
note("c d e f").drop(1)  // Start from cycle 1

// repeatCycles() - finite repetition
note("c d e f").repeatCycles(3)  // Play 3 times then stop

// extend() - slow down
note("c d e f").extend(2)  // Play at half speed

// iter() - rotating view
note("c d e f").iter(4)  // Shift by 1/4 each cycle
// Cycle 0: c d e f
// Cycle 1: d e f c
// Cycle 2: e f c d
// Cycle 3: f c d e

// iterBack() - reverse rotation
note("c d e f").iterBack(4)  // Shift backward each cycle
// Cycle 0: c d e f
// Cycle 1: f c d e
// Cycle 2: e f c d
```

---

## Design Decisions

1. **Pattern Control Support**: All functions (except extend) support both static values and pattern control for dynamic
   behavior

2. **Time Shifting**: drop() shifts events backward to maintain cycle 0 alignment, while iter() uses modulo arithmetic
   for seamless wrapping

3. **Boundary Handling**: take() clamps event end times at the boundary to avoid events extending beyond the take limit

4. **Direction Parameter**: iter() uses a direction parameter (1.0 or -1.0) to support both forward and backward
   shifting with the same implementation

5. **Alias for extend**: extend() is just an alias for slow() since they have identical semantics

6. **Steps Calculation**: pace() calculates speed factor by comparing target steps to current steps, using the pattern's
   natural step count

---

## Known Limitations

1. **IterPattern Complexity**: The wrap-around logic in IterPattern is complex and may need refinement based on edge
   case testing

2. **Pattern Control Sampling**: Control patterns are sampled at cycle boundaries, which may not be ideal for all use
   cases

3. **RepeatCycles Efficiency**: RepeatCyclesPattern queries the source multiple times per render, which could be
   optimized

---

## Next Steps After Implementation

1. Run all tests to verify correctness
2. Test with GraalVM JavaScript interop
3. Add integration tests with other pattern functions
4. Document any edge cases discovered during testing
5. Consider performance optimizations for IterPattern if needed
