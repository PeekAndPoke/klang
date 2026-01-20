@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.lcm
import io.peekandpoke.klang.strudel.pattern.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangStructuralInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Structural patterns
// ///

// -- hush() -----------------------------------------------------------------------------------------------------------

/** Stops all playing patterns by returning silence, ignoring all arguments. */
@StrudelDsl
val hush by dslFunction { _, _ -> silence }

@StrudelDsl
val StrudelPattern.hush by dslPatternExtension { _, _, _ -> silence }

@StrudelDsl
val String.hush by dslStringExtension { _, _, _ -> silence }

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates silence with a specific duration in steps (metrical steps). */
private fun applyGap(args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val stepsVal = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val stepsRat = stepsVal.toRational()

    return object : StrudelPattern {
        override val weight: Double = 1.0
        override val steps: Rational = stepsRat
        override fun estimateCycleDuration(): Rational = Rational.ONE

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            return emptyList()
        }
    }
}

/** Creates silence with a specific duration in steps (metrical steps). */
@StrudelDsl
val gap by dslFunction { args, /* callInfo */ _ -> applyGap(args) }

@StrudelDsl
val StrudelPattern.gap by dslPatternExtension { _, args, /* callInfo */ _ -> applyGap(args) }

@StrudelDsl
val String.gap by dslStringExtension { p, args, callInfo -> p.gap(args, callInfo) }

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
private fun applySeq(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction { args, /* callInfo */ _ ->
    // specialized modifier for seq to prioritize value
    args.toPattern {
        copy(
            value = it?.asVoiceValue(),
            gain = 1.0,
        )
    }
}

@StrudelDsl
val StrudelPattern.seq by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.seq by dslStringExtension { p, args, callInfo -> p.seq(args, callInfo) }

// -- mini() -----------------------------------------------------------------------------------------------------------

/** Parses input as mini-notation. Effectively an alias for `seq`. */
val mini by dslFunction { args, /* callInfo */ _ ->
    args.toPattern(defaultModifier)
}

// -- stack() ----------------------------------------------------------------------------------------------------------

private fun applyStack(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> StackPattern(patterns)
    }
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.stack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.stack by dslStringExtension { p, args, callInfo -> p.stack(args, callInfo) }

// -- arrange() --------------------------------------------------------------------------------------------------------

private fun applyArrange(args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val segments: List<Pair<Double, StrudelPattern>> = args.map { arg ->
        when (val argVal = arg.value) {
            // Case: pattern (defaults to 1 cycle)
            is StrudelPattern -> 1.0 to argVal
            // Case: [2, pattern]
            is List<*> if argVal.size >= 2 && argVal[0] is Number && argVal[1] is StrudelPattern -> {
                val dur = ((argVal[0] as? Number) ?: 1.0).toDouble()

                val pat = when (val patVal = argVal[1]) {
                    is StrudelPattern -> patVal
                    else -> parseMiniNotation(patVal.toString()) { text, _ ->
                        AtomicPattern(VoiceData.empty.defaultModifier(text))
                    }
                }

                dur to pat
            }
            // Case: [pattern] (defaults to 1 cycle)
            is List<*> if argVal.size == 1 -> {
                val pat = when (val patVal = argVal[0]) {
                    is StrudelPattern -> patVal
                    else -> parseMiniNotation(patVal.toString()) { text, _ ->
                        AtomicPattern(VoiceData.empty.defaultModifier(text))
                    }
                }

                1.0 to pat
            }
            // Unknown
            else -> 0.0 to silence
        }
    }.filter { it.first > 0.0 }

    return if (segments.isEmpty()) {
        silence
    } else {
        ArrangementPattern(segments)
    }
}

// arrange([2, a], b) -> 2 cycles of a, 1 cycle of b.
@StrudelDsl
val arrange by dslFunction { args, /* callInfo */ _ -> applyArrange(args) }

@StrudelDsl
val StrudelPattern.arrange by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArrange(listOf(StrudelDslArg.of(p)) + args)
}

@StrudelDsl
val String.arrange by dslStringExtension { p, args, callInfo -> p.arrange(args, callInfo) }

// -- stackBy() --------------------------------------------------------------------------------------------------------

private fun applyStackBy(patterns: List<StrudelPattern>, alignment: Double): StrudelPattern {
    if (patterns.isEmpty()) return silence

    // Get duration for each pattern
    val durations = patterns.map { it.estimateCycleDuration() }
    val maxDur = durations.maxOrNull() ?: Rational.ONE

    // Align patterns by time-shifting them
    val alignedPatterns = patterns.zip(durations).map { (pat, dur) ->
        if (dur == maxDur) {
            pat
        } else {
            val totalGap = maxDur - dur
            val timeShift = totalGap * alignment.toRational()

            // Create a pattern that queries at original time then shifts results
            object : StrudelPattern {
                override val weight: Double = pat.weight
                override val steps: Rational? = pat.steps

                override fun estimateCycleDuration(): Rational = maxDur

                override fun queryArcContextual(
                    from: Rational,
                    to: Rational,
                    ctx: QueryContext,
                ): List<StrudelPatternEvent> {
                    val results = mutableListOf<StrudelPatternEvent>()

                    // Determine which cycles of the aligned pattern we need to check
                    val startCycle = (from / maxDur).floor().toInt()
                    val endCycle = (to / maxDur).ceil().toInt()

                    for (cycle in startCycle until endCycle) {
                        val cycleOffset = Rational(cycle) * maxDur
                        val patternStart = cycleOffset + timeShift
                        val patternEnd = patternStart + dur

                        // Check if this cycle's pattern window overlaps with the query range
                        if (patternEnd > from && patternStart < to) {
                            // Query the pattern at its ORIGINAL time coordinates (0 to dur)
                            // Get events from cycle 0 of the original pattern
                            val originalStart = Rational(cycle) * dur
                            val originalEnd = originalStart + dur

                            val events = pat.queryArcContextual(originalStart, originalEnd, ctx)

                            // Shift the events to the aligned position
                            results.addAll(events.map { event ->
                                event.copy(
                                    begin = event.begin - originalStart + patternStart,
                                    end = event.end - originalStart + patternStart
                                )
                            }.filter { event ->
                                // Only include events that overlap with the query range
                                event.end > from && event.begin < to
                            })
                        }
                    }

                    return results
                }
            }
        }
    }

    return StackPattern(alignedPatterns)
}

