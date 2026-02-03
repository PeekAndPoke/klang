@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.lcm
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

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
fun applyGap(args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val stepsVal = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val stepsRat = stepsVal.toRational()

    return GapPattern(stepsRat)
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
fun applySeq(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction { args, /* callInfo */ _ -> args.toPattern() }

@StrudelDsl
val StrudelPattern.seq by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns())
}

@StrudelDsl
val String.seq by dslStringExtension { p, args, callInfo -> p.seq(args, callInfo) }

// -- mini() -----------------------------------------------------------------------------------------------------------

/** Parses input as mini-notation. Effectively an alias for `seq`. */
val mini by dslFunction { args, /* callInfo */ _ -> args.toPattern() }

/** Parses input as mini-notation. Effectively an alias for `seq`. */
val String.mini by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p }

// -- stack() ----------------------------------------------------------------------------------------------------------

fun applyStack(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> StackPattern(patterns)
    }
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.stack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val String.stack by dslStringExtension { p, args, callInfo -> p.stack(args, callInfo) }

// -- arrange() --------------------------------------------------------------------------------------------------------

fun applyArrange(args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val segments = args.parseWeightedArgs()

    if (segments.isEmpty()) return silence

    val totalDuration = segments.sumOf { it.first }

    // 1. fast(dur) speeds up the pattern to play 'dur' times in 1 cycle
    // 2. withWeight(dur) tells SequencePattern to allocate 'dur' proportional space
    // 3. SequencePattern compresses it to fit that space
    // 4. slow(total) stretches the whole 1-cycle sequence to the total duration
    val processedPatterns = segments.map { (dur, pat) ->
        pat.fast(dur).withWeight(dur)
    }

    return SequencePattern(processedPatterns).slow(totalDuration)
}

@StrudelDsl
val arrange by dslFunction { args, /* callInfo */ _ -> applyArrange(args) }

@StrudelDsl
val StrudelPattern.arrange by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArrange(listOf(StrudelDslArg.of(p)) + args)
}

@StrudelDsl
val String.arrange by dslStringExtension { p, args, callInfo -> p.arrange(args, callInfo) }

// -- stepcat() / timeCat() --------------------------------------------------------------------------------------------

fun applyStepcat(args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val segments = args.parseWeightedArgs()

    if (segments.isEmpty()) return silence

    // Parse arguments into weighted patterns
    val patterns = segments.map { (dur, pat) ->
        pat.withWeight(dur)
    }

    // Use SequencePattern which handles weighted time distribution and compression to 1 cycle
    return SequencePattern(patterns)
}

/**
 * Concatenates patterns proportionally by their durations, then compresses the result to fit exactly one cycle.
 *
 * Each segment is specified as [duration, pattern]. If only a pattern is given, duration defaults to 1.
 * The total sum of durations determines how much the sequence is sped up to fit into 1 cycle.
 *
 * @example
 * timeCat([1, "a"], [3, "b"])
 * // "a" takes 25% (1/4), "b" takes 75% (3/4) of one cycle
 *
 * @example
 * stepcat([2, note("c")], [1, note("e g")])
 * // "c" takes 66% (2/3), "e g" takes 33% (1/3) of one cycle
 */
@StrudelDsl
val stepcat by dslFunction { args, /* callInfo */ _ -> applyStepcat(args) }

@StrudelDsl
val StrudelPattern.stepcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(StrudelDslArg.of(p)) + args)
}

@StrudelDsl
val String.stepcat by dslStringExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val timeCat by dslFunction { args, callInfo -> stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val StrudelPattern.timeCat by dslPatternExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val String.timeCat by dslStringExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val timecat by dslFunction { args, callInfo -> stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val StrudelPattern.timecat by dslPatternExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val String.timecat by dslStringExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val s_cat by dslFunction { args, callInfo -> stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val StrudelPattern.s_cat by dslPatternExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

/** Alias for [stepcat] */
@StrudelDsl
val String.s_cat by dslStringExtension { p, args, callInfo -> p.stepcat(args, callInfo) }

// -- stackBy() --------------------------------------------------------------------------------------------------------

fun applyStackBy(patterns: List<StrudelPattern>, alignment: Double): StrudelPattern {
    if (patterns.isEmpty()) return silence

    // Get duration for each pattern
    val durations = patterns.map { it.estimateCycleDuration() }
    val maxDur = durations.maxOrNull() ?: Rational.ONE

    val alignmentRat = alignment.toRational()

    // Align patterns by padding them with gaps to match maxDur
    val alignedPatterns = patterns.zip(durations).map { (pat, dur) ->
        if (dur == maxDur) {
            pat
        } else {
            val diff = maxDur - dur
            val leftGap = diff * alignmentRat
            val rightGap = diff - leftGap

            val segments = mutableListOf<StrudelPattern>()

            // Use EmptyPattern instead of GapPattern for padding.
            // EmptyPattern occupies time (via weight) but produces NO events.
            // GapPattern produces "silent events" which pollute the event count.

            if (leftGap > Rational.ZERO) {
                segments.add(EmptyPattern.withWeight(leftGap.toDouble()))
            }

            segments.add(pat.withWeight(dur.toDouble()))

            if (rightGap > Rational.ZERO) {
                segments.add(EmptyPattern.withWeight(rightGap.toDouble()))
            }

            // SequencePattern fits total weight into 1 cycle.
            // We slow it down by maxDur to restore original speeds and placement within the larger cycle.
            SequencePattern(segments).slow(maxDur.toDouble())
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
    val patterns = args.drop(1).toListOfPatterns(voiceValueModifier)

    applyStackBy(patterns = patterns, alignment = alignment)
}

// -- stackLeft() ------------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the left (start). */
@StrudelDsl
val stackLeft by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(voiceValueModifier), alignment = 0.0)
}

// -- stackRight() -----------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the right (end). */
@StrudelDsl
val stackRight by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(voiceValueModifier), alignment = 1.0)
}

// -- stackCentre() ----------------------------------------------------------------------------------------------------

/** Stack patterns aligned to the center. */
@StrudelDsl
val stackCentre by dslFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(voiceValueModifier), alignment = 0.5)
}

