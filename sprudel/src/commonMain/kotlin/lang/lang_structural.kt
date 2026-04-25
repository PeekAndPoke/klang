@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.common.math.lcm
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel._bindRestart
import io.peekandpoke.klang.sprudel._fmap
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._splitQueries
import io.peekandpoke.klang.sprudel._squeezeJoin
import io.peekandpoke.klang.sprudel._stepJoin
import io.peekandpoke.klang.sprudel._withHapSpan
import io.peekandpoke.klang.sprudel._withHapTime
import io.peekandpoke.klang.sprudel._withQuerySpan
import io.peekandpoke.klang.sprudel._withQueryTime
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.addons.not
import io.peekandpoke.klang.sprudel.lang.addons.timeLoop
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.map
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ControlValueProvider
import io.peekandpoke.klang.sprudel.pattern.EmptyPattern
import io.peekandpoke.klang.sprudel.pattern.GapPattern
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.RepeatCyclesPattern
import io.peekandpoke.klang.sprudel.pattern.SegmentPattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.pattern.StructurePattern
import io.peekandpoke.klang.sprudel.withSteps
import io.peekandpoke.klang.sprudel.withWeight
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangStructuralInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Structural patterns
// ///

// -- hush() / bypass() / mute() --------------------------------------------------------------------------------------

/**
 * Applies hush/mute with control pattern support.
 * Returns silence when the condition is true.
 * When called without arguments, unconditionally returns silence.
 */