/**
 * Stack patterns with alignment.
 * 0 = left, 0.5 = center, 1 = right.
 */
@StrudelDsl
val stackBy by dslFunction { args, /* callInfo */ _ ->
    val alignment = args.firstOrNull()?.value?.asDoubleOrNull() ?: 0.0
    val patterns = args.drop(1).toListOfPatterns(defaultModifier)

    applyStackBy(patterns = patterns, alignment = alignment)
}

// -- stackLeft() ------------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the left (start). */
@StrudelDsl
val stackLeft by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(defaultModifier), alignment = 0.0)
}

// -- stackRight() -----------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the right (end). */
@StrudelDsl
val stackRight by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(defaultModifier), alignment = 1.0)
}

// -- stackCentre() ----------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the center. */
@StrudelDsl
val stackCentre by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(defaultModifier), alignment = 0.5)
}

// -- polyrhythm() -----------------------------------------------------------------------------------------------------

/**
 * Alias for stack. Creates polyrhythms by playing patterns simultaneously.
 */
@StrudelDsl
val polyrhythm by dslFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.polyrhythm by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.polyrhythm by dslStringExtension { p, args, callInfo -> p.polyrhythm(args, callInfo) }

// -- sequenceP() ------------------------------------------------------------------------------------------------------

/**
 * Pattern of patterns sequence. Alias for seq.
 */
@StrudelDsl
val sequenceP by dslFunction { args, /* callInfo */ _ ->
    applySeq(args.toListOfPatterns(defaultModifier))
}

// -- pickRestart() ----------------------------------------------------------------------------------------------------

// pickRestart([a, b, c]) -> picks patterns sequentially per cycle (slowcat)
// TODO: make this really work. Must be dslMethod -> https://strudel.cc/learn/conditional-modifiers/#pickrestart
@StrudelDsl
val pickRestart by dslFunction { args, /* callInfo */ _ ->
    val patterns = args.toListOfPatterns(defaultModifier)
    if (patterns.isEmpty()) {
        silence
    } else {
        // seq plays all in 1 cycle. slow(n) makes each take 1 cycle.
        val s = SequencePattern(patterns)
        s.slow(patterns.size)
    }
}

// -- cat() ------------------------------------------------------------------------------------------------------------

internal fun applyCat(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> ArrangementPattern(patterns.map { 1.0 to it })
    }
}