// -- polyrhythm() -----------------------------------------------------------------------------------------------------

/**
 * Alias for stack. Creates polyrhythms by playing patterns simultaneously.
 */
@StrudelDsl
val polyrhythm by dslFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.polyrhythm by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val String.polyrhythm by dslStringExtension { p, args, callInfo -> p.polyrhythm(args, callInfo) }

// -- sequenceP() ------------------------------------------------------------------------------------------------------

/**
 * Pattern of patterns sequence. Alias for seq.
 */
@StrudelDsl
val sequenceP by dslFunction { args, /* callInfo */ _ ->
    applySeq(args.toListOfPatterns(voiceValueModifier))
}

// -- cat() ------------------------------------------------------------------------------------------------------------

fun applyCat(patterns: List<StrudelPattern>): StrudelPattern {
    // cat is an alias for slowcat (infinite cycling, relative time)
    return applySlowcat(patterns)
}

@StrudelDsl
val cat by dslFunction { args, /* callInfo */ _ ->
    applyCat(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
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
    applySeq(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.fastcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
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
    applyCat(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.slowcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val String.slowcat by dslStringExtension { p, args, callInfo -> p.slowcat(args, callInfo) }

// -- slowcatPrime() ---------------------------------------------------------------------------------------------------

/**
 * Cycles through a list of patterns infinitely, playing one pattern per cycle.
 * Preserves absolute time (does not reset pattern time to 0 for each cycle).
 *
 * This corresponds to 'slowcatPrime' in JS.
 */
fun applySlowcatPrime(patterns: List<StrudelPattern>): StrudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : StrudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Rational? = null

        override fun estimateCycleDuration(): Rational = Rational.ONE * patterns.size

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

            val result = mutableListOf<StrudelPatternEvent>()
            val n = patterns.size
            var cycle = from.floor()

            while (cycle < to) {
                val cycleEnd = cycle + Rational.ONE
                val queryStart = maxOf(from, cycle)
                val queryEnd = minOf(to, cycleEnd)

                // Select pattern using modulo (cycles infinitely through patterns)
                val patternIndex = cycle.toInt().mod(n)
                val pattern = patterns[patternIndex]

                // Crucial: We query at absolute time (queryStart), not relative time.
                // This is what makes it "Prime".
                if (queryEnd > queryStart) {
                    result.addAll(pattern.queryArcContextual(queryStart, queryEnd, ctx))
                }

                cycle = cycleEnd
            }

            return result
        }
    }
}

/**
 * Like slowcat but maintains relative timing.
 */
@StrudelDsl
val slowcatPrime by dslFunction { args, /* callInfo */ _ ->
    applySlowcatPrime(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.slowcatPrime by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySlowcatPrime(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val String.slowcatPrime by dslStringExtension { p, args, callInfo -> p.slowcatPrime(args, callInfo) }

// -- polymeter() ------------------------------------------------------------------------------------------------------

fun applyPolymeter(patterns: List<StrudelPattern>, baseSteps: Int? = null): StrudelPattern {
    if (patterns.isEmpty()) return silence

    // Filter for patterns that have steps defined
    val validPatterns = patterns.filter { it.numSteps != null }
    if (validPatterns.isEmpty()) return silence

    val patternSteps = validPatterns.mapNotNull { it.numSteps?.toInt() }
    val targetSteps = baseSteps ?: lcm(patternSteps).takeIf { it > 0 } ?: 4

    val adjustedPatterns = validPatterns.map { pat ->
        val steps = pat.numSteps!!.toInt()
        if (steps == targetSteps) pat
        else pat.fast(targetSteps.toDouble() / steps)
    }

    return PropertyOverridePattern(
        source = StackPattern(adjustedPatterns),
        stepsOverride = targetSteps.toRational()
    )
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
    applyPolymeter(patterns = args.toListOfPatterns(voiceValueModifier))
}

@StrudelDsl
val StrudelPattern.polymeter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPolymeter(patterns = listOf(p) + args.toListOfPatterns(voiceValueModifier))
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
    val patterns = args.drop(1).toListOfPatterns(voiceValueModifier)

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
    AtomicPattern(StrudelVoiceData.empty.copy(value = value?.asVoiceValue()))
}

// -- struct() ---------------------------------------------------------------------------------------------------------

fun applyStruct(source: StrudelPattern, structArg: StrudelDslArg<Any?>?): StrudelPattern {
    val structure = structArg?.toPattern() ?: return silence

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

    val pattern = listOf(args[1]).toPattern(voiceValueModifier)

    applyStruct(source = pattern, structArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.struct by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStruct(source = p, structArg = args.firstOrNull())
}

@StrudelDsl
val String.struct by dslStringExtension { p, args, callInfo -> p.struct(args, callInfo) }

// -- structAll() ------------------------------------------------------------------------------------------------------

fun applyStructAll(source: StrudelPattern, structArg: StrudelDslArg<Any?>?): StrudelPattern {
    val structure = structArg?.toPattern() ?: return silence

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

    val pattern = listOf(args[1]).toPattern(voiceValueModifier)

    applyStructAll(source = pattern, structArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.structAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStructAll(source = p, structArg = args.firstOrNull())
}

@StrudelDsl
val String.structAll by dslStringExtension { p, args, callInfo -> p.structAll(args, callInfo) }

// -- mask() -----------------------------------------------------------------------------------------------------------

fun applyMask(source: StrudelPattern, maskArg: StrudelDslArg<Any?>?): StrudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

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

    val pattern = listOf(args[1]).toPattern(voiceValueModifier)

    applyMask(source = pattern, maskArg = args.getOrNull(0))
}

@StrudelDsl
val StrudelPattern.mask by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMask(source = p, maskArg = args.firstOrNull())
}

@StrudelDsl
val String.mask by dslStringExtension { p, args, callInfo -> p.mask(args, callInfo) }

// -- maskAll() --------------------------------------------------------------------------------------------------------

fun applyMaskAll(source: StrudelPattern, maskArg: StrudelDslArg<Any?>?): StrudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

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

// -- jux() ------------------------------------------------------------------------------------------------------------

fun applyJux(source: StrudelPattern, transform: StrudelPatternMapper): StrudelPattern {
    // Pan is unipolar (0.0 to 1.0).
    // jux pans original hard left (0.0) and transformed hard right (1.0).
    val left = source.pan(0.0)
    val right = transform(source).pan(1.0)
    return StackPattern(listOf(left, right))
}

/**
 * Applies the function to the pattern and pans the result to the right,
 * while panning the original pattern to the left.
 *
 * @param {transform} Function to apply to the right channel
 */
@StrudelDsl
val StrudelPattern.jux by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transform = args.firstOrNull().toPatternMapper() ?: { it }
    applyJux(p, transform)
}

@StrudelDsl
val String.jux by dslStringExtension { p, args, callInfo -> p.jux(args, callInfo) }

// -- juxBy() ----------------------------------------------------------------------------------------------------------

fun applyJuxBy(source: StrudelPattern, amount: Double, transform: StrudelPatternMapper): StrudelPattern {
    // Unipolar pan: 0.0 is left, 1.0 is right, 0.5 is center.
    // amount=1.0 -> 0.0 (L) & 1.0 (R) - full stereo
    // amount=0.5 -> 0.25 (L) & 0.75 (R) - half stereo width
    // amount=0.0 -> 0.5 (L) & 0.5 (R) - mono/center
    val panLeft = 0.5 * (1.0 - amount)
    val panRight = 0.5 * (1.0 + amount)

    val left = source.pan(panLeft)
    val right = transform(source).pan(panRight)

    return StackPattern(listOf(left, right))
}

/**
 * Like jux, but with adjustable stereo width.
 *
 * @param {amount} Stereo width (0 to 1). 1 = full stereo, 0 = mono (center).
 * @param {transform} Function to apply to the right channel logic
 */
@StrudelDsl
val StrudelPattern.juxBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val amount = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 1.0
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyJuxBy(p, amount, transform)
}

@StrudelDsl
val String.juxBy by dslStringExtension { p, args, callInfo -> p.juxBy(args, callInfo) }

// -- off() ------------------------------------------------------------------------------------------------------------

/**
 * Layers a modified version of the pattern on top of itself, shifted in time.
 *
 * @param {time} Time offset in cycles
 * @param {transform} Function to apply to the delayed layer
 */
@StrudelDsl
val StrudelPattern.off by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val time = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.25

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    p.stack(transform(p).late(time))
}

