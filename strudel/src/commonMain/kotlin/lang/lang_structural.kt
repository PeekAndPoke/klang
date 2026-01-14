@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.*

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
val hush by dslFunction { _ ->
    silence
}

@StrudelDsl
val StrudelPattern.hush by dslPatternExtension { _, _ ->
    silence
}

@StrudelDsl
val String.hush by dslStringExtension { _, _ ->
    silence
}

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates silence with a specific duration in steps (metrical steps). */
@StrudelDsl
val gap by dslFunction { args ->
    val steps = args.firstOrNull()?.asDoubleOrNull() ?: 1.0
    silence.slow(steps)
}

@StrudelDsl
val StrudelPattern.gap by dslPatternExtension { _, args ->
    val steps = args.firstOrNull()?.asDoubleOrNull() ?: 1.0
    silence.slow(steps)
}

@StrudelDsl
val String.gap by dslStringExtension { _, args ->
    val steps = args.firstOrNull()?.asDoubleOrNull() ?: 1.0
    silence.slow(steps)
}

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
private fun applySeq(patterns: List<StrudelPattern>): StrudelPattern {
    return if (patterns.isEmpty()) EmptyPattern
    else if (patterns.size == 1) patterns.first()
    else SequencePattern(patterns)
}

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction { args ->
    // specialized modifier for seq to prioritize value
    args.toPattern(defaultModifier)
}

@StrudelDsl
val StrudelPattern.seq by dslPatternExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applySeq(patterns)
}

@StrudelDsl
val String.seq by dslStringExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applySeq(patterns)
}

// -- mini() -----------------------------------------------------------------------------------------------------------

/** Parses input as mini-notation. Effectively an alias for `seq`. */
val mini by dslFunction { args ->
    args.toPattern(defaultModifier)
}

// -- stack() ----------------------------------------------------------------------------------------------------------

private fun applyStack(patterns: List<StrudelPattern>): StrudelPattern {
    return if (patterns.size == 1) patterns.first() else StackPattern(patterns)
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction { args ->
    val patterns = args.toListOfPatterns(defaultModifier)
    if (patterns.isEmpty()) silence else StackPattern(patterns)
}

@StrudelDsl
val StrudelPattern.stack by dslPatternExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applyStack(patterns)
}

@StrudelDsl
val String.stack by dslStringExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applyStack(patterns)
}

// -- arrange() --------------------------------------------------------------------------------------------------------

private fun applyArrange(args: List<Any?>): StrudelPattern {
    val segments = args.map { arg ->
        when (arg) {
            // Case: pattern (defaults to 1 cycle)
            is StrudelPattern -> 1.0 to arg
            // Case: [2, pattern]
            is List<*> if arg.size == 2 && arg[0] is Number && arg[1] is StrudelPattern -> {
                val dur = (arg[0] as Number).toDouble()
                val pat = arg[1] as StrudelPattern
                dur to pat
            }
            // Case: [pattern] (defaults to 1 cycle)
            is List<*> if arg.size == 1 && arg[0] is StrudelPattern -> 1.0 to (arg[0] as StrudelPattern)
            // Unknown
            else -> 0.0 to silence
        }
    }.filter { it.first > 0.0 }

    return if (segments.isEmpty()) silence
    else ArrangementPattern(segments)
}

// arrange([2, a], b) -> 2 cycles of a, 1 cycle of b.
@StrudelDsl
val arrange by dslFunction { args ->
    applyArrange(args)
}

@StrudelDsl
val StrudelPattern.arrange by dslPatternExtension { p, args ->
    applyArrange(listOf(p) + args)
}

@StrudelDsl
val String.arrange by dslStringExtension { p, args ->
    applyArrange(listOf(p) + args)
}

// -- pickRestart() ----------------------------------------------------------------------------------------------------