@StrudelDsl
val cat by dslFunction { args, /* callInfo */ _ ->
    applyCat(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.cat by dslStringExtension { p, args, callInfo -> p.cat(args, callInfo) }

// -- fastcat() --------------------------------------------------------------------------------------------------------

/**
 * Concatenates patterns, squashing them all into one cycle.
 * Effectively an alias for `seq`.
 *
 * @param {patterns} patterns to concatenate
 * @example
 * fastcat("bd", "sd")
 * // same as seq("bd", "sd") or "bd sd"
 */
@StrudelDsl
val fastcat by dslFunction { args, /* callInfo */ _ ->
    applySeq(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.fastcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.fastcat by dslStringExtension { p, args, callInfo -> p.fastcat(args, callInfo) }

// -- slowcat() --------------------------------------------------------------------------------------------------------

/**
 * Concatenates patterns, each taking one full cycle.
 * Alias for `cat`.
 *
 * Note: In the JS implementation, `slowcat` ensures that internal cycles of patterns are preserved
 * (not skipped) when switching. In this implementation, it behaves like `slowcatPrime` / `cat`,
 * maintaining absolute time (which means cycles of inner patterns might be "skipped" while they are not playing).
 *
 * @param {patterns} patterns to concatenate
 * @example
 * slowcat("bd", "sd")
 * // Cycle 0: "bd"
 * // Cycle 1: "sd"
 */
@StrudelDsl
val slowcat by dslFunction { args, /* callInfo */ _ ->
    applyCat(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.slowcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.slowcat by dslStringExtension { p, args, callInfo -> p.slowcat(args, callInfo) }

// -- slowcatPrime() ---------------------------------------------------------------------------------------------------

/**
 * Like slowcat but maintains relative timing.
 * In this implementation, `cat` already maintains relative timing (using absolute time), so this is an alias.
 *
 * @param {patterns} patterns to concatenate
 */
@StrudelDsl
val slowcatPrime by dslFunction { args, /* callInfo */ _ ->
    applyCat(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.slowcatPrime by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.slowcatPrime by dslStringExtension { p, args, callInfo -> p.slowcatPrime(args, callInfo) }

// -- polymeter() ------------------------------------------------------------------------------------------------------

private fun applyPolymeter(patterns: List<StrudelPattern>, baseSteps: Int? = null): StrudelPattern {
    if (patterns.isEmpty()) return silence

    // Filter for patterns that have steps defined
    val validPatterns = patterns.filter { it.steps != null }
    if (validPatterns.isEmpty()) return silence

    val patternSteps = validPatterns.mapNotNull { it.steps?.toInt() }
    val targetSteps = baseSteps ?: lcm(patternSteps).takeIf { it > 0 } ?: 4

    val adjustedPatterns = validPatterns.map { pat ->
        val steps = pat.steps!!.toInt()
        if (steps == targetSteps) pat
        else pat.fast(targetSteps.toDouble() / steps)
    }

    return StackPattern(adjustedPatterns).let { stack ->
        object : StrudelPattern by stack {
            override val steps: Rational = targetSteps.toRational()
            override fun estimateCycleDuration(): Rational = stack.estimateCycleDuration()
        }
    }
}

/**
 * Aligns the steps of the patterns, creating polymeters.
 * The patterns are repeated until they all fit the cycle.
 *
 * For example, `polymeter("a b", "a b c")` will align the 2-step and 3-step patterns
 * to a 6-step cycle, effectively speeding them up to fit.
 *
 * @param {patterns} patterns to align
 */
@StrudelDsl
val polymeter by dslFunction { args, /* callInfo */ _ ->
    applyPolymeter(patterns = args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.polymeter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPolymeter(patterns = listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.polymeter by dslStringExtension { p, args, callInfo -> p.polymeter(args, callInfo) }

// -- polymeterSteps() -------------------------------------------------------------------------------------------------

/**
 * Polymeter with explicit step specification.
 * Speeds up or slows down patterns to fit the specified number of steps per cycle.
 *
 * @param {steps} The number of steps per cycle to align to.
 * @param {patterns} The patterns to align.
 */
@StrudelDsl
val polymeterSteps by dslFunction { args, /* callInfo */ _ ->
    val steps = args.getOrNull(0)?.value?.asIntOrNull() ?: 4
    // Remaining args are patterns
    val patterns = args.drop(1).toListOfPatterns(defaultModifier)

    applyPolymeter(patterns = patterns, baseSteps = steps)
}

// -- pure() -----------------------------------------------------------------------------------------------------------

/**
 * Creates an atomic pattern containing a single value.
 * The pattern repeats this value every cycle.
 *
 * @param {value} The value to wrap in a pattern.
 */
@StrudelDsl
val pure by dslFunction { args, /* callInfo */ _ ->
    val value = args.getOrNull(0)?.value
    AtomicPattern(VoiceData.empty.copy(value = value?.asVoiceValue()))
}

// -- struct() ---------------------------------------------------------------------------------------------------------

private fun applyStruct(source: StrudelPattern, structArg: StrudelDslArg<Any?>?): StrudelPattern {
    val structure: StrudelPattern = when (val structVal = structArg?.value) {
        null -> silence

        is StrudelPattern -> structVal

        else -> parseMiniNotation(input = structArg) { text, _ ->
            AtomicPattern(VoiceData.empty.copy(note = text))
        }
    }

    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = true
    )
}

/**
 * Structures the pattern according to another pattern (the mask).
 */
@StrudelDsl
val struct by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslFunction silence

    val pattern = listOf(args[1]).toPattern(defaultModifier)

    applyStruct(source = pattern, structArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.struct by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStruct(source = p, structArg = args.firstOrNull())
}

@StrudelDsl
val String.struct by dslStringExtension { p, args, callInfo -> p.struct(args, callInfo) }

// -- structAll() ------------------------------------------------------------------------------------------------------

private fun applyStructAll(source: StrudelPattern, structArg: StrudelDslArg<Any?>?): StrudelPattern {
    val structure: StrudelPattern = when (val structVal = structArg?.value) {
        null -> silence

        is StrudelPattern -> structVal

        else -> parseMiniNotation(input = structArg) { text, _ ->
            AtomicPattern(VoiceData.empty.copy(note = text))
        }
    }

    // We use a different implementation for structAll that preserves all source events
    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = false
    )
}

/**
 * Structures the pattern according to another pattern, keeping all source events that overlap.
 *
 * Example: structAll("x", note("c e")) -> keeps both c and e within the x window
 */
@StrudelDsl
val structAll by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslFunction silence

    val pattern = listOf(args[1]).toPattern(defaultModifier)

    applyStructAll(source = pattern, structArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.structAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStructAll(source = p, structArg = args.firstOrNull())
}

@StrudelDsl
val String.structAll by dslStringExtension { p, args, callInfo -> p.structAll(args, callInfo) }

// -- mask() -----------------------------------------------------------------------------------------------------------

private fun applyMask(source: StrudelPattern, maskArg: StrudelDslArg<Any?>?): StrudelPattern {
    val maskPattern: StrudelPattern = when (val maskVal = maskArg?.value) {
        null -> silence

        is StrudelPattern -> maskVal

        else -> parseMiniNotation(input = maskArg) { text, _ ->
            AtomicPattern(VoiceData.empty.copy(note = text))
        }
    }

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Filters the pattern using a boolean mask.
 * Only events from the source that overlap with "truthy" events in the mask are kept.
 */
@StrudelDsl
val mask by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslFunction silence

    val pattern = listOf(args[1]).toPattern(defaultModifier)

    applyMask(source = pattern, maskArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.mask by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMask(source = p, maskArg = args.firstOrNull())
}

@StrudelDsl
val String.mask by dslStringExtension { p, args, callInfo -> p.mask(args, callInfo) }

// -- maskAll() --------------------------------------------------------------------------------------------------------

private fun applyMaskAll(source: StrudelPattern, maskArg: StrudelDslArg<Any?>?): StrudelPattern {
    val maskPattern: StrudelPattern = when (val maskVal = maskArg?.value) {
        null -> silence

        is StrudelPattern -> maskVal

        else -> parseMiniNotation(input = maskArg) { text, _ ->
            AtomicPattern(VoiceData.empty.copy(note = text))
        }
    }

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = false
    )
}

/**
 * Filters the pattern using a mask, keeping all source events that overlap with the mask's structure.
 */
@StrudelDsl
val maskAll by dslFunction { args, /* callInfo */ _ ->
    val maskArg = args.getOrNull(0)

    val source = args.map { it.value }.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg?.value is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyMaskAll(source, maskArg)
}

@StrudelDsl
val StrudelPattern.maskAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMaskAll(source = p, maskArg = args.firstOrNull())
}

@StrudelDsl
val String.maskAll by dslStringExtension { p, args, callInfo -> p.maskAll(args, callInfo) }

// -- filter() ---------------------------------------------------------------------------------------------------------

private fun applyFilter(source: StrudelPattern, predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern {
    return object : StrudelPattern {
        override val weight: Double get() = source.weight
        override val steps: Rational? get() = source.steps

        override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            return source.queryArcContextual(from, to, ctx).filter(predicate)
        }
    }
}

/** Filters haps using the given function. */
@StrudelDsl
val filter by dslFunction { _, _ -> silence }

@Suppress("unused")
@StrudelDsl
fun filter(ignored: (StrudelPatternEvent) -> Boolean): StrudelPattern = silence

/** Filters haps using the given function. */
@StrudelDsl
val StrudelPattern.filter by dslPatternExtension { p, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((StrudelPatternEvent) -> Boolean)? =
        args.firstOrNull()?.value as? (StrudelPatternEvent) -> Boolean

    if (predicate != null) applyFilter(source = p, predicate = predicate) else p
}

/** Filters haps using the given function. */
fun StrudelPattern.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern =
    applyFilter(source = this, predicate = predicate)

/** Filters haps using the given function. */
@StrudelDsl
val String.filter by dslStringExtension { p, args, callInfo -> p.filter(args, callInfo) }

/** Filters haps using the given function. */
fun String.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern = filter(predicate as Any)

// -- filterWhen() -----------------------------------------------------------------------------------------------------

/**
 * Filters haps by their begin time.
 *
 * @param {predicate} function to test the begin time (Double)
 */
@StrudelDsl
val filterWhen by dslFunction { args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.getOrNull(0)?.value as? (Double) -> Boolean

    val pat: StrudelPattern = args.getOrNull(1)?.value as? StrudelPattern ?: silence

    if (predicate != null) applyFilter(pat) { predicate(it.begin.toDouble()) } else pat
}

@Suppress("unused")
@StrudelDsl
fun filterWhen(predicate: (Double) -> Boolean): StrudelPattern = silence

@StrudelDsl
val StrudelPattern.filterWhen by dslPatternExtension { source, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.firstOrNull()?.value as? (Double) -> Boolean

    if (predicate != null) applyFilter(source = source) { predicate(it.begin.toDouble()) } else source
}

@StrudelDsl
fun StrudelPattern.filterWhen(predicate: (Double) -> Boolean): StrudelPattern =
    applyFilter(source = this) { predicate(it.begin.toDouble()) }

@StrudelDsl
val String.filterWhen by dslStringExtension { source, args, callInfo -> source.filterWhen(args, callInfo) }

@StrudelDsl
fun String.filterWhen(predicate: (Double) -> Boolean): StrudelPattern = filterWhen(predicate as Any)

// -- bypass() ---------------------------------------------------------------------------------------------------------

private fun applyBypass(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source

    val condition = args.toPattern(defaultModifier)
    val conditionNot = condition.not()

    return StructurePattern(
        source = source,
        other = conditionNot,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Returns silence when the condition is true (bypasses the pattern).
 * Supports both static values and patterns.
 */
@StrudelDsl
val bypass by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

@StrudelDsl
val StrudelPattern.bypass by dslPatternExtension { p, args, /* callInfo */ _ -> applyBypass(p, args) }

@StrudelDsl
val String.bypass by dslStringExtension { p, args, callInfo -> p.bypass(args, callInfo) }

// -- superimpose() ----------------------------------------------------------------------------------------------------

/**
 * Layers a modified version of the pattern on top of itself.
 *
 * Example: s("bd sd").superimpose { it.fast(2) }
 */
@StrudelDsl
val StrudelPattern.superimpose by dslPatternExtension { p, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.firstOrNull()?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    SuperimposePattern(p, transform)
}

@StrudelDsl
val String.superimpose by dslStringExtension { p, args, callInfo -> p.superimpose(args, callInfo) }

// -- layer() //////----------------------------------------------------------------------------------------------------

/**
 * Applies multiple transformation functions to the pattern and stacks the results.
 *
 * Example: s("bd").layer({ it.fast(2) }, { it.rev() })
 */
@StrudelDsl
val StrudelPattern.layer by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transforms: List<(StrudelPattern) -> StrudelPattern> =
        args.map { it.value }.filterIsInstance<(StrudelPattern) -> StrudelPattern>()

    if (transforms.isEmpty()) {
        silence
    } else {
        val patterns = transforms.mapNotNull { transform ->
            try {
                transform(p)
            } catch (e: Exception) {
                println("Error applying layer transform: ${e.stackTraceToString()}")
                null
            }
        }

        if (patterns.size == 1) {
            patterns.first()
        } else {
            StackPattern(patterns)
        }
    }
}

@StrudelDsl
val String.layer by dslStringExtension { p, args, callInfo -> p.layer(args, callInfo) }

/**
 * Alias for [layer].
 */
@StrudelDsl
val StrudelPattern.apply by dslPatternExtension { p, args, callInfo -> p.layer(args, callInfo) }

@StrudelDsl
val String.apply by dslStringExtension { p, args, callInfo -> p.apply(args, callInfo) }

// -- zoom() -----------------------------------------------------------------------------------------------------------

private fun applyZoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val startArg = args.getOrNull(0)
    val startVal = startArg?.value
    val endArg = args.getOrNull(1)
    val endVal = endArg?.value

    // Parse the start argument into a pattern
    val startPattern: StrudelPattern = when (startVal) {
        is StrudelPattern -> startVal

        else -> parseMiniNotation(startArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    // Parse the end argument into a pattern
    val endPattern: StrudelPattern = when (val endVal = endArg?.value) {
        is StrudelPattern -> endVal

        else -> parseMiniNotation(endArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have static values for optimization
    val staticStart = startVal?.asDoubleOrNull()
    val staticEnd = endVal?.asDoubleOrNull()

    return if (staticStart != null && staticEnd != null) {
        // Static path: use the simple early/fast approach
        val duration = staticEnd - staticStart
        if (duration <= 0.0) silence
        else source.early(staticStart).fast(duration)
    } else {
        // Dynamic path: ZoomPatternWithControl is necessary because we need to
        // pair up start/end events together, not just use pattern arithmetic.
        // endPattern.sub(startPattern) would use endPattern's structure which isn't correct.
        ZoomPatternWithControl(source, startPattern, endPattern)
    }
}

/**
 * Plays a portion of a pattern, specified by the beginning and end of a time span.
 * The new resulting pattern is played over the time period of the original pattern.
 *
 * @example
 * s("bd*2 hh*3 [sd bd]*2 perc").zoom(0.25, 0.75)
 * // s("hh*3 [sd bd]*2") // equivalent
 *
 * @param {start} start of the zoom window (0.0 to 1.0)
 * @param {end}   end of the zoom window (0.0 to 1.0)
 */
@StrudelDsl
val StrudelPattern.zoom by dslPatternExtension { p, args, /* callInfo */ _ -> applyZoom(p, args) }

/**
 * Plays a portion of a pattern, specified by the beginning and end of a time span.
 */
fun StrudelPattern.zoom(start: Double, end: Double): StrudelPattern =
    applyZoom(this, listOf(start, end).asStrudelDslArgs())

@StrudelDsl
val String.zoom by dslStringExtension { p, args, callInfo -> p.zoom(args, callInfo) }

@StrudelDsl
fun String.zoon(start: Double, end: Double): StrudelPattern = this.zoom(start, end)

// -- bite() -----------------------------------------------------------------------------------------------------------

private fun applyBite(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.getOrNull(0)
    val indicesArg = args.getOrNull(1)

    // Parse the n argument into a pattern
    val nPattern: StrudelPattern = when (val nVal = nArg?.value) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("4")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    // Parse the indices argument into a pattern
    val indices: StrudelPattern = when (val indicesVal = indicesArg?.value) {
        null -> return silence

        is StrudelPattern -> indicesVal

        else -> parseMiniNotation(input = indicesArg) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for n for optimization
    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use the original inline implementation
        object : StrudelPattern {
            override val weight: Double get() = source.weight
            override val steps: Rational? get() = source.steps
            override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                val indexEvents = indices.queryArcContextual(from, to, ctx)

                return indexEvents.flatMap { ev ->
                    val idx = ev.data.value?.asInt ?: 0

                    // Handle wrapping like JS .mod(1)
                    val normalizedIdx = idx.mod(staticN)
                    val safeIdx = if (normalizedIdx < 0) normalizedIdx + staticN else normalizedIdx

                    val start = safeIdx.toDouble() / staticN
                    val end = (safeIdx + 1.0) / staticN

                    val slice = source.zoom(start, end)

                    val dur = ev.dur.toDouble()
                    if (dur <= 0.0) return@flatMap emptyList()

                    val fitted = slice.fast(1.0 / dur).late(ev.begin.toDouble())

                    fitted.queryArcContextual(ev.begin, ev.end, ctx)
                }
            }
        }
    } else {
        // Dynamic path: use pattern-controlled bite
        BitePatternWithControl(source, nPattern, indices)
    }
}

/**
 * Splits a pattern into the given number of slices, and plays them according to a pattern of slice numbers.
 *
 * `bite(n, indices)` is a function for slicing and rearranging a pattern.
 *
 * Here is the conceptual breakdown:
 *
 * **1. Slice the Source**:
 *
 * It takes the **source pattern** (the one you call `.bite()` on) and conceptually cuts each cycle into
 * `n` **equal slices**.
 * - If `n` is 4, slice 0 is `0.0 - 0.25`, slice 1 is `0.25 - 0.5`, etc.
 *
 * **2. Read the Indices**:
 *
 * It looks at the `indices` **pattern**. This pattern tells `bite` which slice to play at what time.
 *
 * **3. Playback**:
 *
 * For each event in the `indices` pattern:
 * - It takes the value (an integer) as the **slice index**.
 * - It grabs that specific slice from the **source pattern**.
 * - It plays that slice **fitting exactly into the duration** of the index event.
 *
 * **Example:**
 * `n("0 1 2 3").bite(4, "0 1 2 3")`
 * - **Source**: `n("0 1 2 3")` has "0" at the first quarter, "1" at the second, etc.
 * - **Slices (n=4)**:
 *     - Slice 0 contains "0"
 *     - Slice 1 contains "1"
 *     - Slice 2 contains "2"
 *     - Slice 3 contains "3"
 *
 * - **Indices**: `"0 1 2 3"` creates 4 events, each 1/4 cycle long.
 *     - Event 1 (0.0-0.25): Play Slice 0.
 *     - Event 2 (0.25-0.5): Play Slice 1.
 *     - ...
 *
 * - **Result**: The original pattern is reconstructed exactly.
 *
 * **Example 2 (Reordering):**
 * `n("0 1 2 3").bite(4, "3 2 1 0")`
 * - Event 1 (0.0-0.25): Play Slice 3 (which contains "3").
 * - Event 2 (0.25-0.5): Play Slice 2 (which contains "2").
 * - **Result**: "3 2 1 0" (The pattern is reversed).
 *
 * **Example 3 (Rhythmic variation):**
 * `n("0 1 2 3").bite(4, "0*2 1")`
 * - Event 1 (0.0-0.125): Play Slice 0 (compressed to fit 1/8th cycle).
 * - Event 2 (0.125-0.25): Play Slice 0 (compressed to fit 1/8th cycle).
 * - Event 3 (0.25-0.5): Play Slice 1 (fits 1/4 cycle).
 * - **Result**: "0" played twice quickly, then "1".
 *
 * @param n number of slices
 * @param indices pattern of slice indices
 */
@StrudelDsl
val StrudelPattern.bite by dslPatternExtension { p, args, /* callInfo */ _ -> applyBite(p, args) }

@StrudelDsl
val String.bite by dslStringExtension { p, args, callInfo -> p.bite(args, callInfo) }

// -- segment() --------------------------------------------------------------------------------------------------------

private fun applySegment(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()

    val nPattern: StrudelPattern = when (val nVal = nArg?.value) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use original implementation with struct + fast
        val structPat = parseMiniNotation("x") { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }

        source.struct(structPat.fast(staticN))
    } else {
        // Dynamic path: use SegmentPatternWithControl which properly slices each timespan
        SegmentPatternWithControl(source, nPattern)
    }
}

/**
 * Samples the pattern at a rate of n events per cycle. Useful for turning a continuous pattern into a discrete one.
 *
 * @example
 * note(saw.range(40,52).segment(24))
 *
 * @name segment
 * @synonyms seg
 * @param {number} segments number of segments per cycle
 */
@StrudelDsl
val StrudelPattern.segment by dslPatternExtension { p, args, /* callInfo */ _ -> applySegment(p, args) }

@StrudelDsl
fun StrudelPattern.segment(n: Int) = this.segment(n as Any)

@StrudelDsl
val String.segment by dslStringExtension { p, args, callInfo -> p.segment(args, callInfo) }

@StrudelDsl
fun String.segment(n: Int) = this.segment(n as Any)

/** Alias for [segment] */
@StrudelDsl
val StrudelPattern.seg by dslPatternExtension { p, args, callInfo -> p.segment(args, callInfo) }

/** Alias for [segment] */
@StrudelDsl
fun StrudelPattern.seg(n: Int) = this.segment(n as Any)

/** Alias for [segment] */
@StrudelDsl
val String.seg by dslStringExtension { p, args, callInfo -> p.segment(args, callInfo) }

/** Alias for [segment] */
@StrudelDsl
fun String.seg(n: Int) = this.seg(n as Any)

// -- euclid() ---------------------------------------------------------------------------------------------------------

private fun applyEuclid(source: StrudelPattern, pulses: Int, steps: Int, rotation: Int): StrudelPattern {
    return EuclideanPattern.create(
        inner = source,
        pulses = pulses,
        steps = steps,
        rotation = rotation,
    )
}

/**
 * Changes the structure of the pattern to form an Euclidean rhythm.
 * Euclidean rhythms are rhythms obtained using the greatest common
 * divisor of two numbers.
 *
 * @param {pulses}   the number of onsets/beats
 * @param {steps}    the number of steps to fill
 * @param {pattern}  the pattern to apply the euclid to
 */
@StrudelDsl
val euclid by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(2).toPattern(defaultModifier)

    pattern.euclid(args)
}

@StrudelDsl
val StrudelPattern.euclid by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = 0)
    } else {
        EuclideanPatternWithControl(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = false)
    }
}

@StrudelDsl
val String.euclid by dslStringExtension { p, args, callInfo -> p.euclid(args, callInfo) }

// -- euclidRot() ------------------------------------------------------------------------------------------------------

/**
 * Like `euclid`, but has an additional parameter for 'rotating' the resulting sequence.
 *
 * @param {pulses}   the number of onsets/beats
 * @param {steps}    the number of steps to fill
 * @param {rotation} offset in steps
 * @param {pattern}  the pattern to apply the euclid to
 */
@StrudelDsl
val euclidRot by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(3).toPattern(defaultModifier)

    pattern.euclidRot(args)
}

@StrudelDsl
val StrudelPattern.euclidRot by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val rotationArg = args.getOrNull(2)
    val rotationVal = rotationArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPatternWithControl(p, pulsesPattern, stepsPattern, rotationPattern, legato = false)
    }
}

@StrudelDsl
val String.euclidRot by dslStringExtension { p, args, callInfo -> p.euclidRot(args, callInfo) }

/** Alias for [euclidRot] */
@StrudelDsl
val euclidrot by dslFunction { args, /* callInfo */ _ -> euclidRot(args) }

/** Alias for [euclidRot] */
@StrudelDsl
val StrudelPattern.euclidrot by dslPatternExtension { p, args, callInfo -> p.euclidRot(args, callInfo) }

/** Alias for [euclidRot] */
@StrudelDsl
val String.euclidrot by dslStringExtension { p, args, callInfo -> p.euclidRot(args, callInfo) }

// -- bjork() ----------------------------------------------------------------------------------------------------------

/**
 * Euclidean rhythm specifying parameters as a list.
 *
 * @param {list}    List with [pulses, steps, rotation]
 * @param {pattern} the pattern to apply the euclid to
 */
@StrudelDsl
val bjork by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(defaultModifier)

    pattern.bjork(args)
}

@StrudelDsl
val StrudelPattern.bjork by dslPatternExtension { p, args, /* callInfo */ _ ->
    val list = args.getOrNull(0)?.value as? List<*>
        ?: args.map { it.value }

    val pulsesVal = list.getOrNull(0)
    val stepsVal = list.getOrNull(1)
    val rotationVal = list.getOrNull(2)

    val pulsesPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val rotationPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPatternWithControl(
            inner = p,
            pulsesPattern = pulsesPattern,
            stepsPattern = stepsPattern,
            rotationPattern = rotationPattern,
            legato = false
        )
    }
}

@StrudelDsl
val String.bjork by dslStringExtension { p, args, callInfo -> p.bjork(args, callInfo) }

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

/**
 * Similar to `euclid`, but each pulse is held until the next pulse, so there will be no gaps.
 *
 * @param {pulses}   the number of onsets/beats
 * @param {steps}    the number of steps to fill
 * @param {pattern}  the pattern to apply the euclid to
 */
@StrudelDsl
val euclidLegato by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(2).toPattern(defaultModifier)

    pattern.euclidLegato(args)
}