@StrudelDsl
val String.off by dslStringExtension { p, args, callInfo -> p.off(args, callInfo) }

// -- filter() ---------------------------------------------------------------------------------------------------------

private fun applyFilter(source: StrudelPattern, predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern {
    return source.map { events -> events.filter(predicate) }
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

    if (predicate != null) applyFilter(pat) { predicate(it.part.begin.toDouble()) } else pat
}

@Suppress("unused")
@StrudelDsl
fun filterWhen(predicate: (Double) -> Boolean): StrudelPattern = silence

@StrudelDsl
val StrudelPattern.filterWhen by dslPatternExtension { source, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.firstOrNull()?.value as? (Double) -> Boolean

    if (predicate != null) applyFilter(source = source) { predicate(it.part.begin.toDouble()) } else source
}

@StrudelDsl
fun StrudelPattern.filterWhen(predicate: (Double) -> Boolean): StrudelPattern =
    applyFilter(source = this) { predicate(it.part.begin.toDouble()) }

@StrudelDsl
val String.filterWhen by dslStringExtension { source, args, callInfo -> source.filterWhen(args, callInfo) }

@StrudelDsl
fun String.filterWhen(predicate: (Double) -> Boolean): StrudelPattern = filterWhen(predicate as Any)

// -- bypass() ---------------------------------------------------------------------------------------------------------

private fun applyBypass(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source

    val condition = args.toPattern(voiceValueModifier)
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
    val transform = args.firstOrNull().toPatternMapper() ?: { it }
    p.stack(transform(p))
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
    val transforms: List<StrudelPatternMapper> = args.mapNotNull { it.toPatternMapper() }

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
    if (args.size < 2) {
        return source
    }

    // We convert both arguments to patterns to support dynamic zoom (e.g. zoom("<0 0.5>", "<0.5 1>"))
    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    // Bind the start pattern...
    return startCtrl._bind { startEv ->
        val sVal = startEv.data.value?.asDouble ?: return@_bind null

        // ... then bind the end pattern
        endCtrl._bind { endEv ->
            val eVal = endEv.data.value?.asDouble ?: return@_bind null

            val s = sVal.toRational()
            val e = eVal.toRational()

            if (s >= e) return@_bind null

            val d = e - s
            val steps = source.numSteps?.let { it * d }

            source._withQueryTime { t -> t * d + s }.mapEvents { ev ->
                val scaledPart = ev.part.shift(-s).scale(Rational.ONE / d)
                val scaledWhole = ev.whole.shift(-s).scale(Rational.ONE / d)
                ev.copy(part = scaledPart, whole = scaledWhole)
            }.let { if (steps != null) it.withSteps(steps) else it }
        }
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

val StrudelPattern.within by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns for start and end
    val start = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val end = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0
    val transform = args.getOrNull(2).toPatternMapper() ?: { it }

    if (start >= end || start < 0.0 || end > 1.0) {
        p // Return unchanged if invalid window
    } else {
        val startRat = start.toRational()
        val endRat = end.toRational()

        val isBeginInWindow: (StrudelPatternEvent) -> Boolean = { ev ->
            val cycle = ev.part.begin.floor()
            if (start < end) {
                val s = cycle + startRat
                val e = cycle + endRat
                ev.part.begin >= s && ev.part.begin < e
            } else {
                val s1 = cycle + startRat
                val e1 = cycle + Rational.ONE
                val s2 = cycle
                val e2 = cycle + endRat
                (ev.part.begin >= s1 && ev.part.begin < e1) || (ev.part.begin >= s2 && ev.part.begin < e2)
            }
        }

        val inside = p.filter(isBeginInWindow)
        val outside = p.filter { !isBeginInWindow(it) }

        StackPattern(listOf(transform(inside), outside))
    }
}

/** Alias for within - supports function call syntax */
@StrudelDsl
fun StrudelPattern.within(start: Double, end: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.within(listOf(start, end, transform).asStrudelDslArgs())
}

@StrudelDsl
val String.within by dslStringExtension { p, args, callInfo -> p.within(args, callInfo) }

/** Alias for within - supports function call syntax */
@StrudelDsl
fun String.within(start: Double, end: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.within(listOf(start, end, transform).asStrudelDslArgs())
}

// -- chunk() ----------------------------------------------------------------------------------------------------------

private fun applyChunk(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
    val n = nArg.value?.asIntOrNull() ?: 1
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // TODO: support control patterns
    val back = args.getOrNull(2)?.value as? Boolean ?: false
    val fast = args.getOrNull(3)?.value as? Boolean ?: false

    if (n <= 0) {
        return silence
    }

    val binary = MutableList(n - 1) { false }
    binary.add(0, true)  // [true, false, false, false] for n=4

    // Construct binary patterns manually to avoid dependency on 'pure' DSL property order
    val binaryPatterns = binary.map {
        AtomicPattern(StrudelVoiceData.empty.copy(value = it.asVoiceValue()))
    }
    val binarySequence = applySeq(binaryPatterns)

    val binaryIter = if (back) {
        applyIter(binarySequence, listOf(nArg))  // forward (default)
    } else {
        applyIterBack(binarySequence, listOf(nArg))  // backward
    }

    val pattern = if (!fast) {
        source.repeatCycles(n)
    } else {
        source
    }

    // return pat.when(binary_pat, func);
    return pattern.`when`(binaryIter, transform)
}

/**
 * Divides a pattern into a given number of parts, then cycles through those parts in turn,
 * applying the given function to each part in turn (one part per cycle).
 *
 * Ported from JavaScript _chunk() implementation in pattern.mjs.
 *
 * @param {n}         Number of chunks to divide the pattern into
 * @param {transform} Function to apply to each chunk
 * @param {back}      If true, cycles backward through chunks (default: false)
 * @param {fast}      If true, doesn't repeat the pattern (default: false)
 *
 * @example
 * seq("0 1 2 3").chunk(4) { it.add(7) }
 * // Cycle 0: "7 1 2 3"  (chunk 0 transformed)
 * // Cycle 1: "0 8 2 3"  (chunk 1 transformed)
 * // Cycle 2: "0 1 9 3"  (chunk 2 transformed)
 * // Cycle 3: "0 1 2 10" (chunk 3 transformed)
 */
@StrudelDsl
val StrudelPattern.chunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

/** Alias for chunk - supports function call syntax */
@StrudelDsl
fun StrudelPattern.chunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())
}