// pickRestart([a, b, c]) -> picks patterns sequentially per cycle (slowcat)
// TODO: make this really work. Must be dslMethod -> https://strudel.cc/learn/conditional-modifiers/#pickrestart
@StrudelDsl
val pickRestart by dslFunction { args ->
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

internal fun applyCat(patterns: List<StrudelPattern>): StrudelPattern = when {
    patterns.isEmpty() -> silence
    patterns.size == 1 -> patterns.first()
    else -> ArrangementPattern(patterns.map { 1.0 to it })
}

@StrudelDsl
val cat by dslFunction { args ->
    applyCat(args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.cat by dslPatternExtension { p, args ->
    applyCat(listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.cat by dslStringExtension { p, args ->
    applyCat(listOf(p) + args.toListOfPatterns(defaultModifier))
}

// -- struct() ---------------------------------------------------------------------------------------------------------

private fun applyStruct(source: StrudelPattern, structArg: Any?): StrudelPattern {
    val structure = when (structArg) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
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
val struct by dslFunction { args ->
    val structArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && structArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyStruct(source, structArg)
}

@StrudelDsl
val StrudelPattern.struct by dslPatternExtension { source, args ->
    applyStruct(source, args.firstOrNull())
}

@StrudelDsl
val String.struct by dslStringExtension { source, args ->
    applyStruct(source, args.firstOrNull())
}

// -- structAll() ------------------------------------------------------------------------------------------------------

private fun applyStructAll(source: StrudelPattern, structArg: Any?): StrudelPattern {
    val structure = when (structArg) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
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
val structAll by dslFunction { args ->
    val structArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && structArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyStructAll(source, structArg)
}

@StrudelDsl
val StrudelPattern.structAll by dslPatternExtension { source, args ->
    applyStructAll(source, args.firstOrNull())
}

@StrudelDsl
val String.structAll by dslStringExtension { source, args ->
    applyStructAll(source, args.firstOrNull())
}

private fun applyMask(source: StrudelPattern, maskArg: Any?): StrudelPattern {
    val maskPattern = when (maskArg) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
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
val mask by dslFunction { args ->
    val maskArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyMask(source, maskArg)
}

@StrudelDsl
val StrudelPattern.mask by dslPatternExtension { source, args ->
    applyMask(source, args.firstOrNull())
}

@StrudelDsl
val String.mask by dslStringExtension { source, args ->
    applyMask(source, args.firstOrNull())
}

// -- maskAll() --------------------------------------------------------------------------------------------------------

private fun applyMaskAll(source: StrudelPattern, maskArg: Any?): StrudelPattern {
    val maskPattern = when (maskArg) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
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
val maskAll by dslFunction { args ->
    val maskArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyMaskAll(source, maskArg)
}

@StrudelDsl
val StrudelPattern.maskAll by dslPatternExtension { source, args ->
    applyMaskAll(source, args.firstOrNull())
}

@StrudelDsl
val String.maskAll by dslStringExtension { source, args ->
    applyMaskAll(source, args.firstOrNull())
}


// -- filter() ---------------------------------------------------------------------------------------------------------

private fun applyFilter(source: StrudelPattern, predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern {
    return object : StrudelPattern {
        override val weight: Double get() = source.weight

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            return source.queryArcContextual(from, to, ctx).filter(predicate)
        }
    }
}

/** Filters haps using the given function. */
@StrudelDsl
val filter by dslFunction { _ -> silence }

@Suppress("unused")
@StrudelDsl
fun filter(ignored: (StrudelPatternEvent) -> Boolean): StrudelPattern = silence

/** Filters haps using the given function. */
@StrudelDsl
val StrudelPattern.filter by dslPatternExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val predicate = args.firstOrNull() as? (StrudelPatternEvent) -> Boolean

    if (predicate != null) applyFilter(source, predicate) else source
}

/** Filters haps using the given function. */
fun StrudelPattern.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern = applyFilter(this, predicate)

/** Filters haps using the given function. */
@StrudelDsl
val String.filter by dslStringExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val predicate = args.firstOrNull() as? (StrudelPatternEvent) -> Boolean

    if (predicate != null) applyFilter(source, predicate) else source
}

/** Filters haps using the given function. */
fun String.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern {
    val pattern = parseMiniNotation(this) {
        AtomicPattern(VoiceData.empty.defaultModifier(it))
    }

    return pattern.filter(predicate)
}

// -- filterWhen() -----------------------------------------------------------------------------------------------------

/**
 * Filters haps by their begin time.
 *
 * @param predicate function to test the begin time (Double)
 */
@StrudelDsl
val filterWhen by dslFunction { args ->
    @Suppress("UNCHECKED_CAST")
    val predicate = args.getOrNull(0) as? (Double) -> Boolean
    val pat = args.getOrNull(1) as? StrudelPattern ?: silence

    if (predicate != null) applyFilter(pat) { predicate(it.begin.toDouble()) } else pat
}

@Suppress("unused")
@StrudelDsl
fun filterWhen(predicate: (Double) -> Boolean): StrudelPattern = silence

@StrudelDsl
val StrudelPattern.filterWhen by dslPatternExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val predicate = args.firstOrNull() as? (Double) -> Boolean

    if (predicate != null) applyFilter(source) { predicate(it.begin.toDouble()) } else source
}

@StrudelDsl
fun StrudelPattern.filterWhen(predicate: (Double) -> Boolean): StrudelPattern =
    applyFilter(this) { predicate(it.begin.toDouble()) }

@StrudelDsl
val String.filterWhen by dslStringExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val predicate = args.firstOrNull() as? (Double) -> Boolean

    if (predicate != null) applyFilter(source) { predicate(it.begin.toDouble()) } else source
}

@StrudelDsl
fun String.filterWhen(predicate: (Double) -> Boolean): StrudelPattern {
    val pattern = parseMiniNotation(this) {
        AtomicPattern(VoiceData.empty.defaultModifier(it))
    }

    return pattern.filterWhen(predicate)
}

// -- bypass() ---------------------------------------------------------------------------------------------------------

private fun applyBypass(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    if (args.isEmpty()) return source

    val condition = args.toPattern(defaultModifier).not()

    return StructurePattern(
        source = source,
        other = condition,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Returns silence when the condition is true (bypasses the pattern).
 * Supports both static values and patterns.
 */
@StrudelDsl
val bypass by dslFunction { args -> applyBypass(silence, args) }

@StrudelDsl
val StrudelPattern.bypass by dslPatternExtension { source, args -> applyBypass(source, args) }

@StrudelDsl
val String.bypass by dslStringExtension { source, args -> applyBypass(source, args) }

// -- superimpose() ----------------------------------------------------------------------------------------------------


/**
 * Layers a modified version of the pattern on top of itself.
 *
 * Example: s("bd sd").superimpose { it.fast(2) }
 */
@StrudelDsl
val StrudelPattern.superimpose by dslPatternExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val transform = args.firstOrNull() as? (StrudelPattern) -> StrudelPattern ?: { it }
    SuperimposePattern(source, transform)
}

// TODO: string extension

// -- superimpose() ----------------------------------------------------------------------------------------------------

/**
 * Applies multiple transformation functions to the pattern and stacks the results.
 *
 * Example: s("bd").layer({ it.fast(2) }, { it.rev() })
 */
@StrudelDsl
val StrudelPattern.layer by dslPatternExtension { source, args ->
    val transforms = args.filterIsInstance<(StrudelPattern) -> StrudelPattern>()

    if (transforms.isEmpty()) {
        silence
    } else {
        val patterns = transforms.mapNotNull { transform ->
            try {
                transform(source)
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

// TODO: string extension

/**
 * Alias for [layer].
 */
@StrudelDsl
val StrudelPattern.apply by dslPatternExtension { source, args ->
    source.layer(args)
}

// TODO: string extension

// -- run() ------------------------------------------------------------------------------------------------------------

// TODO: run -> see signal.mjs

// -- binary() ---------------------------------------------------------------------------------------------------------

// TODO: binary -> see signal.mjs

// -- binaryN() --------------------------------------------------------------------------------------------------------

// TODO: binaryN -> see signal.mjs

// -- binaryL() --------------------------------------------------------------------------------------------------------

// TODO: binaryL -> see signal.mjs

// -- binaryNL() -------------------------------------------------------------------------------------------------------

// TODO: binaryNL -> see signal.mjs

// -- randL() ----------------------------------------------------------------------------------------------------------

// TODO: randL -> see signal.mjs

// -- randrun() --------------------------------------------------------------------------------------------------------

// TODO: randrun -> see signal.mjs

// -- shuffle() --------------------------------------------------------------------------------------------------------

// TODO: shuffle -> see signal.mjs

// -- scramble() -------------------------------------------------------------------------------------------------------

// TODO: scramble -> see signal.mjs