@StrudelDsl
val StrudelPattern.euclidLegato by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = 0,
        )
    } else {
        EuclideanPatternWithControl(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = true)
    }
}

@StrudelDsl
val String.euclidLegato by dslStringExtension { p, args, callInfo -> p.euclidLegato(args, callInfo) }

// -- euclidLegatoRot() ------------------------------------------------------------------------------------------------

/**
 * Similar to `euclid`, but each pulse is held until the next pulse,
 * so there will be no gaps, and has an additional parameter for 'rotating' the resulting sequence
 *
 * @param {pulses}   the number of onsets/beats
 * @param {steps}    the number of steps to fill
 * @param {rotation} offset in steps
 * @param {pattern}  the pattern to apply the euclid to
 */
@StrudelDsl
val euclidLegatoRot by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(3).toPattern(defaultModifier)

    pattern.euclidLegatoRot(args)
}

@StrudelDsl
val StrudelPattern.euclidLegatoRot by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val rotationArg = args.getOrNull(2)
    val rotationVal = rotationArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation,
        )
    } else {
        EuclideanPatternWithControl(
            inner = p,
            pulsesPattern = pulsesPattern,
            stepsPattern = stepsPattern,
            rotationPattern = rotationPattern,
            legato = true
        )
    }
}

@StrudelDsl
val String.euclidLegatoRot by dslStringExtension { p, args, callInfo -> p.euclidLegatoRot(args, callInfo) }