@StrudelDsl
val String.chunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

/** Alias for chunk - supports function call syntax */
@StrudelDsl
fun String.chunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return this.chunk(listOf(n, transform, back, fast).asStrudelDslArgs())
}

/** Alias for [chunk] */
@StrudelDsl
val StrudelPattern.slowchunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

/** Alias for [chunk] */
@StrudelDsl
fun StrudelPattern.slowchunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())
}

/** Alias for [chunk] */
@StrudelDsl
val String.slowchunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

/** Alias for [chunk] */
@StrudelDsl
fun String.slowchunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return this.slowchunk(listOf(n, transform, back, fast).asStrudelDslArgs())
}

/** Alias for [chunk] */
@StrudelDsl
val StrudelPattern.slowChunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

/** Alias for [chunk] */
@StrudelDsl
fun StrudelPattern.slowChunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())
}

/** Alias for [chunk] */
@StrudelDsl
val String.slowChunk by dslStringExtension { p, args, /*callInfo */ _ -> applyChunk(p, args) }

/** Alias for [chunk] */
@StrudelDsl
fun String.slowChunk(
    n: Int,
    transform: StrudelPatternMapper,
    back: Boolean = false,
    fast: Boolean = false,
): StrudelPattern {
    return this.slowChunk(listOf(n, transform, back, fast).asStrudelDslArgs())
}