private fun applyHush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // No arguments: unconditionally mute
    if (args.isEmpty()) return silence

    // With control pattern: mute when condition is true
    val condition = args.toPattern()
    val conditionNot = condition.not()

    return StructurePattern(
        source = source,
        other = conditionNot,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").hush()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").hush("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hush(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyHush(this, args.toList().asSprudelDslArgs(callInfo))

/** Silences this string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
@KlangScript.Function
fun String.hush(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hush(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that silences the source pattern.
 *
 * Without arguments, unconditionally silences. With a condition, silences when truthy.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(hush())          // Unconditional silence via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(hush("<1 0>"))   // Silent on odd cycles via mapper
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
@KlangScript.Function
fun hush(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hush(*args, callInfo = callInfo) }

/** Chains a hush onto this [PatternMapperFn]; silences or conditionally gates the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hush(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hush(*args, callInfo = callInfo) }

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").bypass()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").bypass("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.hush(*args, callInfo = callInfo)

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
@KlangScript.Function
fun String.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bypass(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(bypass())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@SprudelDsl
@KlangScript.Function
fun bypass(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bypass(*args, callInfo = callInfo) }

/** Chains a bypass (alias for [hush]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bypass(*args, callInfo = callInfo) }

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").mute()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").mute("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.mute(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.hush(*args, callInfo = callInfo)

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
@KlangScript.Function
fun String.mute(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mute(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(mute())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
@KlangScript.Function
fun mute(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mute(*args, callInfo = callInfo) }

/** Chains a mute (alias for [hush]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.mute(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mute(*args, callInfo = callInfo) }

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates a silent pattern occupying the given number of steps. Supports control patterns via _innerJoin. */
private fun applyGap(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val stepsArg = args.getOrNull(0) ?: return GapPattern(Rational.ONE)

    // For static values create GapPattern directly so its weight is preserved.
    // SequencePattern reads .weight once at construction for proportional allocation.
    val staticSteps = stepsArg.value?.asRationalOrNull()
    if (staticSteps != null) {
        return GapPattern(staticSteps)
    }

    // For control patterns evaluate the step count per event via _innerJoin.
    // Proportional weight in sequences is not supported for control patterns (defaults to 1).
    return silence._innerJoin(stepsArg) { _, stepsVal ->
        val steps = stepsVal?.asRationalOrNull() ?: Rational.ONE
        GapPattern(steps)
    }
}

/**
 * Creates a silent slot that produces no events.
 *
 * When called with a static step count and used inside [seq] or [cat], the gap occupies
 * proportionally more space than adjacent 1-step elements. Equivalent to the `~` rest character
 * in mini-notation.
 *
 * Control patterns (e.g. `gap("<1 2>")`) are supported — the step count is evaluated per event —
 * but proportional space allocation in sequences is not affected (weight defaults to 1 for
 * control patterns, since [seq] reads weights once at construction time).
 *
 * @param steps Step count for the silence (default 1). Accepts static numbers and control patterns.
 * @return A silent pattern with the given step weight
 *
 * ```KlangScript(Playable)
 * seq("bd", gap(), "hh").s()  // bd, 1-step rest, hh — each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd", gap(2), "hh").s()  // bd=1/4, rest=2/4, hh=1/4
 * ```
 * @category structural
 * @tags silence, rest, gap, rhythm
 */
@SprudelDsl
@KlangScript.Function
fun gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyGap(steps.toList().asSprudelDslArgs(callInfo))

/**
 * Replaces this pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript(Playable)
 * note("c").gap()  // Replaces with 1-step silence
 * ```
 *
 * ```KlangScript(Playable)
 * note("c").gap(2)  // Replaces with 2-step silence
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyGap(steps.toList().asSprudelDslArgs(callInfo))

/**
 * Replaces this string pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript(Playable)
 * seq("bd", "hh".gap(), "sd").s()  // Middle step replaced by silence
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gap(*steps, callInfo = callInfo)

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
private fun applySeq(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/**
 * Creates a sequence pattern that squeezes all patterns into one cycle.
 *
 * All patterns are evenly distributed within a single cycle. With two patterns
 * each gets half the cycle; with three patterns each gets a third, and so on.
 * A list passed as an argument is treated as a nested sub-sequence that occupies
 * the same time slot as any other single argument.
 *
 * @param patterns Patterns to squeeze into one cycle. Accepts patterns, strings, numbers,
 *                 lists (as nested sub-sequences), and other values that can be converted to patterns.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * seq("c d e", "f g a").note()  // Two patterns squeezed into one cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq(note("c"), note("e"), note("g"))  // Three notes, each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd", ["sd", "oh"], "hh").s()  // Nested list as sub-sequence within its slot
 * ```
 * @category structural
 * @tags sequence, timing, control, order, pattern-creator
 */
@SprudelDsl
@KlangScript.Function
fun seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySeq(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern and squeezes all of them into one cycle.
 *
 * This pattern and all appended patterns are evenly distributed within a single cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * note("c e").seq("g a".note())
 * ```
 *
 * ```KlangScript(Playable)
 * "bd sd".seq("hh hh", "cp").s()
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySeq(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Converts this string to a pattern and squeezes it together with additional patterns into one cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * "c e".seq("g a").note()  // Two patterns squeezed into one cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).seq(*patterns, callInfo = callInfo)

// -- mini() -----------------------------------------------------------------------------------------------------------

/**
 * Parses mini-notation and returns the resulting pattern. Effectively an alias for [seq].
 *
 * Mini-notation is the compact pattern language for expressing sequences, sub-sequences,
 * alternations, and other rhythmic structures inline as strings.
 *
 * @param patterns Strings or other pattern-like values to parse as mini-notation.
 * @return A pattern built from the mini-notation input
 *
 * ```KlangScript(Playable)
 * mini("c d e f").note()  // Four notes, one per quarter cycle
 * ```
 *
 * ```KlangScript(Playable)
 * mini("bd [sd cp] hh").s()  // Nested sub-sequence in square brackets
 * ```
 * @category structural
 * @tags mini, notation, parse, sequence
 */
@SprudelDsl
@KlangScript.Function
fun mini(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    patterns.toList().asSprudelDslArgs(callInfo).toPattern()

/**
 * Parses this string as mini-notation and returns the resulting pattern.
 *
 * ```KlangScript(Playable)
 * "c d e f".mini().note()  // Four notes from mini-notation string
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.mini(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation)

// -- stack() ----------------------------------------------------------------------------------------------------------

private fun applyStack(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> StackPattern(patterns)
    }
}

/**
 * Plays multiple patterns simultaneously, layering them on top of each other.
 *
 * Unlike [seq], which plays patterns one after another, `stack` overlays all patterns so they all
 * sound at the same time over the full cycle. This is useful for chords, polyrhythms, and combining
 * independent pattern layers.
 *
 * @param patterns Patterns to layer. Accepts patterns, strings, numbers, and other pattern-like values.
 * @return A pattern that plays all inputs simultaneously
 *
 * ```KlangScript(Playable)
 * stack(note("c e g"), s("bd sd"))  // Chord with beat underneath
 * ```
 *
 * ```KlangScript(Playable)
 * stack("c e", "g b").note()  // Two melodic lines at the same time
 * ```
 * @category structural
 * @tags stack, layer, chord, polyrhythm, simultaneous
 */
@SprudelDsl
@KlangScript.Function
fun stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStack(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Layers this pattern together with additional patterns so they all play simultaneously.
 *
 * ```KlangScript(Playable)
 * note("c e g").stack(s("bd sd"))  // Melody on top of a beat
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").stack("g b".note())  // Two melodic lines layered
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStack(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and layers it together with additional patterns.
 *
 * ```KlangScript(Playable)
 * "c e g".stack("g b d").note()  // Two chord voicings layered
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stack(*patterns, callInfo = callInfo)

// -- arrange() --------------------------------------------------------------------------------------------------------

private fun applyArrange(args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Plays each segment for a specified number of cycles, forming a repeating arrangement.
 *
 * Each segment is a `[duration, pattern]` pair where `duration` is the number of cycles that
 * pattern plays at its natural speed. A bare pattern without a duration defaults to 1 cycle.
 * The whole arrangement repeats after the sum of all durations.
 *
 * Unlike [stepcat], which compresses all patterns into a single cycle, `arrange` keeps each
 * pattern's internal tempo — a 2-cycle segment genuinely plays the pattern for 2 full cycles.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (duration defaults to 1).
 * @return A pattern that plays each segment for its specified number of cycles, then repeats
 *
 * ```KlangScript(Playable)
 * arrange([2, "a b"], [1, "c"]).note()  // "a b" for 2 cycles, "c" for 1
 * ```
 *
 * ```KlangScript(Playable)
 * arrange([3, note("c e g")], [1, note("f a c")]).s("piano")  // chord changes over 4 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * arrange(note("c e g"), [2, note("f a c")]).s("piano")  // Pattern without weight
 * ```
 * @category structural
 * @tags arrange, sequence, timing, duration, loop
 */
@SprudelDsl
@KlangScript.Function
fun arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArrange(segments.toList().asSprudelDslArgs(callInfo))

/**
 * Prepends this pattern (duration 1) and plays it followed by the given segments.
 *
 * ```KlangScript(Playable)
 * note("c e g").arrange([2, note("f a c")]).s("piano")  // 1 cycle chord, then 2 cycles
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArrange(listOf(SprudelDslArg.of(this)) + segments.toList().asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern (duration 1) and arranges it together with the given segments.
 *
 * ```KlangScript(Playable)
 * "c e g".arrange([2, "f a c"]).note()  // 1 cycle, then 2 cycles of second chord
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).arrange(*segments, callInfo = callInfo)

// -- stepcat() / timeCat() --------------------------------------------------------------------------------------------

private fun applyStepcat(args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * Concatenates weighted patterns and compresses the result to fit exactly one cycle.
 *
 * Each segment is a `[duration, pattern]` pair. Duration determines the proportional share of the
 * cycle each pattern gets. A bare pattern without a duration defaults to weight 1.
 * Unlike [arrange], `stepcat` always fits everything into a single cycle regardless of durations.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * stepcat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * stepcat([2, note("c")], [1, note("e g")])  // "c" takes 2/3, "e g" takes 1/3
 * ```
 * @alias timeCat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
@KlangScript.Function
fun stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStepcat(segments.toList().asSprudelDslArgs(callInfo))

/**
 * Prepends this pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").stepcat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStepcat(listOf(SprudelDslArg.of(this)) + segments.toList().asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".stepcat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * timeCat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
@KlangScript.Function
fun timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").timeCat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".timeCat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).timeCat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * timecat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
@KlangScript.Function
fun timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").timecat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".timecat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).timecat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * s_cat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, timecat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
@KlangScript.Function
fun s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").s_cat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".s_cat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).s_cat(*segments, callInfo = callInfo)

// -- stackBy() --------------------------------------------------------------------------------------------------------

private fun applyStackBy(patterns: List<SprudelPattern>, alignment: Double): SprudelPattern {
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

            val segments = mutableListOf<SprudelPattern>()

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
 * Layers patterns simultaneously, aligning shorter patterns within the span of the longest.
 *
 * Unlike [stack], which always aligns all patterns from the start, `stackBy` lets you control
 * where shorter patterns sit within the span of the longest: 0 = left-aligned, 0.5 = centered,
 * 1 = right-aligned.
 *
 * @param alignment Position within the longest pattern's span (0 = left, 0.5 = center, 1 = right).
 * @param patterns Patterns to layer. The longest determines the total span.
 * @return A pattern with all inputs layered at the given alignment
 *
 * ```KlangScript(Playable)
 * stackBy(0.5, note("c"), note("c e g"))  // short pattern centered within the long one
 * ```
 *
 * ```KlangScript(Playable)
 * stackBy(1.0, s("bd"), s("bd sd ht lt"))  // short pattern right-aligned
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
@KlangScript.Function
fun stackBy(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    // TODO: support control patterns
    val dslArgs = args.toList().asSprudelDslArgs(callInfo)
    val alignmentVal = dslArgs.firstOrNull()?.value?.asDoubleOrNull() ?: 0.0
    val patternList = dslArgs.drop(1).toListOfPatterns()
    return applyStackBy(patterns = patternList, alignment = alignmentVal)
}

// -- stackLeft() ------------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the left (start) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones left-aligned
 *
 * ```KlangScript(Playable)
 * stackLeft(note("c"), note("c e g"))  // short pattern starts at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
@KlangScript.Function
fun stackLeft(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 0.0,
    )

// -- stackRight() -----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the right (end) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones right-aligned
 *
 * ```KlangScript(Playable)
 * stackRight(note("c"), note("c e g"))  // short pattern ends at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
@KlangScript.Function
fun stackRight(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 1.0,
    )

// -- stackCentre() ----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the centre of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones centred
 *
 * ```KlangScript(Playable)
 * stackCentre(note("c"), note("c e g"))  // short pattern centred within the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
@KlangScript.Function
fun stackCentre(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 0.5,
    )

// -- polyrhythm() -----------------------------------------------------------------------------------------------------

/**
 * Alias for [stack]. Plays multiple patterns simultaneously to create polyrhythms.
 *
 * @param patterns Patterns to layer simultaneously.
 * @return A pattern that plays all inputs at the same time
 *
 * ```KlangScript(Playable)
 * polyrhythm(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat patterns together
 * ```
 * @alias stack
 * @category structural
 * @tags polyrhythm, stack, layer, simultaneous, rhythm
 */
@SprudelDsl
@KlangScript.Function
fun polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stack(*patterns, callInfo = callInfo)

/**
 * Alias for [stack]. Layers this pattern together with additional patterns simultaneously.
 *
 * ```KlangScript(Playable)
 * s("bd sd").polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stack(*patterns, callInfo = callInfo)

/**
 * Alias for [stack]. Parses this string as a pattern and layers it with additional patterns.
 *
 * ```KlangScript(Playable)
 * "bd sd".polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).polyrhythm(*patterns, callInfo = callInfo)

// -- sequenceP() ------------------------------------------------------------------------------------------------------

/**
 * Alias for [seq]. Creates a sequence pattern that squeezes all patterns into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * sequenceP("c d", "e f").note()  // Two patterns squeezed into one cycle
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@SprudelDsl
@KlangScript.Function
fun sequenceP(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    seq(*patterns, callInfo = callInfo)

// -- cat() ------------------------------------------------------------------------------------------------------------

fun applyCat(patterns: List<SprudelPattern>): SprudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : SprudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Rational? = null

        override fun estimateCycleDuration(): Rational {
            return patterns.fold(Rational.ZERO) { acc, p -> acc + p.estimateCycleDuration() }
        }

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
            val totalDuration = estimateCycleDuration()
            if (totalDuration <= Rational.ZERO) return emptyList()

            val result = mutableListOf<SprudelPatternEvent>()

            // Find which "loops" of the total sequence we touch
            val startLoop = (from / totalDuration).floor()
            val endLoop = (to / totalDuration).ceil()

            var currentLoop = startLoop
            while (currentLoop < endLoop) {
                val loopStart = currentLoop * totalDuration
                var currentOffset = loopStart

                for (p in patterns) {
                    val dur = p.estimateCycleDuration()
                    val pStart = currentOffset
                    val pEnd = pStart + dur

                    // Check intersection
                    val start = if (from > pStart) from else pStart
                    val end = if (to < pEnd) to else pEnd

                    if (end > start) {
                        // Map to pattern local time
                        val localStart = start - pStart
                        val localEnd = end - pStart

                        val pEvents = p.queryArcContextual(localStart, localEnd, ctx)

                        // Shift back
                        pEvents.forEach { ev ->
                            result.add(
                                ev.copy(
                                    part = ev.part.shift(pStart),
                                    whole = ev.whole.shift(pStart)
                                )
                            )
                        }
                    }
                    currentOffset += dur
                }
                currentLoop += Rational.ONE
            }
            return result
        }
    }
}

/**
 * Concatenates patterns in sequence, each playing for its natural cycle duration before the next begins.
 *
 * Unlike [seq], which squeezes all patterns into one cycle, `cat` plays each pattern for its full
 * natural duration. A 2-cycle pattern takes 2 cycles, then the next pattern begins.
 *
 * @param patterns Patterns to concatenate. Each plays for its natural duration.
 * @return A pattern that plays each input in turn for its natural duration
 *
 * ```KlangScript(Playable)
 * cat(note("c d"), note("e f g")).s("piano")  // 2-step then 3-step pattern in sequence
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), s("hh hh hh hh"))  // alternates between patterns each cycle
 * ```
 * @alias slowcat
 * @category structural
 * @tags cat, sequence, concatenate, timing
 */
@SprudelDsl
@KlangScript.Function
fun cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyCat(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern, each playing for its natural cycle duration.
 *
 * ```KlangScript(Playable)
 * note("c d").cat(note("e f g"))  // "c d" then "e f g" in sequence
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyCat(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and concatenates it with the given patterns in sequence.
 *
 * ```KlangScript(Playable)
 * "c d".cat("e f g").note()  // "c d" then "e f g" in sequence
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cat(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Concatenates patterns, squeezing them all into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * fastcat("bd", "sd").s()  // same as seq("bd", "sd") or "bd sd"
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@SprudelDsl
@KlangScript.Function
fun fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    seq(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Appends patterns to this pattern, squeezing all into one cycle.
 *
 * ```KlangScript(Playable)
 * s("bd").fastcat(s("sd"))  // "bd sd" squeezed into one cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.seq(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Parses this string and squeezes it together with the given patterns into one cycle.
 *
 * ```KlangScript(Playable)
 * "bd".fastcat("sd").s()  // "bd sd" squeezed into one cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastcat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Concatenates patterns, each taking one full cycle.
 *
 * Note: unlike the JS implementation, this behaves like `cat` / `slowcatPrime`, maintaining
 * absolute time (cycles of inner patterns may be "skipped" while they are not playing).
 *
 * @param patterns Patterns to concatenate. Each plays for one cycle.
 * @return A pattern that plays each input for one cycle in turn
 *
 * ```KlangScript(Playable)
 * slowcat("bd sd", "hh hh hh hh").s()  // cycle 0: "bd sd", cycle 1: "hh hh hh hh"
 * ```
 * @alias cat
 * @category structural
 * @tags sequence, concatenate, timing
 */
@SprudelDsl
@KlangScript.Function
fun slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    cat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Appends patterns to this pattern, each taking one full cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd").slowcat(s("hh hh hh hh"))  // alternates each cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.cat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Parses this string and concatenates with the given patterns, each taking one cycle.
 *
 * ```KlangScript(Playable)
 * "bd sd".slowcat("hh hh hh hh").s()  // alternates each cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slowcat(*patterns, callInfo = callInfo)

// -- slowcatPrime() ---------------------------------------------------------------------------------------------------

/**
 * Cycles through a list of patterns infinitely, playing one pattern per cycle.
 * Preserves absolute time (does not reset pattern time to 0 for each cycle).
 *
 * This corresponds to 'slowcatPrime' in JS.
 */
fun applySlowcatPrime(patterns: List<SprudelPattern>): SprudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : SprudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Rational? = null

        override fun estimateCycleDuration(): Rational = Rational.ONE * patterns.size

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {

            val result = mutableListOf<SprudelPatternEvent>()
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
 * Cycles through patterns one per cycle, preserving absolute time across pattern switches.
 *
 * Like [cat], but when a pattern resumes it continues from where it would be at absolute time,
 * rather than restarting from zero. This means inner cycles of each pattern are not reset.
 *
 * @param patterns Patterns to cycle through, one per cycle.
 * @return A pattern that cycles through each input at absolute time
 *
 * ```KlangScript(Playable)
 * slowcatPrime(note("c d e f"), note("g a b c")).s("piano")  // each pattern plays at abs time
 * ```
 * @category structural
 * @tags sequence, concatenate, timing, absolute
 */
@SprudelDsl
@KlangScript.Function
fun slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlowcatPrime(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern, cycling through them one per cycle at absolute time.
 *
 * ```KlangScript(Playable)
 * note("c d").slowcatPrime(note("e f g"))  // cycles through at absolute time
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlowcatPrime(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string and cycles through it with given patterns, one per cycle at absolute time.
 *
 * ```KlangScript(Playable)
 * "c d".slowcatPrime("e f g").note()  // cycles through at absolute time
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slowcatPrime(*patterns, callInfo = callInfo)

// -- polymeter() ------------------------------------------------------------------------------------------------------

private fun applyPolymeter(patterns: List<SprudelPattern>, baseSteps: Int? = null): SprudelPattern {
    if (patterns.isEmpty()) return silence

    // Filter for patterns that have steps defined
    val validPatterns = patterns.filter { it.numSteps != null }
    if (validPatterns.isEmpty()) return silence

    val patternSteps = validPatterns.mapNotNull { it.numSteps?.toInt() }
    val targetSteps = baseSteps ?: lcm(patternSteps).takeIf { it > 0 } ?: 4

    val adjustedPatterns = validPatterns.map { pat ->
        val steps = pat.numSteps!!.toInt()
        if (steps == targetSteps) {
            pat
        } else {
            pat.fast(targetSteps.toDouble() / steps)
        }
    }

    return PropertyOverridePattern(
        source = StackPattern(adjustedPatterns),
        stepsOverride = targetSteps.toRational()
    )
}

/**
 * Aligns patterns with different step counts so they share a common cycle, creating polymeters.
 *
 * Patterns are sped up so that their LCM number of steps fits into one cycle. For example,
 * a 2-step and a 3-step pattern are both sped up to a 6-step cycle — the 2-step plays 3 times
 * and the 3-step plays 2 times per cycle, all in lockstep.
 *
 * @param patterns Patterns to align. Each must have a defined step count.
 * @return A pattern with all inputs aligned to their LCM step count per cycle
 *
 * ```KlangScript(Playable)
 * polymeter(note("c d"), note("c d e")).s("piano")  // 2- and 3-step in a 6-step cycle
 * ```
 *
 * ```KlangScript(Playable)
 * polymeter(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat polyrhythm
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, alignment
 */
@SprudelDsl
@KlangScript.Function
fun polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPolymeter(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Prepends this pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript(Playable)
 * note("c d").polymeter(note("c d e"))  // 2 and 3 steps aligned to LCM
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPolymeter(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript(Playable)
 * "c d".polymeter("c d e").note()  // 2 and 3 steps aligned to LCM
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).polymeter(*patterns, callInfo = callInfo)

// -- polymeterSteps() -------------------------------------------------------------------------------------------------

/**
 * Like [polymeter], but with an explicit step count instead of using the LCM.
 *
 * All patterns are sped up or slowed down to fit exactly `steps` steps per cycle.
 *
 * @param args First argument is the target step count; remaining arguments are the patterns.
 * @return A pattern with all inputs adjusted to the given step count per cycle
 *
 * ```KlangScript(Playable)
 * polymeterSteps(4, note("c d"), note("c d e"))  // both fit into 4 steps per cycle
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, steps
 */
@SprudelDsl
@KlangScript.Function
fun polymeterSteps(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val steps = argList.getOrNull(0)?.value?.asIntOrNull() ?: 4
    val patterns = argList.drop(1).toListOfPatterns()
    return applyPolymeter(patterns = patterns, baseSteps = steps)
}

// -- pure() -----------------------------------------------------------------------------------------------------------

/**
 * Creates an atomic pattern that repeats a single value every cycle.
 *
 * @param value The value to wrap in a pattern.
 * @return A pattern that emits `value` once per cycle
 *
 * ```KlangScript(Playable)
 * pure("c").note()  // repeats note "c" every cycle
 * ```
 *
 * ```KlangScript(Playable)
 * pure(1)  // repeats the number 1 every cycle
 * ```
 * @category structural
 * @tags pure, value, atomic, repeat
 */
@SprudelDsl
@KlangScript.Function
fun pure(value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    AtomicPattern(SprudelVoiceData.empty.copy(value = value.asVoiceValue()))

// -- struct() ---------------------------------------------------------------------------------------------------------

private fun applyStruct(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
    val structure = structArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = true
    )
}

/**
 * Restructures this pattern using the timing of a mask pattern, keeping only truthy mask events.
 *
 * The mask provides the rhythmic structure; the source pattern provides the values. Only source
 * events that overlap with truthy events in the mask are kept, clipped to the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").struct("x ~ x x ~ x x ~")  // hats shaped by a boolean rhythm pattern
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").struct("x*4")         // chord hits restructured to 4 equal beats
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.struct(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStruct(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Restructures this string pattern using the mask's timing; keeps only truthy mask events. */
@SprudelDsl
@KlangScript.Function
fun String.struct(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).struct(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that restructures the source pattern using the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source pattern to the mask's rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(struct("x ~ x x"))    // via mapper
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@SprudelDsl
@KlangScript.Function
fun struct(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.struct(*args, callInfo = callInfo) }

/** Chains a struct onto this [PatternMapperFn]; restructures using the mask's timing. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.struct(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.struct(*args, callInfo = callInfo) }

// -- structAll() ------------------------------------------------------------------------------------------------------

private fun applyStructAll(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
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
 * Like [struct], but keeps all source events overlapping the mask — including falsy ones.
 *
 * While [struct] filters to only truthy mask events, `structAll` uses the mask purely for timing
 * without filtering by value. Useful when the mask defines structure but all events should pass.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm, with all overlapping events kept.
 *
 * ```KlangScript(Playable)
 * note("c e g").structAll("x x")   // all chord notes kept within each x window
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").structAll("x x x x") // both c and e kept within each mask window
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStructAll(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Like [structAll] on a string pattern; keeps all events overlapping the mask. */
@SprudelDsl
@KlangScript.Function
fun String.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).structAll(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that reshapes the source using the mask, keeping all overlapping events.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source keeping all overlapping events.
 *
 * ```KlangScript(Playable)
 * note("c e g").apply(structAll("x x"))   // via mapper
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@SprudelDsl
@KlangScript.Function
fun structAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.structAll(*args, callInfo = callInfo) }

/** Chains a structAll onto this [PatternMapperFn]; reshapes keeping all overlapping events. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.structAll(*args, callInfo = callInfo) }

// -- mask() -----------------------------------------------------------------------------------------------------------

private fun applyMask(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Filters this pattern using a boolean mask, keeping events that overlap truthy mask events.
 *
 * Unlike [struct], which uses the mask's timing for structure, `mask` keeps the source pattern's
 * original timing and gates out events where the mask is falsy.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return The source pattern gated by the mask.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").mask("1 0 1 1")  // second beat silenced by the mask
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").mask("<1 0>")      // entire pattern alternates on/off each cycle
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.mask(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMask(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Filters this string pattern using a boolean mask; truthy events pass, falsy events are silenced. */
@SprudelDsl
@KlangScript.Function
fun String.mask(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mask(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that filters the source using a boolean mask.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return A [PatternMapperFn] that gates the source pattern by the mask.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(mask("1 0 1 1"))  // via mapper
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@SprudelDsl
@KlangScript.Function
fun mask(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mask(*args, callInfo = callInfo) }

/** Chains a mask onto this [PatternMapperFn]; gates the result by the boolean mask. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.mask(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mask(*args, callInfo = callInfo) }

// -- maskAll() --------------------------------------------------------------------------------------------------------

private fun applyMaskAll(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = false
    )
}

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * @param mask Pattern that defines the gating structure (all values allowed, not just truthy).
 * @return The source pattern gated by the mask's structure, with all overlapping events kept.
 *
 * ```KlangScript(Playable)
 * note("c d e f").maskAll("x ~ x ~")  // every other beat silenced by structure
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMaskAll(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Like [maskAll] on a string pattern; gates by mask structure, all values pass. */
@SprudelDsl
@KlangScript.Function
fun String.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).maskAll(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that gates the source using the mask's structure (all values pass).
 *
 * @param mask Pattern that defines the gating structure.
 * @return A [PatternMapperFn] that gates the source, keeping all overlapping events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(maskAll("x ~ x ~"))  // via mapper
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@SprudelDsl
@KlangScript.Function
fun maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.maskAll(*args, callInfo = callInfo) }

/** Chains a maskAll onto this [PatternMapperFn]; gates the result keeping all overlapping events. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.maskAll(*args, callInfo = callInfo) }

// -- jux() ------------------------------------------------------------------------------------------------------------

private fun applyJux(source: SprudelPattern, transform: PatternMapperFn): SprudelPattern {
    // Pan is unipolar (0.0 to 1.0).
    // jux pans original hard left (0.0) and transformed hard right (1.0).
    val left = source.pan(0.0)
    val right = transform(source).pan(1.0)
    return StackPattern(listOf(left, right))
}

/**
 * Pans this pattern hard left and a transformed version hard right.
 *
 * Creates a stereo image by stacking the original panned to 0.0 (left) with `transform(this)` panned
 * to 1.0 (right). Useful for stereo width or call-and-response effects.
 *
 * @param transform Function applied to the right-channel copy of the pattern.
 * @return A stereo pattern with the original on the left and the transformed copy on the right.
 *
 * ```KlangScript(Playable)
 * s("bd sd").jux(x => x.rev())       // reversed pattern panned right
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").jux(x => x.fast(2))  // double-speed version panned right
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.jux(transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyJux(this, transform)

/** Pans this string pattern hard left and a transformed version hard right. */
@SprudelDsl
@KlangScript.Function
fun String.jux(transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).jux(transform, callInfo)

/**
 * Returns a [PatternMapperFn] that pans the source hard left and a transformed version hard right.
 *
 * @param transform Function applied to the right-channel copy of the source pattern.
 * @return A [PatternMapperFn] producing a stereo pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(jux(x => x.rev()))   // via mapper
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@SprudelDsl
@KlangScript.Function
fun jux(transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.jux(transform, callInfo) }

/** Chains a jux onto this [PatternMapperFn]; pans left and transformed-right. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.jux(transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.jux(transform, callInfo) }

// -- juxBy() ----------------------------------------------------------------------------------------------------------

private fun applyJuxBy(source: SprudelPattern, amount: Double, transform: PatternMapperFn): SprudelPattern {
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
 * Like [jux], but with adjustable stereo width.
 *
 * Pans the original left by `0.5 * (1 - amount)` and the transformed copy right by `0.5 * (1 + amount)`.
 * At `amount = 1.0` (full stereo) this is equivalent to [jux]. At `amount = 0.0` both copies are centred.
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan).
 * @param transform Function applied to the right-channel copy.
 * @return A stereo pattern with width controlled by `amount`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").juxBy(0.5, x => x.rev())        // half stereo width, reversed on right
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").juxBy(0.75, x => x.fast(2))  // 75% stereo, faster on right
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyJuxBy(this, amount, transform)

/** Like [jux] with adjustable stereo width on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).juxBy(amount, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that pans the source with adjustable stereo width.
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan).
 * @param transform Function applied to the right-channel copy.
 * @return A [PatternMapperFn] producing a stereo pattern with the given width.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(juxBy(0.5, x => x.rev()))  // via mapper
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@SprudelDsl
@KlangScript.Function
fun juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.juxBy(amount, transform, callInfo) }

/** Chains a juxBy onto this [PatternMapperFn]; pans with adjustable stereo width. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.juxBy(amount, transform, callInfo) }

// -- off() ------------------------------------------------------------------------------------------------------------

private fun applyOff(source: SprudelPattern, time: Double, transform: PatternMapperFn): SprudelPattern {
    val timeRat = time.toRational()
    return source.stack(transform(source).late(timeRat))
}

/**
 * Layers a time-shifted, transformed copy of this pattern on top of itself.
 *
 * Stacks the original with a delayed copy produced by applying [transform]. Useful for creating rhythmic
 * echoes, counterpoint, or call-and-response effects.
 *
 * @param time Time offset in cycles for the delayed copy. Default is 0.25 (quarter cycle).
 * @param transform Function applied to the delayed copy.
 * @return The original pattern stacked with a time-shifted, transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").off(0.125, x => x.gain(0.2))       // quiet echo 1/8 cycle behind
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").off(0.25, x => x.transpose(12)) // octave-up copy a quarter cycle behind
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyOff(this, time, transform)

/** Layers a time-shifted, transformed copy of this string pattern on top of itself. */
@SprudelDsl
@KlangScript.Function
fun String.off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).off(time, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that layers a time-shifted, transformed copy on top of the source.
 *
 * @param time Time offset in cycles for the delayed copy.
 * @param transform Function applied to the delayed copy.
 * @return A [PatternMapperFn] that stacks the source with a late, transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(off(0.125, x => x.gain(0.2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@SprudelDsl
@KlangScript.Function
fun off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.off(time, transform, callInfo) }

/** Chains an off onto this [PatternMapperFn]; layers a time-shifted, transformed copy. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.off(time, transform, callInfo) }

// -- filter() ---------------------------------------------------------------------------------------------------------

private fun applyFilter(source: SprudelPattern, predicate: (SprudelPatternEvent) -> Boolean): SprudelPattern {
    return source.map { events -> events.filter(predicate) }
}

/**
 * Filters events from this pattern using a predicate function.
 *
 * Only events for which [predicate] returns `true` are kept.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A pattern containing only the events that satisfy the predicate.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").filter(x => x.part.begin < 0.5)  // keep first-half events
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").filter(x => x.isOnset)             // keep only onset events
 * ```
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    applyFilter(this, predicate)

/** Filters events from this string pattern using a predicate function. */
@SprudelDsl
@KlangScript.Function
fun String.filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).filter(predicate, callInfo)

/**
 * Returns a [PatternMapperFn] that filters events from the source using a predicate.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A [PatternMapperFn] that keeps only events satisfying the predicate.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(filter(x => x.part.begin < 0.5))  // via mapper
 * ```
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@SprudelDsl
@KlangScript.Function
fun filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.filter(predicate, callInfo) }

/** Chains a filter onto this [PatternMapperFn]; keeps only events satisfying the predicate. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.filter(predicate, callInfo) }

// -- filterWhen() -----------------------------------------------------------------------------------------------------

/**
 * Filters events from this pattern based on their begin time.
 *
 * Only events whose `part.begin` (as `Double`) satisfies [predicate] are kept.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A pattern with only the events whose begin time satisfies the predicate.
 *
 * ```KlangScript(Playable)
 * note("c d e f").filterWhen(t => t < 0.5)       // keep only first-half events
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").filterWhen(t => t % 0.25 == 0) // keep only events on beat boundaries
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    applyFilter(this) { predicate(it.part.begin.toDouble()) }

/** Filters events from this string pattern based on their begin time. */
@SprudelDsl
@KlangScript.Function
fun String.filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).filterWhen(predicate, callInfo)

/**
 * Returns a [PatternMapperFn] that filters events from the source based on their begin time.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A [PatternMapperFn] that filters events by their begin time.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(filterWhen(t => t < 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@SprudelDsl
@KlangScript.Function
fun filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.filterWhen(predicate, callInfo) }

/** Chains a filterWhen onto this [PatternMapperFn]; filters events by their begin time. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.filterWhen(predicate, callInfo) }

// -- superimpose() ----------------------------------------------------------------------------------------------------

private fun applySuperimpose(source: SprudelPattern, transforms: Array<out PatternMapperFn>): SprudelPattern {
    val transformed = transforms.map { it(source) }
    return source.stack(*transformed.toTypedArray())
}

/**
 * Layers one or more transformed copies of this pattern on top of itself.
 *
 * Stacks the original pattern with the result of applying each [transforms] to it.
 * Unlike [off], the copies are not time-shifted — all layers start at the same position.
 *
 * @param transforms Functions applied to produce the additional layers.
 * @return The original pattern stacked with its transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").superimpose(x => x.fast(2))                         // double-speed layer on top
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").superimpose(x => x.transpose(7), x => x.transpose(12))  // fifth and octave stacked
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applySuperimpose(this, transforms)

/** Layers a transformed copy of this string pattern on top of itself. */
@SprudelDsl
@KlangScript.Function
fun String.superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).superimpose(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that layers a transformed copy of the source on top of itself.
 *
 * @param transforms Functions applied to produce the additional layers.
 * @return A [PatternMapperFn] that stacks the source with its transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(superimpose(x => x.fast(2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@SprudelDsl
@KlangScript.Function
fun superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.superimpose(*transforms, callInfo = callInfo) }

/** Chains a superimpose onto this [PatternMapperFn]; layers a transformed copy on top. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.superimpose(*transforms, callInfo = callInfo) }

// -- layer() ----------------------------------------------------------------------------------------------------------

private fun applyLayer(source: SprudelPattern, transforms: Array<out PatternMapperFn>): SprudelPattern {
    if (transforms.isEmpty()) {
        return source // we keep the pattern as is
    }

    val patterns = transforms.map { transform ->
        try {
            transform(source)
        } catch (e: Exception) {
            println("Error applying layer transform: ${e.stackTraceToString()}")
            source
        }
    }

    return if (patterns.size == 1) {
        patterns.first()
    } else {
        StackPattern(patterns)
    }
}

/**
 * Applies one or more transformation functions to this pattern and stacks the results.
 *
 * Each function in [transforms] is applied to the original pattern independently, and all
 * results are stacked together. Useful for building complex textures from a single source.
 *
 * @param transforms One or more functions to apply; each result is stacked with the others.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").layer(x => x.fast(2), x => x.rev())              // two transformed layers stacked
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").layer(x => x.transpose(7), x => x.transpose(12))    // fifth and octave stacked
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyLayer(this, transforms)

/** Applies transformations to this string pattern and stacks the results. */
@SprudelDsl
@KlangScript.Function
fun String.layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).layer(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that applies the given transforms to the source and stacks results.
 *
 * @param transforms One or more functions; each is applied to the source and results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // via mapper
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@SprudelDsl
@KlangScript.Function
fun layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.layer(*transforms, callInfo = callInfo) }

/** Chains a layer onto this [PatternMapperFn]; stacks the transformed copies. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.layer(*transforms, callInfo = callInfo) }

/**
 * Alias for [layer] — applies multiple transformation functions and stacks the results.
 *
 * @param transforms One or more functions to apply; results are stacked.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").apply(x => x.fast(2), x => x.rev())   // two layers stacked
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").apply(x => x.transpose(7))                  // fifth layer stacked
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.layer(*transforms, callInfo = callInfo)

/** Alias for [layer] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).apply(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [layer] — that applies transforms and stacks results.
 *
 * @param transforms One or more functions; results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // apply the layer mapper
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@SprudelDsl
@KlangScript.Function
fun apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.apply(*transforms, callInfo = callInfo) }

/** Chains an apply (alias for [layer]) onto this [PatternMapperFn]; stacks the transformed copies. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.apply(*transforms, callInfo = callInfo) }

// -- zoom() -----------------------------------------------------------------------------------------------------------

private fun applyZoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return source
    }

    // We convert both arguments to patterns to support dynamic zoom (e.g. zoom("<0 0.5>", "<0.5 1>"))
    val startCtrl = args[0].toPattern()
    val endCtrl = args[1].toPattern()

    // Bind the start pattern...
    return startCtrl._bind { startEv ->
        val s = startEv.data.value?.asRational ?: return@_bind null

        // ... then bind the end pattern
        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asRational ?: return@_bind null

            if (s >= e) return@_bind silence

            val d = e - s
            val steps = source.numSteps?.let { it * d }

            // Using relative start to ensure correct periodicity even if s > 1
            val sRel = s - s.floor()

            // Match JS implementation: withQuerySpan + withHapSpan + splitQueries
            source
                // Apply transformation to cycle-local time: t => t * d + sRel
                ._withQuerySpan { span -> span.withCycle { t -> t * d + sRel } }
                // Apply transformation to cycle-local time: t => (t - sRel) / d
                ._withHapSpan { span -> span.withCycle { t: Rational -> (t - sRel) / d } }
                ._splitQueries()
                .withSteps(steps)
        }
    }
}

/**
 * Plays a portion of this pattern within a time window, stretching it to fill a full cycle.
 *
 * The window `[start, end]` is zoomed in on — events within that portion are stretched to fill the cycle.
 * Both `start` and `end` can be pattern strings for dynamic zooming (e.g. `"<0 0.25>"`).
 *
 * @param start Start of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @param end End of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @return The zoomed portion of the pattern stretched to one full cycle.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd hh").zoom(0.0, 0.5)   // plays only first half, stretched to full cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").zoom(0.25, 0.75)  // plays middle two notes, stretched to full cycle
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyZoom(this, listOf(start, end).asSprudelDslArgs(callInfo))

/** Plays a portion of this string pattern within a time window, stretched to fill a cycle. */
@SprudelDsl
@KlangScript.Function
fun String.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).zoom(start, end, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a portion of the source, stretching it to fill a cycle.
 *
 * @param start Start of the zoom window (0.0 to 1.0).
 * @param end End of the zoom window (0.0 to 1.0).
 * @return A [PatternMapperFn] that zooms the source into the given window.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd hh").apply(zoom(0.0, 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@SprudelDsl
@KlangScript.Function
fun zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.zoom(start, end, callInfo) }

/** Chains a zoom onto this [PatternMapperFn]; plays a window of the result stretched to one cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.zoom(start, end, callInfo) }

// -- within() ---------------------------------------------------------------------------------------------------------

private fun applyWithin(
    source: SprudelPattern,
    startVal: Double,
    endVal: Double,
    transform: PatternMapperFn,
): SprudelPattern {
    val start = startVal.toRational()
    val end = endVal.toRational()

    if (start >= end || start < Rational.ZERO || end > Rational.ONE) {
        return source // Return unchanged if invalid window
    }

    val isBeginInWindow: (SprudelPatternEvent) -> Boolean = { ev ->
        val cycle = ev.part.begin.floor()
        if (start < end) {
            val s = cycle + start
            val e = cycle + end
            ev.part.begin >= s && ev.part.begin < e
        } else {
            val s1 = cycle + start
            val e1 = cycle + Rational.ONE
            val s2 = cycle
            val e2 = cycle + end
            (ev.part.begin >= s1 && ev.part.begin < e1) || (ev.part.begin >= s2 && ev.part.begin < e2)
        }
    }

    val inside = source.filter(isBeginInWindow)
    val outsidePredicate: (SprudelPatternEvent) -> Boolean = { !isBeginInWindow(it) }
    val outside = source.filter(outsidePredicate)

    return StackPattern(listOf(transform(inside), outside))
}

/**
 * Applies a transformation to the portion of the pattern that falls within a time window.
 *
 * Events inside `[start, end)` are extracted, transformed, then stacked back with the untouched events
 * outside the window. The window bounds must satisfy `0.0 <= start < end <= 1.0`.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").within(0.0, 0.5, x => x.fast(2))  // double-speed in first half
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").within(0.25, 0.75, x => x.transpose(12))  // octave up in the middle
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): SprudelPattern = applyWithin(this, start, end, transform)

/**
 * Applies a transformation to the portion of this string-parsed pattern that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".within(0.0, 0.5, x => x.fast(2)).s()  // double-speed in first half
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
@KlangScript.Function
fun String.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).within(start, end, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a transformation to the portion of the source pattern
 * that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return A [PatternMapperFn] that applies `transform` to events in `[start, end)`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(within(0.0, 0.5, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
@KlangScript.Function
fun within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): PatternMapperFn = { p -> p.within(start, end, transform, callInfo) }

/** Chains a within onto this [PatternMapperFn]; applies `transform` to events in the time window of the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): PatternMapperFn = this.chain { p -> p.within(start, end, transform, callInfo) }

// -- chunk() ----------------------------------------------------------------------------------------------------------

internal fun applyChunk(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val n = nArg.value?.asIntOrNull() ?: 1
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // TODO: support control patterns
    val back = args.getOrNull(2)?.value as? Boolean ?: false
    val fast = args.getOrNull(3)?.value as? Boolean ?: false
    val earlyOffset = args.getOrNull(4)?.value?.asIntOrNull() ?: 0

    if (n <= 0) {
        return silence
    }

    val binary = MutableList(n - 1) { false }
    binary.add(0, true)  // [true, false, false, false] for n=4

    // Construct binary patterns manually to avoid dependency on 'pure' DSL property order
    val binaryPatterns = binary.map {
        AtomicPattern(SprudelVoiceData.empty.copy(value = it.asVoiceValue()))
    }
    val binarySequence = applySeq(binaryPatterns)

    var binaryIter = if (back) {
        applyIter(binarySequence, listOf(nArg))  // forward (default)
    } else {
        applyIterBack(binarySequence, listOf(nArg))  // backward
    }

    if (earlyOffset != 0) {
        binaryIter = binaryIter.early(earlyOffset.toRational())
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
 * Divides the pattern into `n` chunks and cycles through them, applying [transform] to one chunk per cycle.
 *
 * Over `n` cycles the whole pattern plays `n` times, with each cycle highlighting one of the `n` equal
 * segments via [transform]. Use `back = true` to cycle backward, and `fast = true` to let the pattern
 * progress at its natural speed (without the `n`-times repetition).
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated — it runs at natural speed (default `false`).
 * @param transform Function applied to the currently active chunk each cycle.
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunk(4) { it.add(7) }.scale("c:minor").n()  // one chunk transformed per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunk(4) { it.gain(1.5) }  // one hit louder, cycling forward
 * ```
 * @alias slowchunk, slowChunk
 * @category structural
 * @tags chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = applyChunk(this, listOf(n, transform, back, fast).asSprudelDslArgs(callInfo))

/** Like [chunk] applied to a mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.chunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).chunk(n, transform, back, fast, callInfo)

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").slowchunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").slowchunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowChunk
 * @category structural
 * @tags slowchunk, chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.slowchunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.chunk(n, transform, back, fast, callInfo)

/** Alias for [chunk]. */
@SprudelDsl
@KlangScript.Function
fun String.slowchunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).slowchunk(n, transform, back, fast, callInfo)

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").slowChunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").slowChunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowchunk
 * @category structural
 * @tags slowChunk, chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.slowChunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.chunk(n, transform, back, fast, callInfo)

/** Alias for [chunk]. */
@SprudelDsl
@KlangScript.Function
fun String.slowChunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).slowChunk(n, transform, back, fast, callInfo)

// -- chunkBack() / chunkback() ----------------------------------------------------------------------------------------

/**
 * Like [chunk], but cycles through the parts in reverse order (known as `chunk'` in TidalCycles).
 *
 * Divides the pattern into `n` chunks and cycles backward through them — starting from chunk 0,
 * then chunk n-1, n-2, …, 1 — applying [transform] to one chunk per cycle.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern cycling backward through transformed chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunkBack(4, x => x.add(7)).scale("c:minor").n()  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkBack(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkBack(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, true, false).asSprudelDslArgs(callInfo))

/**
 * Like [chunk] on a string-parsed pattern, but cycles backward through parts.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".chunkBack(4, x => x.gain(0.1)).s()  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 */
@SprudelDsl
@KlangScript.Function
fun String.chunkBack(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkBack(n, transform, callInfo)

/**
 * Alias for [chunkBack] — cycles backward through transformed chunks.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern cycling backward through transformed chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunkback(4, x => x.add(7))  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkback(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkBack
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkback(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkBack(n, transform, callInfo)

/**
 * Alias for [chunkBack] on a string-parsed pattern.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".chunkback(4, x => x.gain(0.8)).s()  // one hit boosted, cycling back
 * ```
 * @alias chunkBack
 */
@SprudelDsl
@KlangScript.Function
fun String.chunkback(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkback(n, transform, callInfo)

// -- fastChunk() / fastchunk() ----------------------------------------------------------------------------------------

/**
 * Like [chunk], but the source pattern plays at natural speed (not repeated `n` times).
 *
 * While [chunk] repeats the source `n` times before cycling through transformations, `fastChunk` lets
 * the pattern progress naturally while the transformed chunk cycles independently.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern at natural speed with chunks cycling through the transformation.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").fastChunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").fastChunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastchunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fastChunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, false, true).asSprudelDslArgs(callInfo))

/**
 * Like [chunk] on a string-parsed pattern but at natural speed.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".fastChunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastchunk
 */
@SprudelDsl
@KlangScript.Function
fun String.fastChunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastChunk(n, transform, callInfo)

/**
 * Alias for [fastChunk] — like [chunk] but pattern plays at natural speed.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern at natural speed with chunks cycling through the transformation.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").fastchunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").fastchunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastChunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fastchunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.fastChunk(n, transform, callInfo)

/**
 * Alias for [fastChunk] on a string-parsed pattern.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".fastchunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastChunk
 */
@SprudelDsl
@KlangScript.Function
fun String.fastchunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastchunk(n, transform, callInfo)

// -- chunkInto() ------------------------------------------------------------------------------------------------------

/**
 * Like [chunk], but applies [transform] to a fast-looped subcycle instead of repeating the pattern `n` times.
 *
 * Equivalent to `fastChunk` — the source pattern plays at its natural speed while the transformed
 * chunk cycles through the `n` parts independently each cycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the active chunk each cycle.
 * @return A pattern at natural speed with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkInto(4, x => x.hurry(2))  // transform cycles, no repeat
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g h").chunkInto(3, x => x.transpose(7))  // 3 chunks, each transposed
 * ```
 * @alias chunkinto
 * @category structural
 * @tags chunkInto, chunk, cycle, transform, fast, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, false, true).asSprudelDslArgs(callInfo))

/** Like [chunkInto] applied to a mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.chunkInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkInto(n, transform, callInfo)

/**
 * Alias for [chunkInto] — applies [transform] to a fast-looped subcycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the active chunk each cycle.
 * @return A pattern at natural speed with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkinto(4, x => x.hurry(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").chunkinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkInto
 * @category structural
 * @tags chunkinto, chunkInto, chunk, cycle, transform, fast, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkInto(n, transform, callInfo)

/** Alias for [chunkInto]. */
@SprudelDsl
@KlangScript.Function
fun String.chunkinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkinto(n, transform, callInfo)

// -- chunkBackInto() --------------------------------------------------------------------------------------------------

/**
 * Divides a pattern into `n` chunks and applies a transform to the active chunk, cycling backwards.
 *
 * Like [chunkInto], but advances through chunks in reverse order each cycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkBackInto(4, x => x.hurry(2))  // transform cycles backward
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g h").chunkBackInto(3, x => x.transpose(7))  // 3 chunks, reversed order
 * ```
 * @alias chunkbackinto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkBackInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, true, true, 1).asSprudelDslArgs(callInfo))

/** Like [chunkBackInto] applied to a mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.chunkBackInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkBackInto(n, transform, callInfo)

/**
 * Alias for [chunkBackInto] — divides the pattern into `n` chunks, applying a transform cycling backwards.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkbackinto(4, x => x.hurry(2))  // backwards chunk transform
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").chunkbackinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkBackInto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chunkbackinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkBackInto(n, transform, callInfo)

/** Alias for [chunkBackInto]. */
@SprudelDsl
@KlangScript.Function
fun String.chunkbackinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkbackinto(n, transform, callInfo)

// -- linger() ---------------------------------------------------------------------------------------------------------

private fun applyLinger(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val tArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(tArg) { src, tVal ->
        val t = tVal?.asRational ?: return@_innerJoin src

        when {
            t == Rational.ZERO -> silence
            t < Rational.ZERO -> {
                // Negative: zoom from (t+1) to 1, then slow by t (which is negative)
                src.zoom(t + Rational.ONE, Rational.ONE).slow(-t).timeLoop(Rational.ONE)
            }

            else -> {
                // Positive: zoom from 0 to t, then slow by t
                src.zoom(Rational.ZERO, t).slow(t).timeLoop(Rational.ONE)
            }
        }
    }
}

/**
 * Selects the given fraction of the pattern and repeats that part to fill the remainder of the cycle.
 *
 * - `linger(0.5)`: Takes first 50% of pattern, repeats it to fill the cycle
 * - `linger(-0.5)`: Takes last 50% of pattern, repeats it to fill the cycle
 * - `linger(0)`: Returns silence
 *
 * The selected portion is slowed down by the fraction amount to fill the full cycle time.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").linger(0.5)  // repeats "bd sd" throughout the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("lt ht mt cp").linger("<1 .5 .25 .125 .0625 .03125>")  // different fraction each cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.linger(t: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLinger(this, listOf(t).asSprudelDslArgs(callInfo))

/**
 * Selects the given fraction of this string-parsed pattern and repeats that part to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".linger(0.5).s()  // repeats "bd sd" throughout the cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
@KlangScript.Function
fun String.linger(t: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).linger(t, callInfo)

/**
 * Returns a [PatternMapperFn] that selects the given fraction of the source pattern and repeats it to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A [PatternMapperFn] that lingers on the selected fraction of the source.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").apply(linger(0.5))  // via mapper
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
@KlangScript.Function
fun linger(t: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.linger(t, callInfo) }

/** Chains a linger onto this [PatternMapperFn]; repeats the selected fraction of the result to fill the cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.linger(t: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.linger(t, callInfo) }

// -- echo() / stut() --------------------------------------------------------------------------------------------------

private fun applyEcho(source: SprudelPattern, times: Int, delay: Rational, decay: Rational): SprudelPattern {
    if (times < 1) return silence
    if (times == 1) return source // Only original, no echoes

    // Create layers: original + echoes
    val layers = (0 until times).map { i ->
        if (i == 0) {
            source // Original (no delay, no gain change)
        } else {
            // Delayed and decayed echo
            val gainMultiplier = decay.pow(i)
            source.late(delay * i).gain(gainMultiplier)
        }
    }

    return StackPattern(layers)
}

/**
 * Superimposes delayed and decayed copies of the pattern, creating an echo effect.
 *
 * Each copy is delayed by `delay × copy_number` cycles and its gain reduced by `decay ^ copy_number`.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * s("bd sd").echo(3, 0.125, 0.7)  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").echo(4, 0.25, 0.5)  // 4 layers, quarter-cycle spacing, halving gain
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    applyEcho(this, times, delay.toRational(), decay.toRational())

/**
 * Like [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * "bd sd".echo(3, 0.125, 0.7).s()  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun String.echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echo(times, delay, decay, callInfo)

/**
 * Returns a [PatternMapperFn] that superimposes delayed and decayed copies of the source pattern.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(echo(3, 0.125, 0.7))  // via mapper
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.echo(times, delay, decay, callInfo) }

/** Chains an echo onto this [PatternMapperFn]; superimposes delayed and decayed copies of the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.echo(times, delay, decay, callInfo) }

/**
 * Alias for [echo] — superimposes delayed and decayed copies of the pattern.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * n("0").stut(4, 0.5, 0.5)  // 4 echoes, half-cycle spacing, halving gain
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").stut(3, 0.125, 0.8)  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.echo(times, delay, decay, callInfo)

/**
 * Alias for [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * "hh".stut(3, 0.125, 0.8).s()  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun String.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stut(times, delay, decay, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [echo] — superimposes delayed and decayed copies.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(stut(3, 0.125, 0.8))  // via mapper
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
@KlangScript.Function
fun stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.stut(times, delay, decay, callInfo) }

/** Chains a stut onto this [PatternMapperFn]; alias for [PatternMapperFn.echo]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.stut(times, delay, decay, callInfo) }

// -- echoWith() / stutWith() ------------------------------------------------------------------------------------------

private fun applyEchoWith(source: SprudelPattern, times: Int, delay: Rational, transform: PatternMapperFn): SprudelPattern {
    if (times <= 0) return silence
    if (times == 1) return source // Only original, no additional layers

    // Build layers with cumulative transformation
    val layers = mutableListOf(source) // Layer 0: original
    var current = source

    repeat(times - 1) { i ->
        // Apply transform cumulatively
        current = transform(current)
        // Delay this layer
        layers.add(current.late(delay * (i + 1)))
    }

    return StackPattern(layers)
}

/**
 * Superimposes versions of the pattern with a transform applied cumulatively to each layer.
 *
 * Unlike [echo], which simply decays gain, each layer receives the transform applied once more than
 * the previous layer, creating a compounding effect.
 *
 * @param times     Number of layers including the original (must be ≥ 1).
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").echoWith(4, 0.125, x => x.add(2))  // layers at n=0,2,4,6, each 0.125 apart
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").echoWith(3, 0.25, x => x.fast(2))  // original + 2× and 4× faster copies
 * ```
 * @alias stutWith, stutwith, echowith
 * @category structural
 * @tags echoWith, stutWith, delay, transform, layers, effect
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.echoWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyEchoWith(this, times, delay.toRational(), transform)

/** Like [echoWith] applied to a mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.echoWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echoWith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").stutWith(4, 0.125, x => x.add(2))  // stut alias with additive transform
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").stutWith(3, 0.25, x => x.gain(0.7))  // quieter copies
 * ```
 * @alias echoWith, stutwith, echowith
 * @category structural
 * @tags stutWith, echoWith, delay, transform, layers, effect
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stutWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@SprudelDsl
@KlangScript.Function
fun String.stutWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stutWith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").stutwith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").stutwith(3, 0.25, x => x.speed(1.5))  // each copy 50% faster
 * ```
 * @alias echoWith, stutWith, echowith
 * @category structural
 * @tags stutwith, echoWith, stutWith, delay, transform, layers
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stutwith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@SprudelDsl
@KlangScript.Function
fun String.stutwith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stutwith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").echowith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").echowith(3, 0.5, x => x.rev())  // each copy reversed
 * ```
 * @alias echoWith, stutWith, stutwith
 * @category structural
 * @tags echowith, echoWith, stutWith, delay, transform, layers
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.echowith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@SprudelDsl
@KlangScript.Function
fun String.echowith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echowith(times, delay, transform, callInfo)

// -- bite() -----------------------------------------------------------------------------------------------------------

private fun applyBite(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return silence

    val nPattern = args.take(1).toPattern()
    val indicesPattern = args.drop(1).toPattern()
    val indicesSteps: Rational = indicesPattern.numSteps ?: Rational.ONE

    return source._innerJoin(nPattern, indicesPattern) { src, nValue, indexValue ->
        val steps: Rational =
            src.numSteps ?: return@_innerJoin silence
        val n: Rational =
            nValue?.asRational?.takeIf { it > Rational.ZERO } ?: return@_innerJoin silence
        val index: Rational =
            indexValue?.asRational ?: return@_innerJoin silence
        val indexMod: Rational =
            ((index % steps) + steps) % steps

        val start = indexMod / n
        val end = (indexMod + 1.0) / n

        src.zoom(start, end).fast(indicesSteps)
    }
}

/**
 * Splits the pattern into `n` equal slices and plays them in the order given by `indices`.
 *
 * Each event in the `indices` pattern selects a slice by number and plays it scaled to fit that event's
 * duration. This allows reordering, repeating, or otherwise rearranging slices of any pattern.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A new pattern built by playing slices in the order specified by `indices`.
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").bite(4, "3 2 1 0").scale("c3:major")  // reverse the pattern
 * ```
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").bite(4, "0!2 1").scale("c3:major")  // play slice 0 twice then slice 1
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyBite(this, listOf(n, indices).asSprudelDslArgs(callInfo))

/**
 * Like [bite] applied to a mini-notation string.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A new pattern built by playing slices in the order specified by `indices`.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3".bite(4, "3 2 1 0").n().scale("c3:major")  // reverse the pattern
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
@KlangScript.Function
fun String.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bite(n, indices, callInfo)

/**
 * Returns a [PatternMapperFn] that splits the source into `n` equal slices and plays them in `indices` order.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A [PatternMapperFn] that rearranges slices of the source pattern.
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").apply(bite(4, "3 2 1 0")).scale("c3:major")  // via mapper
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
@KlangScript.Function
fun bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bite(n, indices, callInfo) }

/** Chains a bite onto this [PatternMapperFn]; rearranges slices of the result in `indices` order. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bite(n, indices, callInfo) }

// -- segment() --------------------------------------------------------------------------------------------------------

private fun applySegment(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.firstOrNull()

    val nPattern: SprudelPattern = when (val nVal = nArg?.value) {
        is SprudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: SprudelDslArg.of("1")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use original implementation with struct + fast
        val structPat = parseMiniNotation("x") { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }

        source.struct(structPat.fast(staticN))
    } else {
        // Dynamic path: use SegmentPattern which properly slices each timespan
        SegmentPattern.control(source, nPattern)
    }
}

/**
 * Samples the pattern at a rate of `n` events per cycle.
 *
 * Useful for turning a continuous pattern (e.g. from a signal) into a discrete stepped one.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * note(saw.range(40, 52).segment(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript(Playable)
 * note(sine.range(48, 60).segment(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.segment(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySegment(this, listOf(n).asSprudelDslArgs(callInfo))

/**
 * Like [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * "0".segment(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun String.segment(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).segment(n, callInfo)

/**
 * Returns a [PatternMapperFn] that samples the source pattern at `n` events per cycle.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript(Playable)
 * sine.range(40, 60).apply(segment(8)).note()  // via mapper
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun segment(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.segment(n, callInfo) }

/** Chains a segment onto this [PatternMapperFn]; samples the result at `n` events per cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.segment(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.segment(n, callInfo) }

/**
 * Alias for [segment] — samples the pattern at a rate of `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * note(saw.range(40, 52).seg(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript(Playable)
 * note(sine.range(48, 60).seg(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.seg(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.segment(n, callInfo)

/**
 * Alias for [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * "0".seg(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun String.seg(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).seg(n, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [segment] — samples the source at `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript(Playable)
 * sine.range(40, 60).apply(seg(8)).note()  // via mapper
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
@KlangScript.Function
fun seg(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.seg(n, callInfo) }

/** Chains a seg onto this [PatternMapperFn]; alias for [PatternMapperFn.segment]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.seg(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.seg(n, callInfo) }

// -- run() ------------------------------------------------------------------------------------------------------------

private fun applyRun(n: Int): SprudelPattern {
    // TODO: support control pattern

    if (n <= 0) return silence
    // "0 1 2 ... n-1"
    // equivalent to saw.range(0, n).round().segment(n) in JS
    // But we can just create a sequence directly.
    val items = (0 until n).map {
        AtomicPattern(SprudelVoiceData.empty.copy(value = it.asVoiceValue()))
    }

    return SequencePattern(items)
}

/**
 * Creates a discrete pattern of integers from 0 to `n - 1`.
 *
 * Equivalent to `n("0 1 2 … n-1")`. Useful for driving scale or sample index patterns.
 *
 * @param n Number of steps; the pattern produces values 0, 1, …, n-1.
 * @return A sequential pattern of integers from 0 to n-1.
 *
 * ```KlangScript(Playable)
 * n(run(4)).scale("C4:pentatonic")  // 4 scale degrees per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * n(run(8)).s("piano")  // 8 sequential notes
 * ```
 * @category structural
 * @tags run, sequence, range, index, discrete
 */
@SprudelDsl
@KlangScript.Function
fun run(n: Int, callInfo: CallInfo? = null): SprudelPattern = applyRun(n)

// -- binaryN() --------------------------------------------------------------------------------------------------------

private fun applyBinaryN(n: Int, bits: Int): SprudelPattern {
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
        AtomicPattern(SprudelVoiceData.empty.copy(value = bit.asVoiceValue()))
    }
    return SequencePattern(items)
}

/**
 * Creates a binary pattern from a number, padded to `bits` bits long (MSB first).
 *
 * @param n    The integer to convert to binary.
 * @param bits Total pattern length in bits (default 16).
 * @return A sequential pattern of 0s and 1s representing the binary value.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryN(9, 4))  // 1 0 0 1 — binary 9 in 4 bits
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryN(146, 8))  // 1 0 0 1 0 0 1 0 - binary 146 in 8 bits
 * ```
 * @category structural
 * @tags binaryN, binary, bits, structure, pattern
 */
@SprudelDsl
@KlangScript.Function
fun binaryN(n: Int, bits: Int = 16, callInfo: CallInfo? = null): SprudelPattern = applyBinaryN(n, bits)

// -- binary() ---------------------------------------------------------------------------------------------------------

/**
 * Creates a binary pattern from a number, with bit length calculated automatically.
 *
 * @param n The integer to convert to binary.
 * @return A sequential pattern of 0s and 1s with minimal bit width.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binary(5))  // 1 0 1 — 3 bits
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binary(13))  // 1 1 0 1 — 4 bits
 * ```
 * @category structural
 * @tags binary, binaryN, bits, structure, pattern
 */
@SprudelDsl
@KlangScript.Function
fun binary(n: Int, callInfo: CallInfo? = null): SprudelPattern {
    return if (n == 0) {
        applyBinaryN(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryN(n, bits)
    }
}

// -- binaryNL() -------------------------------------------------------------------------------------------------------

private fun applyBinaryNL(n: Int, bits: Int): SprudelPattern {
    if (bits <= 0) return silence

    val bitList = (0 until bits).mapNotNull { i ->
        // Shift: bits - 1 - i (MSB first)
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        bit.asVoiceValue()
    }

    // Returns a single event containing the list of bits as a Seq value
    return AtomicPattern(
        SprudelVoiceData.empty.copy(value = SprudelVoiceValue.Seq(bitList))
    )
}

/**
 * Creates a binary list pattern from a number, padded to `bits` bits long.
 *
 * Like [binaryN] but returns the bits as a single event containing a list value rather than
 * a sequence of discrete events.
 *
 * @param n    The integer to convert to binary.
 * @param bits Total length in bits (default 16).
 * @return A single-event pattern whose value is a list of 0s and 1s.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryNL(5, 4))  // list [0, 1, 0, 1]
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryNL(255, 8))  // list [1, 1, 1, 1, 1, 1, 1, 1]
 * ```
 * @category structural
 * @tags binaryNL, binary, bits, list, structure
 */
@SprudelDsl
@KlangScript.Function
fun binaryNL(n: Int, bits: Int = 16, callInfo: CallInfo? = null): SprudelPattern = applyBinaryNL(n, bits)

// -- binaryL() --------------------------------------------------------------------------------------------------------

/**
 * Creates a binary list pattern from a number, with bit length calculated automatically.
 *
 * Like [binary] but returns bits as a list value in a single event.
 *
 * @param n The integer to convert to binary.
 * @return A single-event pattern whose value is a list of 0s and 1s with minimal bit width.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryL(5))  // list [1, 0, 1]
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryL(13))  // list [1, 1, 0, 1]
 * ```
 * @category structural
 * @tags binaryL, binaryNL, binary, bits, list, structure
 */
@SprudelDsl
@KlangScript.Function
fun binaryL(n: Int, callInfo: CallInfo? = null): SprudelPattern {
    return if (n == 0) {
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
        // Guard: if any divisor is zero, the fold produces NaN/Infinity → takeIf discards it
        val numbers = parts.mapNotNull { it.toDoubleOrNull() }
        if (numbers.isNotEmpty()) {
            numbers.drop(1).fold(numbers[0]) { acc, divisor -> acc / divisor }.takeIf { it.isFinite() }
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
 * Parses colon-separated ratios into numbers: `"5:4"` → 1.25, `"3:2"` → 1.5, `"12:3:2"` → 2.0.
 *
 * Useful for specifying tuning ratios or rhythmic proportions using familiar ratio notation.
 *
 * @param values One or more ratio strings or numbers to convert.
 * @return A pattern of the computed ratio values.
 *
 * ```KlangScript(Playable)
 * ratio("5:4", "3:2", "2:1").note()  // major third, fifth, octave as ratios
 * ```
 *
 * ```KlangScript(Playable)
 * ratio("3:2").note()  // perfect fifth ratio
 * ```
 * @category structural
 * @tags ratio, tuning, fraction, colon, notation
 */
@SprudelDsl
@KlangScript.Function
fun ratio(vararg values: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    values.toList().asSprudelDslArgs(callInfo).toPattern(ratioMutation)

/** Converts colon-ratio notation in the pattern's values to numbers. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ratio(callInfo: CallInfo? = null): SprudelPattern =
    this.reinterpretVoice { it.ratioMutation(it.value?.asString) }

/** Converts colon-ratio notation in the mini-notation string to numbers. */
@SprudelDsl
@KlangScript.Function
fun String.ratio(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ratio(callInfo)

// -- pace() / steps() -------------------------------------------------------------------------------------------------

private fun applyPace(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asRationalOrNull() ?: Rational.ONE
    val currentSteps = source.numSteps ?: Rational.ONE

    if (targetSteps <= Rational.ZERO || currentSteps <= Rational.ZERO) {
        return source
    }

    // Calculate speed adjustment: fast(targetSteps / currentSteps)
    val speedFactor = targetSteps / currentSteps

    return source.fast(speedFactor)
}

/**
 * Adjusts this pattern's speed so it plays exactly `n` steps per cycle.
 *
 * Computes the speed factor relative to the pattern's natural step count and applies [fast].
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").pace(8)   // 4-step pattern runs at 8 steps/cycle (double speed)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g").pace(4) // 5-step pattern runs at 4 steps/cycle
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pace(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPace(this, listOf(n).asSprudelDslArgs(callInfo))

/** Adjusts this string pattern to play `n` steps per cycle. */
@SprudelDsl
@KlangScript.Function
fun String.pace(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pace(n, callInfo)

/**
 * Returns a [PatternMapperFn] that adjusts the source to play `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return A [PatternMapperFn] that speeds up or slows down the source to fit `n` steps.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(pace(8))   // via mapper
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@SprudelDsl
@KlangScript.Function
fun pace(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pace(n, callInfo) }

/** Chains a pace onto this [PatternMapperFn]; adjusts to play `n` steps per cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pace(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pace(n, callInfo) }

/**
 * Alias for [pace] — adjusts this pattern's speed so it plays `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").steps(8)   // 4-step pattern runs at 8 steps/cycle
 * ```
 *
 * @alias pace
 * @category structural
 * @tags steps, pace, tempo, speed, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.steps(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.pace(n, callInfo)

/** Alias for [pace] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.steps(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).steps(n, callInfo)

/** Returns a [PatternMapperFn] — alias for [pace] — that adjusts to play `n` steps per cycle. */
@SprudelDsl
@KlangScript.Function
fun steps(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.steps(n, callInfo) }

/** Chains a steps (alias for [pace]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.steps(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.steps(n, callInfo) }

// -- take() -----------------------------------------------------------------------------------------------------------

private fun applyTake(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val takeArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = takeArg.asControlValueProvider(Rational.ONE.asVoiceValue())

    val takePattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(SprudelVoiceData.empty.copy(value = control.value))
        is ControlValueProvider.Pattern -> control.pattern
    }

    return takePattern._stepJoin { event ->
        val n = event.data.value?.asRational ?: return@_stepJoin null
        val steps = source.numSteps

        if (steps != null && steps > Rational.ZERO) {
            val end = n / steps

            if (end <= Rational.ZERO) return@_stepJoin silence
            if (end >= Rational.ONE) return@_stepJoin source
            // Take(n) keeps first n steps.
            // Zoom window [0, end] to [0, 1]
            source
                ._withQueryTime { t -> t * end }
                ._withHapTime { t -> t / end }
                .withSteps(n)
        } else {
            silence
        }
    }
}

/**
 * Keeps only the first `n` steps of this pattern, stretched to fill the cycle.
 *
 * @param n Number of steps to keep from the start.
 * @return A pattern containing only the first `n` steps, stretched to fill one cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").take(2)  // keeps "c d", stretched to fill the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").take(3)  // keeps first 3 sounds
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.take(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTake(this, listOf(n).asSprudelDslArgs(callInfo))

/** Keeps the first `n` steps of this string pattern, stretched to fill the cycle. */
@SprudelDsl
@KlangScript.Function
fun String.take(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).take(n, callInfo)

/**
 * Returns a [PatternMapperFn] that keeps only the first `n` steps of the source.
 *
 * @param n Number of steps to keep from the start.
 * @return A [PatternMapperFn] that truncates the source to `n` steps.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(take(2))  // via mapper
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@SprudelDsl
@KlangScript.Function
fun take(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.take(n, callInfo) }

/** Chains a take onto this [PatternMapperFn]; keeps only the first `n` steps. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.take(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.take(n, callInfo) }

// -- drop() -----------------------------------------------------------------------------------------------------------

private fun applyDrop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val dropArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = dropArg.asControlValueProvider(Rational.ZERO.asVoiceValue())

    val dropPattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(SprudelVoiceData.empty.copy(value = control.value))
        is ControlValueProvider.Pattern -> control.pattern
    }

    return dropPattern._stepJoin { event ->
        val n = event.data.value?.asRational ?: return@_stepJoin null
        val steps = source.numSteps

        if (steps != null && steps > Rational.ZERO) {
            if (n > Rational.ZERO) {
                // drop from start: zoom(n/steps, 1)
                val start = n / steps
                if (start >= Rational.ONE) return@_stepJoin silence
                // Zoom window [start, 1] to [0, 1]
                // Map query t in [0, 1] to [start, 1] -> t' = start + t * (1 - start)
                val duration = Rational.ONE - start

                source
                    ._withQueryTime { t -> start + t * duration }
                    ._withHapTime { t -> (t - start) / duration }
                    .withSteps(steps - n)
            } else {
                // drop from end: zoom(0, (steps+n)/steps)
                // n is negative
                val end = (steps + n) / steps
                if (end <= Rational.ZERO) return@_stepJoin silence
                // Zoom window [0, end] to [0, 1]
                // Map query t in [0, 1] to [0, end] -> t' = t * end
                source
                    ._withQueryTime { t -> t * end }
                    ._withHapTime { t -> t / end }
                    .withSteps(steps + n)
            }
        } else {
            silence
        }
    }
}

/**
 * Skips the first `n` steps of this pattern and stretches the remainder to fill the cycle.
 *
 * Use a negative `n` to drop from the end instead.
 *
 * @param n Number of steps to skip from the start (negative = skip from end).
 * @return A pattern with the first `n` steps removed, stretched to fill one cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").drop(1)  // drops "c", plays "d e f" stretched
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").drop(2)  // drops "bd sd", plays "hh cp" stretched
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.drop(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDrop(this, listOf(n).asSprudelDslArgs(callInfo))

/** Skips the first `n` steps of this string pattern, stretched to fill the cycle. */
@SprudelDsl
@KlangScript.Function
fun String.drop(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).drop(n, callInfo)

/**
 * Returns a [PatternMapperFn] that skips the first `n` steps of the source.
 *
 * @param n Number of steps to skip from the start.
 * @return A [PatternMapperFn] that drops the first `n` steps of the source.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(drop(1))  // via mapper
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@SprudelDsl
@KlangScript.Function
fun drop(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.drop(n, callInfo) }

/** Chains a drop onto this [PatternMapperFn]; skips the first `n` steps. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.drop(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.drop(n, callInfo) }

// -- repeatCycles() ---------------------------------------------------------------------------------------------------

private fun applyRepeatCycles(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val repsArg = args.firstOrNull()
    val repsVal = repsArg?.value

    val repsPattern: SprudelPattern = when (repsVal) {
        is SprudelPattern -> repsVal
        else -> parseMiniNotation(repsArg ?: SprudelDslArg.of("1")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticReps = repsVal?.asRationalOrNull()

    return if (staticReps != null) {
        RepeatCyclesPattern(source, staticReps)
    } else {
        RepeatCyclesPattern.control(source, repsPattern)
    }
}

/**
 * Repeats each cycle of this pattern `n` times before advancing.
 *
 * Cycle 0 plays `n` times, then cycle 1 plays `n` times, and so on. Supports control patterns.
 *
 * @param n Number of times to repeat each cycle.
 * @return A pattern where each cycle is repeated `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").repeatCycles(3)    // each cycle repeats 3 times
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").repeatCycles("<1 2 4>") // varying repetitions each cycle
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRepeatCycles(this, listOf(n).asSprudelDslArgs(callInfo))

/** Repeats each cycle of this string pattern `n` times. */
@SprudelDsl
@KlangScript.Function
fun String.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).repeatCycles(n, callInfo)

/**
 * Returns a [PatternMapperFn] that repeats each cycle of the source `n` times.
 *
 * @param n Number of times to repeat each cycle.
 * @return A [PatternMapperFn] that repeats cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(repeatCycles(3))  // via mapper
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@SprudelDsl
@KlangScript.Function
fun repeatCycles(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.repeatCycles(n, callInfo) }

/** Chains a repeatCycles onto this [PatternMapperFn]; repeats each cycle `n` times. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.repeatCycles(n, callInfo) }

// -- extend() ---------------------------------------------------------------------------------------------------------

/**
 * Speeds up this pattern by the given factor — alias for [fast].
 *
 * `extend(2)` is identical to `fast(2)`: events play twice as fast.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript(Playable)
 * note("c d e f").extend(2)      // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").extend("<1 2 4>") // varying speed each cycle
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.extend(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFast(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Speeds up this string pattern by `factor` — alias for [fast]. */
@SprudelDsl
@KlangScript.Function
fun String.extend(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).extend(factor, callInfo)

/**
 * Returns a [PatternMapperFn] that speeds up the source by `factor` — alias for [fast].
 *
 * @param factor Speed-up factor.
 * @return A [PatternMapperFn] that speeds up the source.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(extend(2))  // via mapper
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@SprudelDsl
@KlangScript.Function
fun extend(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.extend(factor, callInfo) }

/** Chains an extend (alias for [fast]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.extend(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.extend(factor, callInfo) }

// -- iter() -----------------------------------------------------------------------------------------------------------

internal fun applyIter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * Divides this pattern into `n` slices and shifts the view forward by one slice each cycle.
 *
 * Each cycle `c` starts at offset `(c % n) / n`, creating a rotating effect.
 * Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @return A pattern that rotates forward by one slice each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").iter(4)  // cycle 0: c d e f, cycle 1: d e f c, …
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").iter(4)  // rotating drum pattern every cycle
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.iter(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyIter(this, listOf(n).asSprudelDslArgs(callInfo))

/** Rotates this string pattern forward by one slice each cycle, dividing into `n` slices. */
@SprudelDsl
@KlangScript.Function
fun String.iter(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iter(n, callInfo)

/**
 * Returns a [PatternMapperFn] that rotates the source forward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source forward each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(iter(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@SprudelDsl
@KlangScript.Function
fun iter(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.iter(n, callInfo) }

/** Chains an iter onto this [PatternMapperFn]; rotates forward by one slice per cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.iter(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iter(n, callInfo) }

// -- iterBack() -------------------------------------------------------------------------------------------------------

internal fun applyIterBack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * Divides this pattern into `n` slices and shifts the view backward by one slice each cycle.
 *
 * Like [iter] but in the opposite direction: each cycle starts later within the pattern.
 * Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @return A pattern that rotates backward by one slice each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").iterBack(4)  // cycle 0: c d e f, cycle 1: f c d e, …
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").iterBack(4)  // backward-rotating drum pattern
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.iterBack(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyIterBack(this, listOf(n).asSprudelDslArgs(callInfo))

/** Rotates this string pattern backward by one slice each cycle, dividing into `n` slices. */
@SprudelDsl
@KlangScript.Function
fun String.iterBack(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iterBack(n, callInfo)

/**
 * Returns a [PatternMapperFn] that rotates the source backward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source backward each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(iterBack(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@SprudelDsl
@KlangScript.Function
fun iterBack(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.iterBack(n, callInfo) }

/** Chains an iterBack onto this [PatternMapperFn]; rotates backward by one slice per cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.iterBack(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iterBack(n, callInfo) }

// -- invert() / inv() ------------------------------------------------------------------------------------------------

/**
 * Inverts boolean values in a pattern: true <-> false, 1 <-> 0.
 * Useful for inverting structural patterns and masks.
 *
 * JavaScript: `pat.fmap((x) => !x)`
 */
private fun applyInvert(pattern: SprudelPattern): SprudelPattern {
    return pattern.mapEvents { event ->
        val currentBool = event.data.value?.asBoolean ?: false
        val invertedBool = !currentBool
        event.copy(data = event.data.copy(value = SprudelVoiceValue.Bool(invertedBool)))
    }
}

/**
 * Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * Useful for flipping structural masks so that silent beats become active and vice-versa.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript(Playable)
 * "1 0 1 1".invert()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").struct("1 0 1 0").invert() // swaps active and silent beats
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.invert(callInfo: CallInfo? = null): SprudelPattern = applyInvert(this)

/** Inverts boolean values in this string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.invert(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).invert(callInfo)

/**
 * Returns a [PatternMapperFn] that inverts boolean values in the source pattern.
 *
 * @return A [PatternMapperFn] that toggles all boolean values.
 *
 * ```KlangScript(Playable)
 * seq("1 0 1 1").apply(invert())  // via mapper
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
@KlangScript.Function
fun invert(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.invert(callInfo) }

/** Chains an invert onto this [PatternMapperFn]; toggles all boolean values. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.invert(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.invert(callInfo) }

/**
 * Alias for [invert]. Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript(Playable)
 * "1 0 1 1".inv()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").struct("1 0 1 0").inv() // swaps active and silent beats
 * ```
 *
 * @alias invert
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.inv(callInfo: CallInfo? = null): SprudelPattern = this.invert(callInfo)

/** Alias for [invert] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.inv(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inv(callInfo)

/** Returns a [PatternMapperFn] — alias for [invert] — that toggles all boolean values. */
@SprudelDsl
@KlangScript.Function
fun inv(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.inv(callInfo) }

/** Chains an inv (alias for [invert]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.inv(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.inv(callInfo) }

// -- applyN() --------------------------------------------------------------------------------------------------------

/**
 * Applies a function to a pattern n times sequentially.
 * Supports control patterns for n.
 *
 * Example: `pattern.applyN(3, x => x.fast(2))` applies fast(2) three times
 *
 * JavaScript: `applyN(n, func, pat)`
 */
private fun applyApplyN(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return pattern

    // Use _innerJoin to support control patterns for n
    return pattern._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 0

        var result = src
        repeat(n) { result = transform(result) }
        result
    }
}

/**
 * Applies a mapper function to this pattern `n` times.
 *
 * Supports control patterns for `n`, so the repetition count can vary each cycle.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly to the pattern.
 * @return A pattern with `transform` applied `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").applyN(2, x => x.fast(2))      // fast(2) applied twice
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").applyN(3, x => x.echo(2, 0.25, 0.5)) // echo applied 3 times
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyApplyN(this, listOf(n, transform).asSprudelDslArgs(callInfo))

/** Applies `transform` to this string pattern `n` times. */
@SprudelDsl
@KlangScript.Function
fun String.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).applyN(n, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies `transform` to the source `n` times.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly.
 * @return A [PatternMapperFn] that applies `transform` `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(applyN(2, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@SprudelDsl
@KlangScript.Function
fun applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.applyN(n, transform, callInfo) }

/** Chains an applyN onto this [PatternMapperFn]; applies `transform` `n` times. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.applyN(n, transform, callInfo) }

// -- pressBy() --------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by compressing events to start at position {r} within their timespan.
 *
 * - r = 0: No compression (normal timing)
 * - r = 0.5: Events start halfway through (syncopated)
 * - r = 1: Events compressed to end
 *
 * JavaScript: `pat.fmap((x) => pure(x).compress(r, 1)).squeezeJoin()`
 * Kotlin: Uses `_bindSqueeze()` with `compress()` (which handles control patterns internally)
 *
 * Example: s("bd mt sd ht").pressBy("<0 0.5 0.25>")
 */
private fun applyPressBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asRational ?: return@_innerJoin src

        src._fmap { value ->
            // Create atomic pattern with the value
            val atomicPattern = AtomicPattern.value(value)
            // Compress to [r, 1] - applyCompress handles control patterns internally
            applyCompress(atomicPattern, listOf(SprudelDslArg.of(r), SprudelDslArg.of(1.0)))
        }._squeezeJoin()
    }
}

/**
 * Syncopates this pattern by compressing each event to start at position `r` within its timespan.
 *
 * - `r = 0`: No compression (normal timing).
 * - `r = 0.5`: Events start halfway through their slot (classic syncopation).
 * - `r = 1`: Events compressed to the very end of their slot.
 *
 * @param r Compression ratio in the range [0, 1]; supports control patterns.
 * @return A syncopated pattern.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").pressBy(0.5)          // classic syncopation
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").pressBy("<0 0.5 0.25>") // varying syncopation each cycle
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pressBy(r: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPressBy(this, listOf(r).asSprudelDslArgs(callInfo))

/** Syncopates this string pattern by compressing events to start at position `r`. */
@SprudelDsl
@KlangScript.Function
fun String.pressBy(r: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pressBy(r, callInfo)

/**
 * Returns a [PatternMapperFn] that syncopates the source by compressing events to position `r`.
 *
 * @param r Compression ratio in the range [0, 1].
 * @return A [PatternMapperFn] that syncopates the source.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").apply(pressBy(0.5))   // classic syncopation via mapper
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@SprudelDsl
@KlangScript.Function
fun pressBy(r: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pressBy(r, callInfo) }

/** Chains a pressBy onto this [PatternMapperFn]; compresses events to position `r`. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pressBy(r: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pressBy(r, callInfo) }

// -- press() ----------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by shifting each event halfway into its timespan.
 * Equivalent to `pressBy(0.5)`.
 *
 * Example: s("bd mt sd ht").every(4, { it.press() })
 */
private fun applyPress(pattern: SprudelPattern): SprudelPattern {
    return applyPressBy(pattern, listOf(SprudelDslArg.of(0.5)))
}

/**
 * Syncopates this pattern by shifting each event halfway into its timespan.
 *
 * Equivalent to `pressBy(0.5)`. Creates a classic off-beat feel.
 *
 * @return A syncopated pattern where events start halfway through their slots.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").press()                   // classic off-beat feel
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").every(4, x => x.press())   // press every 4th cycle
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.press(callInfo: CallInfo? = null): SprudelPattern = applyPress(this)

/** Syncopates this string pattern by shifting events halfway into their timespan. */
@SprudelDsl
@KlangScript.Function
fun String.press(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).press(callInfo)

/**
 * Returns a [PatternMapperFn] that syncopates the source by shifting events halfway.
 *
 * Equivalent to `pressBy(0.5)` as a mapper. Apply using `.apply()`.
 *
 * @return A [PatternMapperFn] that shifts each event halfway into its slot.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").apply(press())   // classic off-beat feel via mapper
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@SprudelDsl
@KlangScript.Function
fun press(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.press(callInfo) }

/** Chains a press onto this [PatternMapperFn]; shifts each event halfway into its timespan. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.press(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.press(callInfo) }

// -- ribbon() ---------------------------------------------------------------------------------------------------------

private fun applyRibbon(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val offsetArg = args.getOrNull(0) ?: SprudelDslArg.of(0.0)
    val cyclesArg = args.getOrNull(1) ?: SprudelDslArg.of(1.0)

    // pat.early(offset) -> use applyTimeShift with factor -1
    val shifted = applyTimeShift(pattern, listOf(offsetArg), Rational.MINUS_ONE)

    // pure(1).slow(cycles)
    // We use SequencePattern to ensure we get discrete events per cycle (or per 'cycles' duration),
    // which forces _bindRestart to re-trigger the pattern repeatedly (looping it).
    // If we just used AtomicPattern, it might produce a single long event, preventing the loop.
    val one = AtomicPattern(SprudelVoiceData.empty.copy(value = 1.asVoiceValue()))
    val pureOne = SequencePattern(listOf(one))

    val loopStructure = applySlow(pureOne, listOf(cyclesArg))

    // struct.restart(shifted)
    return loopStructure._bindRestart { shifted }
}

/**
 * Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * Imagine the entire timeline as a ribbon: `ribbon` cuts a piece starting at `offset` with length
 * `cycles`, then loops that piece indefinitely.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").ribbon(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").ribbon(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRibbon(this, listOf(offset, cycles).asSprudelDslArgs(callInfo))

/**
 * Loops a segment of the mini-notation string pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".ribbon(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun String.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ribbon(offset, cycles, callInfo)

/**
 * Returns a [PatternMapperFn] that loops a segment of the source pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").apply(ribbon(1, 2))  // via mapper
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ribbon(offset, cycles, callInfo) }

/** Chains a ribbon onto this [PatternMapperFn]; loops a segment of the result starting at `offset`. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ribbon(offset, cycles, callInfo) }

/**
 * Alias for [ribbon]. Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").rib(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").rib(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.ribbon(offset, cycles, callInfo)

/**
 * Alias for [ribbon] applied to a mini-notation string.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".rib(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun String.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rib(offset, cycles, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [ribbon] — loops a segment of the source pattern.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").apply(rib(1, 2))  // via mapper
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
@KlangScript.Function
fun rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rib(offset, cycles, callInfo) }

/** Chains a rib onto this [PatternMapperFn]; alias for [PatternMapperFn.ribbon]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rib(offset, cycles, callInfo) }