// -- euclidish() ------------------------------------------------------------------------------------------------------

private fun applyEuclidish(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val grooveArg = args.getOrNull(2)
    val grooveVal = grooveArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveVal) {
        is StrudelPattern -> grooveVal
        else -> {
            parseMiniNotation(grooveArg ?: StrudelDslArg.of("0")) { text, _ ->
                AtomicPattern(VoiceData.empty.defaultModifier(text))
            }
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    return if (staticPulses != null && staticSteps != null) {
        // Static path: use original EuclideanMorphPattern
        if (staticPulses <= 0 || staticSteps <= 0) {
            silence
        } else {
            val structPattern = EuclideanMorphPattern(
                nPulses = staticPulses, nSteps = staticSteps, groove = groovePattern
            )

            source.struct(structPattern)
        }
    } else {
        val structPattern = EuclideanMorphPatternWithControl(
            pulsesPattern = pulsesPattern, stepsPattern = stepsPattern, groovePattern = groovePattern
        )

        source.struct(structPattern)
    }
}

/**
 * A 'euclid' variant with an additional parameter that morphs the resulting
 * rhythm from 0 (no morphing) to 1 (completely 'even').
 *
 * @param {pulses} number of onsets
 * @param {steps}  number of steps to fill
 * @param {groove} morph factor (0..1), can be a pattern
 * @param {pattern} source pattern
 */
@StrudelDsl
val euclidish by dslFunction { args, /* callInfo */ _ ->
    // euclidish(pulses, steps, groove, pat)
    val pattern = args.drop(3).toPattern(defaultModifier)
    applyEuclidish(pattern, args.take(3))
}

@StrudelDsl
val StrudelPattern.euclidish by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyEuclidish(p, args)
}