// -- echo() / stut() --------------------------------------------------------------------------------------------------

/**
 * Superimposes delayed and decayed versions of the pattern (echo effect).
 *
 * Creates {times} copies of the pattern, each delayed by {delay} × copy_number and
 * with gain reduced by [decay] ^ copy_number.
 *
 * @param {times} Number of echoes (including original)
 * @param {delay} Time offset for each echo (in cycles)
 * @param {decay} Gain multiplier for each echo (0.0 to 1.0)
 *
 * @example
 * n("0").stut(4, 0.5, 0.5)
 * // Original at 0.0, echoes at 0.5, 1.0, 1.5 with gain 1.0, 0.5, 0.25, 0.125
 *
 * @example
 * s("bd").echo(3, 0.125, 0.7)
 * // Original + 2 echoes, 0.125 cycles apart, each 70% quieter than previous
 */
@StrudelDsl
val StrudelPattern.echo by dslPatternExtension { p, args, /* callInfo */ _ ->
    val times = args.getOrNull(0)?.value?.asIntOrNull() ?: 1
    val delay = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 0.25
    val decay = args.getOrNull(2)?.value?.asDoubleOrNull() ?: 0.5

    if (times <= 0) {
        silence
    } else if (times == 1) {
        p // Only original, no echoes
    } else {
        // Create layers: original + echoes
        val layers = (0 until times).map { i ->
            if (i == 0) {
                p // Original (no delay, no gain change)
            } else {
                // Delayed and decayed echo
                val gainMultiplier = decay.pow(i)
                p.late(delay * i).gain(gainMultiplier)
            }
        }

        StackPattern(layers)
    }
}

/** Alias for echo - supports function call syntax */
@StrudelDsl
fun StrudelPattern.echo(times: Int, delay: Double, decay: Double): StrudelPattern {
    return this.echo(listOf(times, delay, decay).asStrudelDslArgs())
}

@StrudelDsl
val String.echo by dslStringExtension { p, args, callInfo -> p.echo(args, callInfo) }

/** Alias for echo - supports function call syntax */
@StrudelDsl
fun String.echo(times: Int, delay: Double, decay: Double): StrudelPattern {
    return this.echo(listOf(times, delay, decay).asStrudelDslArgs())
}

/** Alias for [echo] */
@StrudelDsl
val StrudelPattern.stut by dslPatternExtension { p, args, callInfo -> p.echo(args, callInfo) }

/** Alias for [echo] */
@StrudelDsl
fun StrudelPattern.stut(times: Int, delay: Double, decay: Double): StrudelPattern {
    return this.echo(times, delay, decay)
}

/** Alias for [echo] */
@StrudelDsl
val String.stut by dslStringExtension { p, args, callInfo -> p.echo(args, callInfo) }

/** Alias for [echo] */
@StrudelDsl
fun String.stut(times: Int, delay: Double, decay: Double): StrudelPattern {
    return this.echo(times, delay, decay)
}

// -- echoWith() / stutWith() ------------------------------------------------------------------------------------------

/**
 * Superimposes versions of the pattern modified by recursively applying a transform function.
 *
 * Unlike [echo], which simply decays gain, this allows arbitrary transformations to be applied
 * cumulatively to each layer.
 *
 * @param {times}     Number of layers (including original)
 * @param {delay}     Time offset for each layer (in cycles)
 * @param {transform} Function applied cumulatively to each layer
 *
 * @example
 * n("0").stutWith(4, 0.125) { it.add(2) }
 * // Layer 0: 0           (at 0.0)
 * // Layer 1: 2           (at 0.125) - add(2) applied once
 * // Layer 2: 4           (at 0.25)  - add(2) applied twice
 * // Layer 3: 6           (at 0.375) - add(2) applied three times
 *
 * @example
 * s("bd").echoWith(3, 0.25) { it.fast(1.5) }
 * // Layer 0: original    (at 0.0)
 * // Layer 1: 1.5× faster (at 0.25)
 * // Layer 2: 2.25× faster (at 0.5) - fast(1.5) applied twice
 */
@StrudelDsl
val StrudelPattern.echoWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    val times = args.getOrNull(0)?.value?.asIntOrNull() ?: 1
    val delay = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 0.25
    val transform = args.getOrNull(2).toPatternMapper() ?: { it }

    if (times <= 0) {
        silence
    } else if (times == 1) {
        p // Only original, no additional layers
    } else {
        // Build layers with cumulative transformation
        val layers = mutableListOf(p) // Layer 0: original
        var current = p

        repeat(times - 1) { i ->
            // Apply transform cumulatively
            current = transform(current)
            // Delay this layer
            layers.add(current.late(delay * (i + 1)))
        }

        StackPattern(layers)
    }
}

