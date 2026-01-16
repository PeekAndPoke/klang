@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
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
    silence.slow(args)
}

@StrudelDsl
val StrudelPattern.gap by dslPatternExtension { _, args ->
    silence.slow(args)
}

@StrudelDsl
val String.gap by dslStringExtension { _, args ->
    silence.slow(args)
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
    args.toPattern {
        copy(
            value = it?.asVoiceValue(),
            gain = 1.0,
        )
    }
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
 * @param {predicate} function to test the begin time (Double)
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

// -- zoom() -----------------------------------------------------------------------------------------------------------

private fun applyZoom(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val startArg = args.getOrNull(0)
    val endArg = args.getOrNull(1)

    // Parse the start argument into a pattern
    val startPattern = when (startArg) {
        is StrudelPattern -> startArg
        null -> parseMiniNotation("0") { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        else -> parseMiniNotation(startArg.toString()) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
    }

    // Parse the end argument into a pattern
    val endPattern = when (endArg) {
        is StrudelPattern -> endArg
        null -> parseMiniNotation("1") { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        else -> parseMiniNotation(endArg.toString()) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
    }

    // Check if we have static values for optimization
    val staticStart = startArg?.asDoubleOrNull()
    val staticEnd = endArg?.asDoubleOrNull()

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
val StrudelPattern.zoom by dslPatternExtension { p, args -> applyZoom(p, args) }

/**
 * Plays a portion of a pattern, specified by the beginning and end of a time span.
 */
fun StrudelPattern.zoom(start: Double, end: Double): StrudelPattern = applyZoom(this, listOf(start, end))

@StrudelDsl
val String.zoom by dslStringExtension { p, args -> applyZoom(p, args) }

@StrudelDsl
fun String.zoon(start: Double, end: Double): StrudelPattern = this.zoom(start, end)

// -- bite() -----------------------------------------------------------------------------------------------------------

private fun applyBite(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val nArg = args.getOrNull(0)
    val indicesArg = args.getOrNull(1)

    // Parse the n argument into a pattern
    val nPattern = when (nArg) {
        is StrudelPattern -> nArg
        null -> parseMiniNotation("4") { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        else -> parseMiniNotation(nArg.toString()) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
    }

    // Parse the indices argument into a pattern
    val indices = when (indicesArg) {
        is StrudelPattern -> indicesArg
        is String -> parseMiniNotation(input = indicesArg) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        else -> return silence
    }

    // Check if we have a static value for n for optimization
    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use the original inline implementation
        object : StrudelPattern {
            override val weight: Double get() = source.weight

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
val StrudelPattern.bite by dslPatternExtension { p, args -> applyBite(p, args) }

@StrudelDsl
val String.bite by dslStringExtension { p, args -> applyBite(p, args) }

// -- segment() --------------------------------------------------------------------------------------------------------

private fun applySegment(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val nArg = args.firstOrNull()

    val nPattern = when (nArg) {
        is StrudelPattern -> nArg
        null -> parseMiniNotation("1") { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        else -> parseMiniNotation(nArg.toString()) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
    }

    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use original implementation with struct + fast
        val structPat = parseMiniNotation("x") { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
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
val StrudelPattern.segment by dslPatternExtension { p, args -> applySegment(p, args) }

@StrudelDsl
fun StrudelPattern.segment(n: Int) = this.segment(n as Any)

@StrudelDsl
val String.segment by dslStringExtension { p, args -> applySegment(p, args) }

@StrudelDsl
fun String.segment(n: Int) = this.segment(n as Any)

/** Alias for [segment] */
@StrudelDsl
val StrudelPattern.seg by dslPatternExtension { p, args -> applySegment(p, args) } // Alias

/** Alias for [segment] */
@StrudelDsl
fun StrudelPattern.seg(n: Int) = this.segment(n as Any)

/** Alias for [segment] */
@StrudelDsl
val String.seg by dslStringExtension { p, args -> applySegment(p, args) }

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
val euclid by dslFunction { args ->
    val pattern = args.drop(2).toPattern(defaultModifier)

    pattern.euclid(args)
}

@StrudelDsl
val StrudelPattern.euclid by dslPatternExtension { p, args ->
    val pulses = args.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = args.getOrNull(1)?.asIntOrNull() ?: 0

    applyEuclid(source = p, pulses = pulses, steps = steps, rotation = 0)
}

@StrudelDsl
val String.euclid by dslStringExtension { p, args -> p.euclid(args) }

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
val euclidRot by dslFunction { args ->
    val pattern = args.drop(3).toPattern(defaultModifier)

    pattern.euclidRot(args)
}

@StrudelDsl
val StrudelPattern.euclidRot by dslPatternExtension { p, args ->
    val pulses = args.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = args.getOrNull(1)?.asIntOrNull() ?: 0
    val rotation = args.getOrNull(2)?.asIntOrNull() ?: 0

    applyEuclid(source = p, pulses = pulses, steps = steps, rotation = rotation)
}

@StrudelDsl
val String.euclidRot by dslStringExtension { p, args -> p.euclidRot(args) }

/** Alias for [euclidRot] */
@StrudelDsl
val euclidrot by dslFunction { args -> euclidRot(args) }

/** Alias for [euclidRot] */
@StrudelDsl
val StrudelPattern.euclidrot by dslPatternExtension { p, args -> p.euclidRot(args) }

/** Alias for [euclidRot] */
@StrudelDsl
val String.euclidrot by dslStringExtension { p, args -> p.euclidRot(args) }

// -- bjork() ----------------------------------------------------------------------------------------------------------

/**
 * Euclidean rhythm specifying parameters as a list.
 *
 * @param {list}    List with [pulses, steps, rotation]
 * @param {pattern} the pattern to apply the euclid to
 */
@StrudelDsl
val bjork by dslFunction { args ->
    val pattern = args.drop(1).toPattern(defaultModifier)

    pattern.bjork(args)
}

@StrudelDsl
val StrudelPattern.bjork by dslPatternExtension { p, args ->
    val list = args.getOrNull(0) as? List<*> ?: args
    val pulses = list.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = list.getOrNull(1)?.asIntOrNull() ?: 0
    val rotation = list.getOrNull(2)?.asIntOrNull() ?: 0

    applyEuclid(source = p, pulses = pulses, steps = steps, rotation = rotation)
}

@StrudelDsl
val String.bjork by dslStringExtension { p, args -> p.bjork(args) }

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

/**
 * Similar to `euclid`, but each pulse is held until the next pulse, so there will be no gaps.
 *
 * @param {pulses}   the number of onsets/beats
 * @param {steps}    the number of steps to fill
 * @param {pattern}  the pattern to apply the euclid to
 */
@StrudelDsl
val euclidLegato by dslFunction { args ->
    val pattern = args.drop(2).toPattern(defaultModifier)

    pattern.euclidLegato(args)
}

@StrudelDsl
val StrudelPattern.euclidLegato by dslPatternExtension { p, args ->
    val pulses = args.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = args.getOrNull(1)?.asIntOrNull() ?: 0

    EuclideanPattern.createLegato(inner = p, pulses = pulses, steps = steps, rotation = 0)
}

@StrudelDsl
val String.euclidLegato by dslStringExtension { p, args -> p.euclidLegato(args) }

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
val euclidLegatoRot by dslFunction { args ->
    val pattern = args.drop(3).toPattern(defaultModifier)

    pattern.euclidLegatoRot(args)
}

@StrudelDsl
val StrudelPattern.euclidLegatoRot by dslPatternExtension { p, args ->
    val pulses = args.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = args.getOrNull(1)?.asIntOrNull() ?: 0
    val rotation = args.getOrNull(2)?.asIntOrNull() ?: 0

    EuclideanPattern.createLegato(inner = p, pulses = pulses, steps = steps, rotation = rotation)
}

@StrudelDsl
val String.euclidLegatoRot by dslStringExtension { p, args -> p.euclidLegatoRot(args) }

// -- euclidish() ------------------------------------------------------------------------------------------------------

private fun applyEuclidish(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val pulses = args.getOrNull(0)?.asIntOrNull() ?: 0
    val steps = args.getOrNull(1)?.asIntOrNull() ?: 0
    val grooveArg = args.getOrNull(2)

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveArg) {
        is StrudelPattern -> grooveArg
        else -> {
            val g = grooveArg ?: 0
            // Use parse to handle string numbers or mini-notation if passed
            parseMiniNotation(g.toString()) { AtomicPattern(VoiceData.empty.defaultModifier(it)) }
        }
    }

    if (pulses <= 0 || steps <= 0) return silence

    val structPattern = EuclideanMorphPattern(pulses, steps, groovePattern)
    return source.struct(structPattern)
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
val euclidish by dslFunction { args ->
    // euclidish(pulses, steps, groove, pat)
    val pattern = args.drop(3).toPattern(defaultModifier)
    applyEuclidish(pattern, args.take(3))
}

@StrudelDsl
val StrudelPattern.euclidish by dslPatternExtension { p, args ->
    applyEuclidish(p, args)
}

@StrudelDsl
val String.euclidish by dslStringExtension { p, args -> p.euclidish(args) }

/** Alias for [euclidish] */
@StrudelDsl
val eish by dslFunction { args -> euclidish(args) }

/** Alias for [euclidish] */
@StrudelDsl
val StrudelPattern.eish by dslPatternExtension { p, args -> p.euclidish(args) }

/** Alias for [euclidish] */
@StrudelDsl
val String.eish by dslStringExtension { p, args -> p.euclidish(args) }


// -- run() ------------------------------------------------------------------------------------------------------------

private fun applyRun(n: Int): StrudelPattern {
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
val run by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0
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
val binaryN by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.asIntOrNull() ?: 16
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
val binary by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0
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
val binaryNL by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.asIntOrNull() ?: 16
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
val binaryL by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0
    if (n == 0) {
        applyBinaryNL(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryNL(n, bits)
    }
}