@StrudelDsl
val String.euclidish by dslStringExtension { p, args, callInfo -> p.euclidish(args, callInfo) }

/** Alias for [euclidish] */
@StrudelDsl
val eish by dslFunction { args, callInfo -> euclidish(args, callInfo) }

/** Alias for [euclidish] */
@StrudelDsl
val StrudelPattern.eish by dslPatternExtension { p, args, callInfo -> p.euclidish(args, callInfo) }

/** Alias for [euclidish] */
@StrudelDsl
val String.eish by dslStringExtension { p, args, callInfo -> p.euclidish(args, callInfo) }


// -- run() ------------------------------------------------------------------------------------------------------------

private fun applyRun(n: Int): StrudelPattern {
    // TODO: support control pattern

    if (n <= 0) return silence
    // "0 1 2 ... n-1"
    // equivalent to saw.range(0, n).round().segment(n) in JS
    // But we can just create a sequence directly.
    val items = (0 until n).map {
        AtomicPattern(VoiceData.empty.copy(value = it.asVoiceValue()))
    }

    return SequencePattern(items)
}

/**
 * A discrete pattern of numbers from 0 to n-1.
 *
 * @param n number of steps
 * @example
 * n(run(4)).scale("C4:pentatonic")
 * // n("0 1 2 3").scale("C4:pentatonic")
 */