/** Alias for echoWith - supports function call syntax */
@StrudelDsl
fun StrudelPattern.echoWith(times: Int, delay: Double, transform: (StrudelPattern) -> StrudelPattern): StrudelPattern {
    return this.echoWith(listOf(times, delay, transform).asStrudelDslArgs())
}

@StrudelDsl
val String.echoWith by dslStringExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for echoWith - supports function call syntax */
@StrudelDsl
fun String.echoWith(times: Int, delay: Double, transform: (StrudelPattern) -> StrudelPattern): StrudelPattern {
    return this.echoWith(listOf(times, delay, transform).asStrudelDslArgs())
}

/** Alias for [echoWith] */
@StrudelDsl
val StrudelPattern.stutWith by dslPatternExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun StrudelPattern.stutWith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

/** Alias for [echoWith] */
@StrudelDsl
val String.stutWith by dslStringExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun String.stutWith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

/** Alias for [echoWith] */
@StrudelDsl
val StrudelPattern.stutwith by dslPatternExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun StrudelPattern.stutwith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

/** Alias for [echoWith] */
@StrudelDsl
val String.stutwith by dslStringExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun String.stutwith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

/** Alias for [echoWith] */
@StrudelDsl
val StrudelPattern.echowith by dslPatternExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun StrudelPattern.echowith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

/** Alias for [echoWith] */
@StrudelDsl
val String.echowith by dslStringExtension { p, args, callInfo -> p.echoWith(args, callInfo) }

/** Alias for [echoWith] */
@StrudelDsl
fun String.echowith(times: Int, delay: Double, transform: StrudelPatternMapper): StrudelPattern {
    return this.echoWith(times, delay, transform)
}

// -- bite() -----------------------------------------------------------------------------------------------------------

fun applyBite(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) return silence

    val nPattern = args.take(1).toPattern(voiceValueModifier)
    val indicesPattern = args.drop(1).toPattern(voiceValueModifier)

    return source._innerJoin(nPattern, indicesPattern) { src, nValue, indexValue ->
        val n = nValue?.asDouble ?: 4.0
        val index = indexValue?.asDouble ?: return@_innerJoin silence

        if (n <= 0.0) return@_innerJoin silence

        val start = index / n
        val end = (index + 1.0) / n

        src.zoom(start, end)
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

fun applySegment(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()

    val nPattern: StrudelPattern = when (val nVal = nArg?.value) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use original implementation with struct + fast
        val structPat = parseMiniNotation("x") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }

        source.struct(structPat.fast(staticN))
    } else {
        // Dynamic path: use SegmentPattern which properly slices each timespan
        SegmentPattern.control(source, nPattern)
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

fun applyEuclid(source: StrudelPattern, pulses: Int, steps: Int, rotation: Int): StrudelPattern {
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
    val pattern = args.drop(2).toPattern(voiceValueModifier)

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
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = 0)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = false)
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
    val pattern = args.drop(3).toPattern(voiceValueModifier)

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
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern, legato = false)
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
    val pattern = args.drop(1).toPattern(voiceValueModifier)

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
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPattern.control(
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
    val pattern = args.drop(2).toPattern(voiceValueModifier)

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
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    if (staticPulses != null && staticSteps != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = 0,
        )
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = true)
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
    val pattern = args.drop(3).toPattern(voiceValueModifier)

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
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
        EuclideanPattern.control(
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

fun applyEuclidish(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val grooveArg = args.getOrNull(2)
    val grooveVal = grooveArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveVal) {
        is StrudelPattern -> grooveVal
        else -> {
            parseMiniNotation(grooveArg ?: StrudelDslArg.of("0")) { text, _ ->
                AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
            val structPattern = EuclideanMorphPattern.static(
                pulses = staticPulses, steps = staticSteps, groovePattern = groovePattern
            )

            source.struct(structPattern)
        }
    } else {
        val structPattern = EuclideanMorphPattern.control(
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
    val pattern = args.drop(3).toPattern(voiceValueModifier)
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

fun applyRun(n: Int): StrudelPattern {
    // TODO: support control pattern

    if (n <= 0) return silence
    // "0 1 2 ... n-1"
    // equivalent to saw.range(0, n).round().segment(n) in JS
    // But we can just create a sequence directly.
    val items = (0 until n).map {
        AtomicPattern(StrudelVoiceData.empty.copy(value = it.asVoiceValue()))
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

fun applyBinaryN(n: Int, bits: Int): StrudelPattern {
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
        AtomicPattern(StrudelVoiceData.empty.copy(value = bit.asVoiceValue()))
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

fun applyBinaryNL(n: Int, bits: Int): StrudelPattern {
    if (bits <= 0) return silence

    val bitList = (0 until bits).mapNotNull { i ->
        // Shift: bits - 1 - i (MSB first)
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        bit.asVoiceValue()
    }

    // Returns a single event containing the list of bits as a Seq value
    return AtomicPattern(
        StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Seq(bitList))
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

// -- ratio ------------------------------------------------------------------------------------------------------------

private val ratioMutation = voiceModifier { inputValue ->
    // Parse colon notation like "5:4" into a ratio
    // Convert to string first to handle both string and numeric inputs
    val parts = inputValue?.toString()?.split(":") ?: emptyList()

    val ratioValue = if (parts.size > 1) {
        // Parse all parts as numbers and divide them: "5:4" -> 5/4 = 1.25
        val numbers = parts.mapNotNull { it.toDoubleOrNull() }
        if (numbers.isNotEmpty()) {
            numbers.drop(1).fold(numbers[0]) { acc, divisor -> acc / divisor }
        } else {
            null
        }
    } else {
        // Single value without colon, try to parse as number
        inputValue?.asDoubleOrNull()
    }

    copy(value = ratioValue?.asVoiceValue())
}

/**
 * Divides numbers via colon notation using ":".
 * E.g. "5:4" becomes 1.25, "3:2" becomes 1.5, "12:3:2" becomes 2.0.
 * Returns a new pattern with just numbers.
 */
@StrudelDsl
val ratio by dslFunction { args, /* callInfo */ _ -> args.toPattern(ratioMutation) }

/**
 * Divides numbers via colon notation using ":".
 */
@StrudelDsl
val StrudelPattern.ratio by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    p.reinterpretVoice { it.ratioMutation(it.value?.asString) }
}

@StrudelDsl
val String.ratio by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.ratio() }

// -- pace() / steps() -------------------------------------------------------------------------------------------------

fun applyPace(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val currentSteps = source.numSteps?.toDouble() ?: 1.0

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
    val pattern = args.drop(1).toPattern(voiceValueModifier)
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

// -- take() -----------------------------------------------------------------------------------------------------------

fun applyTake(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val takeArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = takeArg.asControlValueProvider(StrudelVoiceValue.Num(1.0))

    val takePattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(StrudelVoiceData.empty.copy(value = control.value))
        is ControlValueProvider.Pattern -> control.pattern
    }

    return takePattern._stepJoin { event ->
        val n = event.data.value?.asDouble ?: return@_stepJoin null
        val steps = source.numSteps?.toDouble()

        if (steps != null && steps > 0) {
            val ratN = n.toRational()
            val ratSteps = steps.toRational()
            val end = ratN / ratSteps

            if (end <= Rational.ZERO) return@_stepJoin silence
            if (end >= Rational.ONE) return@_stepJoin source
            // Take(n) keeps first n steps.
            // Zoom window [0, end] to [0, 1]
            source
                ._withQueryTime { t -> t * end }
                ._withHapTime { t -> t / end }
                .withSteps(ratN)
        } else {
            silence
        }
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
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyTake(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.take by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTake(p, args)
}

@StrudelDsl
val String.take by dslStringExtension { p, args, callInfo -> p.take(args, callInfo) }

// -- drop() -----------------------------------------------------------------------------------------------------------

fun applyDrop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val dropArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = dropArg.asControlValueProvider(StrudelVoiceValue.Num(0.0))

    val dropPattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(StrudelVoiceData.empty.copy(value = control.value))
        is ControlValueProvider.Pattern -> control.pattern
    }

    return dropPattern._stepJoin { event ->
        val n = event.data.value?.asDouble ?: return@_stepJoin null
        val steps = source.numSteps?.toDouble()

        if (steps != null && steps > 0) {
            val ratN = n.toRational()
            val ratSteps = steps.toRational()

            if (ratN > Rational.ZERO) {
                // drop from start: zoom(n/steps, 1)
                val start = ratN / ratSteps
                if (start >= Rational.ONE) return@_stepJoin silence
                // Zoom window [start, 1] to [0, 1]
                // Map query t in [0, 1] to [start, 1] -> t' = start + t * (1 - start)
                val duration = Rational.ONE - start

                source
                    ._withQueryTime { t -> start + t * duration }
                    ._withHapTime { t -> (t - start) / duration }
                    .withSteps(ratSteps - ratN)
            } else {
                // drop from end: zoom(0, (steps+n)/steps)
                // n is negative
                val end = (ratSteps + ratN) / ratSteps
                if (end <= Rational.ZERO) return@_stepJoin silence
                // Zoom window [0, end] to [0, 1]
                // Map query t in [0, 1] to [0, end] -> t' = t * end
                source
                    ._withQueryTime { t -> t * end }
                    ._withHapTime { t -> t / end }
                    .withSteps(ratSteps + ratN)
            }
        } else {
            silence
        }
    }
}

/**
 * Skips the first n steps of the pattern and stretches the remaining steps to fill the cycle.
 *
 * Example: note("c d e f").drop(1) plays "d e f" (3 events stretched to fill the cycle)
 */
@StrudelDsl
val drop by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyDrop(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.drop by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDrop(p, args)
}

@StrudelDsl
val String.drop by dslStringExtension { p, args, callInfo -> p.drop(args, callInfo) }

// -- repeatCycles() ---------------------------------------------------------------------------------------------------

fun applyRepeatCycles(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val repsArg = args.firstOrNull()
    val repsVal = repsArg?.value

    val repsPattern: StrudelPattern = when (repsVal) {
        is StrudelPattern -> repsVal
        else -> parseMiniNotation(repsArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
 * Repeats each cycle the given number of times.
 *
 * Example: note("c d e f").repeatCycles(3) repeats each cycle 3 times
 * (cycle 0 plays 3 times, then cycle 1 plays 3 times, etc.)
 */
@StrudelDsl
val repeatCycles by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyRepeatCycles(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.repeatCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRepeatCycles(p, args)
}

@StrudelDsl
val String.repeatCycles by dslStringExtension { p, args, callInfo -> p.repeatCycles(args, callInfo) }

// -- extend() ---------------------------------------------------------------------------------------------------------

/**
 * Speeds up the pattern by the given factor.
 * This is an alias for fast().
 *
 * Example: note("c d e f").extend(2) plays twice as fast (8 steps in a cycle)
 */
@StrudelDsl
val extend by dslFunction { args, callInfo -> fast(args, callInfo) }

@StrudelDsl
val StrudelPattern.extend by dslPatternExtension { p, args, callInfo -> p.fast(args, callInfo) }

@StrudelDsl
val String.extend by dslStringExtension { p, args, callInfo -> p.fast(args, callInfo) }

// -- slowcat() --------------------------------------------------------------------------------------------------------

/**
 * Cycles through a list of patterns infinitely, playing one pattern per cycle.
 * This mimics the JavaScript `slowcat()` behavior.
 *
 * Relative Time: Each pattern starts at 0.0 when it is its turn.
 */
internal fun applySlowcat(patterns: List<StrudelPattern>): StrudelPattern {
    if (patterns.isEmpty()) return silence
    // We use SequencePattern (which squeezes) and slow() to achieve this.
    // [A, B] -> Sequence(A, B) fits in 1 cycle -> slow(2) stretches to 2 cycles.
    // Effectively A plays 0..1 (local time 0..1), B plays 1..2 (local time 0..1).
    return SequencePattern(patterns.map { it.withWeight(1.0) }).slow(patterns.size.toDouble())
}

// -- iter() -----------------------------------------------------------------------------------------------------------

fun applyIter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    // iter only supports static integer n because it defines the number of cycles in the sequence
    val n = nArg?.value?.asIntOrNull() ?: 1

    if (n <= 0) return silence

    val nRat = n.toRational()

    // Equivalent to JS: listRange(0, times.sub(1)).map((i) => pat.early(Fraction(i).div(times)))
    val patterns = (0 until n).map { i ->
        val shift = i.toRational() / nRat
        // We use early() to shift the view forward (events appear earlier, effectively rotating the pattern left)
        source.early(shift)
    }

    // JS uses slowcat (standard) here, but since iter slices are time-shifted manually above,
    // we can use slowcatPrime logic to sequence them without double-shifting.
    return applySlowcatPrime(patterns)
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
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyIter(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.iter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIter(p, args)
}

@StrudelDsl
val String.iter by dslStringExtension { p, args, callInfo -> p.iter(args, callInfo) }

// -- iterBack() -------------------------------------------------------------------------------------------------------

fun applyIterBack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    val n = nArg?.value?.asIntOrNull() ?: 1

    if (n <= 0) return silence

    val nRat = n.toRational()

    // Equivalent to JS: listRange(0, times.sub(1)).map((i) => pat.late(Fraction(i).div(times)))
    val patterns = (0 until n).map { i ->
        val shift = i.toRational() / nRat
        // We use late() to shift the view backward (events appear later, rotating pattern right)
        source.late(shift)
    }

    return applySlowcatPrime(patterns)
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
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyIterBack(pattern, args.take(1))
}

@StrudelDsl
val StrudelPattern.iterBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIterBack(p, args)
}

@StrudelDsl
val String.iterBack by dslStringExtension { p, args, callInfo -> p.iterBack(args, callInfo) }

// -- invert() / inv() ------------------------------------------------------------------------------------------------

/**
 * Inverts boolean values in a pattern: true <-> false, 1 <-> 0.
 * Useful for inverting structural patterns and masks.
 *
 * JavaScript: `pat.fmap((x) => !x)`
 */
fun applyInvert(pattern: StrudelPattern): StrudelPattern {
    return pattern.mapEvents { event ->
        val currentBool = event.data.value?.asBoolean ?: false
        val invertedBool = !currentBool
        event.copy(data = event.data.copy(value = StrudelVoiceValue.Bool(invertedBool)))
    }
}

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val invert by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern(voiceValueModifier)
    applyInvert(pattern)
}

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val inv by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern(voiceValueModifier)
    applyInvert(pattern)
}

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val StrudelPattern.invert by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyInvert(p)
}

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val StrudelPattern.inv by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyInvert(p)
}

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val String.invert by dslStringExtension { p, args, callInfo -> p.invert(args, callInfo) }

/** Inverts boolean values: true <-> false, 1 <-> 0 */
@StrudelDsl
val String.inv by dslStringExtension { p, args, callInfo -> p.inv(args, callInfo) }

// -- applyN() --------------------------------------------------------------------------------------------------------

/**
 * Applies a function to a pattern n times sequentially.
 * Supports control patterns for n.
 *
 * Example: `pattern.applyN(3, x => x.fast(2))` applies fast(2) three times
 *
 * JavaScript: `applyN(n, func, pat)`
 */
fun applyApplyN(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return pattern

    // Use _innerJoin to support control patterns for n
    return pattern._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 0

        var result = src
        repeat(n) { result = transform(result) }
        result
    }
}

/** Applies a function to a pattern n times */
@StrudelDsl
val applyN by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0) ?: return@dslFunction silence
    val func = args.getOrNull(1).toPatternMapper() ?: return@dslFunction silence
    val pattern = args.getOrNull(2)?.toPattern() ?: return@dslFunction silence

    applyApplyN(pattern, listOf(n, StrudelDslArg.of(func)))
}

/** Applies a function to a pattern n times */
@StrudelDsl
val StrudelPattern.applyN by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyApplyN(p, args)
}

/** Applies a function to a pattern n times */
@StrudelDsl
val String.applyN by dslStringExtension { p, args, callInfo -> p.applyN(args, callInfo) }