@StrudelDsl
val run by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    applyRun(n)
}

// -- binaryN() --------------------------------------------------------------------------------------------------------

private fun applyBinaryN(n: Int, bits: Int): StrudelPattern {
    if (bits <= 0) return silence

    val items = (0 until bits).map { i ->
        // JS: const bitPos = run(nBits).mul(-1).add(nBits.sub(1));
        // This effectively iterates bits from MSB to LSB?
        // "1 1 0 1" for 5 (101) with 4 bits -> 0101?
        // JS example: binaryN(55532, 16) -> "1 1 0 1 1 0 0 0 1 1 1 0 1 1 0 0"
        // This order is MSB first (big-endian).

        // Shift: bits - 1 - i
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        AtomicPattern(VoiceData.empty.copy(value = bit.asVoiceValue()))
    }
    return SequencePattern(items)
}

/**
 * Creates a binary pattern from a number, padded to n bits long.
 *
 * @name binaryN
 * @param {number} n - input number to convert to binary
 * @param {number} bits - pattern length
 * @example
 * "hh".s().struct(binaryN(5, 4))
 * // "hh".s().struct("0 1 0 1")
 */
@StrudelDsl
val binaryN by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.value?.asIntOrNull() ?: 16

    applyBinaryN(n, bits)
}

// -- binary() ---------------------------------------------------------------------------------------------------------

/**
 * Creates a binary pattern from a number.
 * The number of bits is automatically calculated.
 *
 * @name binary
 * @param {number} n - input number to convert to binary
 * @example
 * "hh".s().struct(binary(5))
 * // "hh".s().struct("1 0 1")
 */
@StrudelDsl
val binary by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    if (n == 0) {
        applyBinaryN(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryN(n, bits)
    }
}

// -- binaryNL() -------------------------------------------------------------------------------------------------------

private fun applyBinaryNL(n: Int, bits: Int): StrudelPattern {
    if (bits <= 0) return silence

    val bitList = (0 until bits).mapNotNull { i ->
        // Shift: bits - 1 - i (MSB first)
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        bit.asVoiceValue()
    }

    // Returns a single event containing the list of bits as a Seq value
    return AtomicPattern(
        VoiceData.empty.copy(value = VoiceValue.Seq(bitList))
    )
}

/**
 * Creates a binary list pattern from a number, padded to n bits long.
 *
 * @name binaryNL
 * @param {number} n - input number to convert to binary
 * @param {number} bits - pattern length, defaults to 16
 */
@StrudelDsl
val binaryNL by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.value?.asIntOrNull() ?: 16
    applyBinaryNL(n, bits)
}

// -- binaryL() --------------------------------------------------------------------------------------------------------

/**
 * Creates a binary list pattern from a number.
 *
 * @name binaryL
 * @param {number} n - input number to convert to binary
 */
@StrudelDsl
val binaryL by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    if (n == 0) {
        applyBinaryNL(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryNL(n, bits)
    }
}
