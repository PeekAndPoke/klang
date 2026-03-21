@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.common.math.lcm
import io.peekandpoke.klang.sprudel.*
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.addons.not
import io.peekandpoke.klang.sprudel.lang.addons.timeLoop
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.*
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
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
fun applyHush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _hush by dslPatternMapper { args, callInfo -> { p -> p._hush(args, callInfo) } }
internal val SprudelPattern._hush by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._hush by dslStringExtension { p, args, callInfo -> p._hush(args, callInfo) }
internal val PatternMapperFn._hush by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hush(args, callInfo))
}

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript
 * s("bd sd").hush()          // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").hush("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
fun SprudelPattern.hush(vararg args: PatternLike): SprudelPattern = this._hush(args.toList())

/** Silences this string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
fun String.hush(vararg args: PatternLike): SprudelPattern = this._hush(args.toList())

/**
 * Returns a [PatternMapperFn] that silences the source pattern.
 *
 * Without arguments, unconditionally silences. With a condition, silences when truthy.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript
 * s("bd sd").apply(hush())          // Unconditional silence via mapper
 * ```
 *
 * ```KlangScript
 * s("bd sd").apply(hush("<1 0>"))   // Silent on odd cycles via mapper
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
fun hush(vararg args: PatternLike): PatternMapperFn = _hush(args.toList())

/** Chains a hush onto this [PatternMapperFn]; silences or conditionally gates the result. */
@SprudelDsl
fun PatternMapperFn.hush(vararg args: PatternLike): PatternMapperFn = this._hush(args.toList())

internal val _bypass by dslPatternMapper { args, callInfo -> { p -> p._bypass(args, callInfo) } }
internal val SprudelPattern._bypass by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._bypass by dslStringExtension { p, args, callInfo -> p._bypass(args, callInfo) }
internal val PatternMapperFn._bypass by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bypass(args, callInfo))
}

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript
 * s("bd sd").bypass()          // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").bypass("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@SprudelDsl
fun SprudelPattern.bypass(vararg args: PatternLike): SprudelPattern = this._bypass(args.toList())

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
fun String.bypass(vararg args: PatternLike): SprudelPattern = this._bypass(args.toList())

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript
 * s("bd sd").apply(bypass())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@SprudelDsl
fun bypass(vararg args: PatternLike): PatternMapperFn = _bypass(args.toList())

/** Chains a bypass (alias for [hush]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.bypass(vararg args: PatternLike): PatternMapperFn = this._bypass(args.toList())

internal val _mute by dslPatternMapper { args, callInfo -> { p -> p._mute(args, callInfo) } }
internal val SprudelPattern._mute by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._mute by dslStringExtension { p, args, callInfo -> p._mute(args, callInfo) }
internal val PatternMapperFn._mute by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_mute(args, callInfo))
}

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript
 * s("bd sd").mute()          // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").mute("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
fun SprudelPattern.mute(vararg args: PatternLike): SprudelPattern = this._mute(args.toList())

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@SprudelDsl
fun String.mute(vararg args: PatternLike): SprudelPattern = this._mute(args.toList())

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript
 * s("bd sd").apply(mute())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@SprudelDsl
fun mute(vararg args: PatternLike): PatternMapperFn = _mute(args.toList())

/** Chains a mute (alias for [hush]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.mute(vararg args: PatternLike): PatternMapperFn = this._mute(args.toList())

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates a silent pattern occupying the given number of steps. Supports control patterns via _innerJoin. */
fun applyGap(args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

// delegates - still register with KlangScript
internal val _gap by dslPatternFunction { args, /* callInfo */ _ -> applyGap(args) }
internal val SprudelPattern._gap by dslPatternExtension { _, args, /* callInfo */ _ -> applyGap(args) }
internal val String._gap by dslStringExtension { p, args, callInfo -> p._gap(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * seq("bd", gap(), "hh").s()  // bd, 1-step rest, hh — each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript
 * seq("bd", gap(2), "hh").s()  // bd=1/4, rest=2/4, hh=1/4
 * ```
 * @category structural
 * @tags silence, rest, gap, rhythm
 */
@SprudelDsl
fun gap(vararg steps: PatternLike): SprudelPattern = _gap(steps.toList())

/**
 * Replaces this pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript
 * note("c").gap()  // Replaces with 1-step silence
 * ```
 *
 * ```KlangScript
 * note("c").gap(2)  // Replaces with 2-step silence
 * ```
 */
@SprudelDsl
fun SprudelPattern.gap(vararg steps: PatternLike): SprudelPattern = this._gap(steps.toList())

/**
 * Replaces this string pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript
 * seq("bd", "hh".gap(), "sd").s()  // Middle step replaced by silence
 * ```
 */
@SprudelDsl
fun String.gap(vararg steps: PatternLike): SprudelPattern = this._gap(steps.toList())

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
fun applySeq(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

// delegates - still register with KlangScript
internal val _seq by dslPatternFunction { args, /* callInfo */ _ -> applySeq(args.toListOfPatterns()) }

internal val SprudelPattern._seq by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._seq by dslStringExtension { p, args, callInfo -> p._seq(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * seq("c d e", "f g a").note()  // Two patterns squeezed into one cycle
 * ```
 *
 * ```KlangScript
 * seq(note("c"), note("e"), note("g"))  // Three notes, each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript
 * seq("bd", ["sd", "oh"], "hh").s()  // Nested list as sub-sequence within its slot
 * ```
 * @category structural
 * @tags sequence, timing, control, order, pattern-creator
 */
@SprudelDsl
fun seq(vararg patterns: PatternLike): SprudelPattern {
    return _seq(patterns.toList())
}

/**
 * Appends patterns to this pattern and squeezes all of them into one cycle.
 *
 * This pattern and all appended patterns are evenly distributed within a single cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript
 * note("c e").seq("g a".note())
 * ```
 *
 * ```KlangScript
 * "bd sd".seq("hh hh", "cp").s()
 * ```
 */
@SprudelDsl
fun SprudelPattern.seq(vararg patterns: PatternLike): SprudelPattern {
    return this._seq(patterns.toList())
}

/**
 * Converts this string to a pattern and squeezes it together with additional patterns into one cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript
 * "c e".seq("g a").note()  // Two patterns squeezed into one cycle
 * ```
 */
@SprudelDsl
fun String.seq(vararg patterns: PatternLike): SprudelPattern {
    return this._seq(patterns.toList())
}

// -- mini() -----------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val _mini by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern() }
internal val String._mini by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p }

// ===== USER-FACING OVERLOADS =====

/**
 * Parses mini-notation and returns the resulting pattern. Effectively an alias for [seq].
 *
 * Mini-notation is the compact pattern language for expressing sequences, sub-sequences,
 * alternations, and other rhythmic structures inline as strings.
 *
 * @param patterns Strings or other pattern-like values to parse as mini-notation.
 * @return A pattern built from the mini-notation input
 *
 * ```KlangScript
 * mini("c d e f").note()  // Four notes, one per quarter cycle
 * ```
 *
 * ```KlangScript
 * mini("bd [sd cp] hh").s()  // Nested sub-sequence in square brackets
 * ```
 * @category structural
 * @tags mini, notation, parse, sequence
 */
@SprudelDsl
fun mini(vararg patterns: PatternLike): SprudelPattern = _mini(patterns.toList())

/**
 * Parses this string as mini-notation and returns the resulting pattern.
 *
 * ```KlangScript
 * "c d e f".mini().note()  // Four notes from mini-notation string
 * ```
 */
@SprudelDsl
fun String.mini(): SprudelPattern = this._mini()

// -- stack() ----------------------------------------------------------------------------------------------------------

fun applyStack(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> StackPattern(patterns)
    }
}

// delegates - still register with KlangScript
internal val _stack by dslPatternFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns())
}

internal val SprudelPattern._stack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._stack by dslStringExtension { p, args, callInfo -> p._stack(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * stack(note("c e g"), s("bd sd"))  // Chord with beat underneath
 * ```
 *
 * ```KlangScript
 * stack("c e", "g b").note()  // Two melodic lines at the same time
 * ```
 * @category structural
 * @tags stack, layer, chord, polyrhythm, simultaneous
 */
@SprudelDsl
fun stack(vararg patterns: PatternLike): SprudelPattern = _stack(patterns.toList())

/**
 * Layers this pattern together with additional patterns so they all play simultaneously.
 *
 * ```KlangScript
 * note("c e g").stack(s("bd sd"))  // Melody on top of a beat
 * ```
 *
 * ```KlangScript
 * note("c e").stack("g b".note())  // Two melodic lines layered
 * ```
 */
@SprudelDsl
fun SprudelPattern.stack(vararg patterns: PatternLike): SprudelPattern = this._stack(patterns.toList())

/**
 * Parses this string as a pattern and layers it together with additional patterns.
 *
 * ```KlangScript
 * "c e g".stack("g b d").note()  // Two chord voicings layered
 * ```
 */
@SprudelDsl
fun String.stack(vararg patterns: PatternLike): SprudelPattern = this._stack(patterns.toList())

// -- arrange() --------------------------------------------------------------------------------------------------------

fun applyArrange(args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

// delegates - still register with KlangScript
internal val _arrange by dslPatternFunction { args, /* callInfo */ _ -> applyArrange(args) }

internal val SprudelPattern._arrange by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArrange(listOf(SprudelDslArg.of(p)) + args)
}

internal val String._arrange by dslStringExtension { p, args, callInfo -> p._arrange(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * arrange([2, "a b"], [1, "c"]).note()  // "a b" for 2 cycles, "c" for 1
 * ```
 *
 * ```KlangScript
 * arrange([3, note("c e g")], [1, note("f a c")]).s("piano")  // chord changes over 4 cycles
 * ```
 *
 * ```KlangScript
 * arrange(note("c e g"), [2, note("f a c")]).s("piano")  // Pattern without weight
 * ```
 * @category structural
 * @tags arrange, sequence, timing, duration, loop
 */
@SprudelDsl
fun arrange(vararg segments: PatternLike): SprudelPattern = _arrange(segments.toList())

/**
 * Prepends this pattern (duration 1) and plays it followed by the given segments.
 *
 * ```KlangScript
 * note("c e g").arrange([2, note("f a c")]).s("piano")  // 1 cycle chord, then 2 cycles
 * ```
 */
@SprudelDsl
fun SprudelPattern.arrange(vararg segments: PatternLike): SprudelPattern = this._arrange(segments.toList())

/**
 * Parses this string as a pattern (duration 1) and arranges it together with the given segments.
 *
 * ```KlangScript
 * "c e g".arrange([2, "f a c"]).note()  // 1 cycle, then 2 cycles of second chord
 * ```
 */
@SprudelDsl
fun String.arrange(vararg segments: PatternLike): SprudelPattern = this._arrange(segments.toList())

// -- stepcat() / timeCat() --------------------------------------------------------------------------------------------

fun applyStepcat(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val segments = args.parseWeightedArgs()

    if (segments.isEmpty()) return silence

    // Parse arguments into weighted patterns
    val patterns = segments.map { (dur, pat) ->
        pat.withWeight(dur)
    }

    // Use SequencePattern which handles weighted time distribution and compression to 1 cycle
    return SequencePattern(patterns)
}

// delegates - still register with KlangScript
internal val _stepcat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val SprudelPattern._stepcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(SprudelDslArg.of(p)) + args)
}

internal val String._stepcat by dslStringExtension { p, args, callInfo -> p._stepcat(args, callInfo) }

internal val _timeCat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val SprudelPattern._timeCat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(SprudelDslArg.of(p)) + args)
}

internal val String._timeCat by dslStringExtension { p, args, callInfo -> p._timeCat(args, callInfo) }

internal val _timecat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val SprudelPattern._timecat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(SprudelDslArg.of(p)) + args)
}

internal val String._timecat by dslStringExtension { p, args, callInfo -> p._timecat(args, callInfo) }

internal val _s_cat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val SprudelPattern._s_cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(SprudelDslArg.of(p)) + args)
}

internal val String._s_cat by dslStringExtension { p, args, callInfo -> p._s_cat(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * stepcat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4 of the cycle
 * ```
 *
 * ```KlangScript
 * stepcat([2, note("c")], [1, note("e g")])  // "c" takes 2/3, "e g" takes 1/3
 * ```
 * @alias timeCat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
fun stepcat(vararg segments: PatternLike): SprudelPattern = _stepcat(segments.toList())

/**
 * Prepends this pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * note("c").stepcat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun SprudelPattern.stepcat(vararg segments: PatternLike): SprudelPattern = this._stepcat(segments.toList())

/**
 * Parses this string as a pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".stepcat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun String.stepcat(vararg segments: PatternLike): SprudelPattern = this._stepcat(segments.toList())

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript
 * timeCat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
fun timeCat(vararg segments: PatternLike): SprudelPattern = _timeCat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").timeCat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun SprudelPattern.timeCat(vararg segments: PatternLike): SprudelPattern = this._timeCat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".timeCat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun String.timeCat(vararg segments: PatternLike): SprudelPattern = this._timeCat(segments.toList())

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript
 * timecat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
fun timecat(vararg segments: PatternLike): SprudelPattern = _timecat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").timecat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun SprudelPattern.timecat(vararg segments: PatternLike): SprudelPattern = this._timecat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".timecat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun String.timecat(vararg segments: PatternLike): SprudelPattern = this._timecat(segments.toList())

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript
 * s_cat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, timecat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@SprudelDsl
fun s_cat(vararg segments: PatternLike): SprudelPattern = _s_cat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").s_cat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun SprudelPattern.s_cat(vararg segments: PatternLike): SprudelPattern = this._s_cat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".s_cat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@SprudelDsl
fun String.s_cat(vararg segments: PatternLike): SprudelPattern = this._s_cat(segments.toList())

// -- stackBy() --------------------------------------------------------------------------------------------------------

fun applyStackBy(patterns: List<SprudelPattern>, alignment: Double): SprudelPattern {
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

// delegates - still register with KlangScript
internal val _stackBy by dslPatternFunction { args, /* callInfo */ _ ->
    // TODO: support control patterns
    val alignment = args.firstOrNull()?.value?.asDoubleOrNull() ?: 0.0
    val patterns = args.drop(1).toListOfPatterns()
    applyStackBy(patterns = patterns, alignment = alignment)
}

internal val _stackLeft by dslPatternFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(), alignment = 0.0)
}

internal val _stackRight by dslPatternFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(), alignment = 1.0)
}

internal val _stackCentre by dslPatternFunction { args, /* callInfo */ _ ->
    applyStackBy(patterns = args.toListOfPatterns(), alignment = 0.5)
}

internal val _polyrhythm by dslPatternFunction { args, /* callInfo */ _ ->
    applyStack(patterns = args.toListOfPatterns())
}

internal val SprudelPattern._polyrhythm by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStack(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._polyrhythm by dslStringExtension { p, args, callInfo -> p._polyrhythm(args, callInfo) }

internal val _sequenceP by dslPatternFunction { args, /* callInfo */ _ -> applySeq(args.toListOfPatterns()) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * stackBy(0.5, note("c"), note("c e g"))  // short pattern centered within the long one
 * ```
 *
 * ```KlangScript
 * stackBy(1.0, s("bd"), s("bd sd ht lt"))  // short pattern right-aligned
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
fun stackBy(alignment: PatternLike, vararg patterns: PatternLike): SprudelPattern =
    _stackBy(listOf(alignment).plus(patterns.toList()))

// -- stackLeft() ------------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the left (start) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones left-aligned
 *
 * ```KlangScript
 * stackLeft(note("c"), note("c e g"))  // short pattern starts at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
fun stackLeft(vararg patterns: PatternLike): SprudelPattern = _stackLeft(patterns.toList())

// -- stackRight() -----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the right (end) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones right-aligned
 *
 * ```KlangScript
 * stackRight(note("c"), note("c e g"))  // short pattern ends at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
fun stackRight(vararg patterns: PatternLike): SprudelPattern = _stackRight(patterns.toList())

// -- stackCentre() ----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the centre of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones centred
 *
 * ```KlangScript
 * stackCentre(note("c"), note("c e g"))  // short pattern centred within the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@SprudelDsl
fun stackCentre(vararg patterns: PatternLike): SprudelPattern = _stackCentre(patterns.toList())

// -- polyrhythm() -----------------------------------------------------------------------------------------------------

/**
 * Alias for [stack]. Plays multiple patterns simultaneously to create polyrhythms.
 *
 * @param patterns Patterns to layer simultaneously.
 * @return A pattern that plays all inputs at the same time
 *
 * ```KlangScript
 * polyrhythm(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat patterns together
 * ```
 * @alias stack
 * @category structural
 * @tags polyrhythm, stack, layer, simultaneous, rhythm
 */
@SprudelDsl
fun polyrhythm(vararg patterns: PatternLike): SprudelPattern = _polyrhythm(patterns.toList())

/**
 * Alias for [stack]. Layers this pattern together with additional patterns simultaneously.
 *
 * ```KlangScript
 * s("bd sd").polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@SprudelDsl
fun SprudelPattern.polyrhythm(vararg patterns: PatternLike): SprudelPattern = this._polyrhythm(patterns.toList())

/**
 * Alias for [stack]. Parses this string as a pattern and layers it with additional patterns.
 *
 * ```KlangScript
 * "bd sd".polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@SprudelDsl
fun String.polyrhythm(vararg patterns: PatternLike): SprudelPattern = this._polyrhythm(patterns.toList())

// -- sequenceP() ------------------------------------------------------------------------------------------------------

/**
 * Alias for [seq]. Creates a sequence pattern that squeezes all patterns into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript
 * sequenceP("c d", "e f").note()  // Two patterns squeezed into one cycle
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@SprudelDsl
fun sequenceP(vararg patterns: PatternLike): SprudelPattern = _sequenceP(patterns.toList())

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

// delegates - still register with KlangScript
internal val _cat by dslPatternFunction { args, /* callInfo */ _ -> applyCat(patterns = args.toListOfPatterns()) }

internal val SprudelPattern._cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._cat by dslStringExtension { p, args, callInfo -> p._cat(args, callInfo) }

internal val _fastcat by dslPatternFunction { args, /* callInfo */ _ -> applySeq(patterns = args.toListOfPatterns()) }

internal val SprudelPattern._fastcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._fastcat by dslStringExtension { p, args, callInfo -> p._fastcat(args, callInfo) }

internal val _slowcat by dslPatternFunction { args, /* callInfo */ _ -> applyCat(patterns = args.toListOfPatterns()) }

internal val SprudelPattern._slowcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._slowcat by dslStringExtension { p, args, callInfo -> p._slowcat(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Concatenates patterns in sequence, each playing for its natural cycle duration before the next begins.
 *
 * Unlike [seq], which squeezes all patterns into one cycle, `cat` plays each pattern for its full
 * natural duration. A 2-cycle pattern takes 2 cycles, then the next pattern begins.
 *
 * @param patterns Patterns to concatenate. Each plays for its natural duration.
 * @return A pattern that plays each input in turn for its natural duration
 *
 * ```KlangScript
 * cat(note("c d"), note("e f g")).s("piano")  // 2-step then 3-step pattern in sequence
 * ```
 *
 * ```KlangScript
 * cat(s("bd sd"), s("hh hh hh hh"))  // alternates between patterns each cycle
 * ```
 * @alias slowcat
 * @category structural
 * @tags cat, sequence, concatenate, timing
 */
@SprudelDsl
fun cat(vararg patterns: PatternLike): SprudelPattern = _cat(patterns.toList())

/**
 * Appends patterns to this pattern, each playing for its natural cycle duration.
 *
 * ```KlangScript
 * note("c d").cat(note("e f g"))  // "c d" then "e f g" in sequence
 * ```
 */
@SprudelDsl
fun SprudelPattern.cat(vararg patterns: PatternLike): SprudelPattern = this._cat(patterns.toList())

/**
 * Parses this string as a pattern and concatenates it with the given patterns in sequence.
 *
 * ```KlangScript
 * "c d".cat("e f g").note()  // "c d" then "e f g" in sequence
 * ```
 */
@SprudelDsl
fun String.cat(vararg patterns: PatternLike): SprudelPattern = this._cat(patterns.toList())

/**
 * Alias for [seq]. Concatenates patterns, squeezing them all into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript
 * fastcat("bd", "sd").s()  // same as seq("bd", "sd") or "bd sd"
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@SprudelDsl
fun fastcat(vararg patterns: PatternLike): SprudelPattern = _fastcat(patterns.toList())

/**
 * Alias for [seq]. Appends patterns to this pattern, squeezing all into one cycle.
 *
 * ```KlangScript
 * s("bd").fastcat(s("sd"))  // "bd sd" squeezed into one cycle
 * ```
 */
@SprudelDsl
fun SprudelPattern.fastcat(vararg patterns: PatternLike): SprudelPattern = this._fastcat(patterns.toList())

/**
 * Alias for [seq]. Parses this string and squeezes it together with the given patterns into one cycle.
 *
 * ```KlangScript
 * "bd".fastcat("sd").s()  // "bd sd" squeezed into one cycle
 * ```
 */
@SprudelDsl
fun String.fastcat(vararg patterns: PatternLike): SprudelPattern = this._fastcat(patterns.toList())

/**
 * Alias for [cat]. Concatenates patterns, each taking one full cycle.
 *
 * Note: unlike the JS implementation, this behaves like `cat` / `slowcatPrime`, maintaining
 * absolute time (cycles of inner patterns may be "skipped" while they are not playing).
 *
 * @param patterns Patterns to concatenate. Each plays for one cycle.
 * @return A pattern that plays each input for one cycle in turn
 *
 * ```KlangScript
 * slowcat("bd sd", "hh hh hh hh").s()  // cycle 0: "bd sd", cycle 1: "hh hh hh hh"
 * ```
 * @alias cat
 * @category structural
 * @tags sequence, concatenate, timing
 */
@SprudelDsl
fun slowcat(vararg patterns: PatternLike): SprudelPattern = _slowcat(patterns.toList())

/**
 * Alias for [cat]. Appends patterns to this pattern, each taking one full cycle.
 *
 * ```KlangScript
 * s("bd sd").slowcat(s("hh hh hh hh"))  // alternates each cycle
 * ```
 */
@SprudelDsl
fun SprudelPattern.slowcat(vararg patterns: PatternLike): SprudelPattern = this._slowcat(patterns.toList())

/**
 * Alias for [cat]. Parses this string and concatenates with the given patterns, each taking one cycle.
 *
 * ```KlangScript
 * "bd sd".slowcat("hh hh hh hh").s()  // alternates each cycle
 * ```
 */
@SprudelDsl
fun String.slowcat(vararg patterns: PatternLike): SprudelPattern = this._slowcat(patterns.toList())

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

// delegates - still register with KlangScript
internal val _slowcatPrime by dslPatternFunction { args, /* callInfo */ _ ->
    applySlowcatPrime(patterns = args.toListOfPatterns())
}

internal val SprudelPattern._slowcatPrime by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySlowcatPrime(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._slowcatPrime by dslStringExtension { p, args, callInfo -> p._slowcatPrime(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Cycles through patterns one per cycle, preserving absolute time across pattern switches.
 *
 * Like [cat], but when a pattern resumes it continues from where it would be at absolute time,
 * rather than restarting from zero. This means inner cycles of each pattern are not reset.
 *
 * @param patterns Patterns to cycle through, one per cycle.
 * @return A pattern that cycles through each input at absolute time
 *
 * ```KlangScript
 * slowcatPrime(note("c d e f"), note("g a b c")).s("piano")  // each pattern plays at abs time
 * ```
 * @category structural
 * @tags sequence, concatenate, timing, absolute
 */
@SprudelDsl
fun slowcatPrime(vararg patterns: PatternLike): SprudelPattern = _slowcatPrime(patterns.toList())

/**
 * Appends patterns to this pattern, cycling through them one per cycle at absolute time.
 *
 * ```KlangScript
 * note("c d").slowcatPrime(note("e f g"))  // cycles through at absolute time
 * ```
 */
@SprudelDsl
fun SprudelPattern.slowcatPrime(vararg patterns: PatternLike): SprudelPattern = this._slowcatPrime(patterns.toList())

/**
 * Parses this string and cycles through it with given patterns, one per cycle at absolute time.
 *
 * ```KlangScript
 * "c d".slowcatPrime("e f g").note()  // cycles through at absolute time
 * ```
 */
@SprudelDsl
fun String.slowcatPrime(vararg patterns: PatternLike): SprudelPattern = this._slowcatPrime(patterns.toList())

// -- polymeter() ------------------------------------------------------------------------------------------------------

fun applyPolymeter(patterns: List<SprudelPattern>, baseSteps: Int? = null): SprudelPattern {
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

// delegates - still register with KlangScript
internal val _polymeter by dslPatternFunction { args, /* callInfo */ _ ->
    applyPolymeter(patterns = args.toListOfPatterns())
}

internal val SprudelPattern._polymeter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPolymeter(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._polymeter by dslStringExtension { p, args, callInfo -> p._polymeter(args, callInfo) }

internal val _polymeterSteps by dslPatternFunction { args, /* callInfo */ _ ->
    val steps = args.getOrNull(0)?.value?.asIntOrNull() ?: 4
    val patterns = args.drop(1).toListOfPatterns()
    applyPolymeter(patterns = patterns, baseSteps = steps)
}

internal val _pure by dslPatternFunction { args, /* callInfo */ _ ->
    val value = args.getOrNull(0)?.value
    AtomicPattern(SprudelVoiceData.empty.copy(value = value?.asVoiceValue()))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * polymeter(note("c d"), note("c d e")).s("piano")  // 2- and 3-step in a 6-step cycle
 * ```
 *
 * ```KlangScript
 * polymeter(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat polyrhythm
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, alignment
 */
@SprudelDsl
fun polymeter(vararg patterns: PatternLike): SprudelPattern = _polymeter(patterns.toList())

/**
 * Prepends this pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript
 * note("c d").polymeter(note("c d e"))  // 2 and 3 steps aligned to LCM
 * ```
 */
@SprudelDsl
fun SprudelPattern.polymeter(vararg patterns: PatternLike): SprudelPattern = this._polymeter(patterns.toList())

/**
 * Parses this string as a pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript
 * "c d".polymeter("c d e").note()  // 2 and 3 steps aligned to LCM
 * ```
 */
@SprudelDsl
fun String.polymeter(vararg patterns: PatternLike): SprudelPattern = this._polymeter(patterns.toList())

// -- polymeterSteps() -------------------------------------------------------------------------------------------------

/**
 * Like [polymeter], but with an explicit step count instead of using the LCM.
 *
 * All patterns are sped up or slowed down to fit exactly `steps` steps per cycle.
 *
 * @param args First argument is the target step count; remaining arguments are the patterns.
 * @return A pattern with all inputs adjusted to the given step count per cycle
 *
 * ```KlangScript
 * polymeterSteps(4, note("c d"), note("c d e"))  // both fit into 4 steps per cycle
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, steps
 */
@SprudelDsl
fun polymeterSteps(vararg args: PatternLike): SprudelPattern = _polymeterSteps(args.toList())

// -- pure() -----------------------------------------------------------------------------------------------------------

/**
 * Creates an atomic pattern that repeats a single value every cycle.
 *
 * @param value The value to wrap in a pattern.
 * @return A pattern that emits `value` once per cycle
 *
 * ```KlangScript
 * pure("c").note()  // repeats note "c" every cycle
 * ```
 *
 * ```KlangScript
 * pure(1)  // repeats the number 1 every cycle
 * ```
 * @category structural
 * @tags pure, value, atomic, repeat
 */
@SprudelDsl
fun pure(value: PatternLike): SprudelPattern = _pure(listOf(value))

// -- struct() ---------------------------------------------------------------------------------------------------------

fun applyStruct(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
    val structure = structArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = true
    )
}

internal val _struct by dslPatternMapper { args, callInfo -> { p -> p._struct(args, callInfo) } }
internal val SprudelPattern._struct by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStruct(source = p, structArg = args.firstOrNull())
}
internal val String._struct by dslStringExtension { p, args, callInfo -> p._struct(args, callInfo) }
internal val PatternMapperFn._struct by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_struct(args, callInfo))
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
 * ```KlangScript
 * s("hh").struct("x ~ x x ~ x x ~")  // hats shaped by a boolean rhythm pattern
 * ```
 *
 * ```KlangScript
 * note("c e g").struct("x*4")         // chord hits restructured to 4 equal beats
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@SprudelDsl
fun SprudelPattern.struct(vararg args: PatternLike): SprudelPattern = this._struct(args.toList())

/** Restructures this string pattern using the mask's timing; keeps only truthy mask events. */
@SprudelDsl
fun String.struct(vararg args: PatternLike): SprudelPattern = this._struct(args.toList())

/**
 * Returns a [PatternMapperFn] that restructures the source pattern using the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source pattern to the mask's rhythm.
 *
 * ```KlangScript
 * s("hh").apply(struct("x ~ x x"))    // via mapper
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@SprudelDsl
fun struct(vararg args: PatternLike): PatternMapperFn = _struct(args.toList())

/** Chains a struct onto this [PatternMapperFn]; restructures using the mask's timing. */
@SprudelDsl
fun PatternMapperFn.struct(vararg args: PatternLike): PatternMapperFn = this._struct(args.toList())

// -- structAll() ------------------------------------------------------------------------------------------------------

fun applyStructAll(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
    val structure = structArg?.toPattern() ?: return silence

    // We use a different implementation for structAll that preserves all source events
    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = false
    )
}

internal val _structAll by dslPatternMapper { args, callInfo -> { p -> p._structAll(args, callInfo) } }
internal val SprudelPattern._structAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStructAll(source = p, structArg = args.firstOrNull())
}
internal val String._structAll by dslStringExtension { p, args, callInfo -> p._structAll(args, callInfo) }
internal val PatternMapperFn._structAll by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_structAll(args, callInfo))
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
 * ```KlangScript
 * note("c e g").structAll("x x")   // all chord notes kept within each x window
 * ```
 *
 * ```KlangScript
 * note("c e").structAll("x x x x") // both c and e kept within each mask window
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@SprudelDsl
fun SprudelPattern.structAll(vararg args: PatternLike): SprudelPattern = this._structAll(args.toList())

/** Like [structAll] on a string pattern; keeps all events overlapping the mask. */
@SprudelDsl
fun String.structAll(vararg args: PatternLike): SprudelPattern = this._structAll(args.toList())

/**
 * Returns a [PatternMapperFn] that reshapes the source using the mask, keeping all overlapping events.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source keeping all overlapping events.
 *
 * ```KlangScript
 * note("c e g").apply(structAll("x x"))   // via mapper
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@SprudelDsl
fun structAll(vararg args: PatternLike): PatternMapperFn = _structAll(args.toList())

/** Chains a structAll onto this [PatternMapperFn]; reshapes keeping all overlapping events. */
@SprudelDsl
fun PatternMapperFn.structAll(vararg args: PatternLike): PatternMapperFn = this._structAll(args.toList())

// -- mask() -----------------------------------------------------------------------------------------------------------

fun applyMask(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

internal val _mask by dslPatternMapper { args, callInfo -> { p -> p._mask(args, callInfo) } }
internal val SprudelPattern._mask by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMask(source = p, maskArg = args.firstOrNull())
}
internal val String._mask by dslStringExtension { p, args, callInfo -> p._mask(args, callInfo) }
internal val PatternMapperFn._mask by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_mask(args, callInfo))
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
 * ```KlangScript
 * s("bd sd hh cp").mask("1 0 1 1")  // second beat silenced by the mask
 * ```
 *
 * ```KlangScript
 * note("c d e f").mask("<1 0>")      // entire pattern alternates on/off each cycle
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@SprudelDsl
fun SprudelPattern.mask(vararg args: PatternLike): SprudelPattern = this._mask(args.toList())

/** Filters this string pattern using a boolean mask; truthy events pass, falsy events are silenced. */
@SprudelDsl
fun String.mask(vararg args: PatternLike): SprudelPattern = this._mask(args.toList())

/**
 * Returns a [PatternMapperFn] that filters the source using a boolean mask.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return A [PatternMapperFn] that gates the source pattern by the mask.
 *
 * ```KlangScript
 * s("bd sd hh cp").apply(mask("1 0 1 1"))  // via mapper
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@SprudelDsl
fun mask(vararg args: PatternLike): PatternMapperFn = _mask(args.toList())

/** Chains a mask onto this [PatternMapperFn]; gates the result by the boolean mask. */
@SprudelDsl
fun PatternMapperFn.mask(vararg args: PatternLike): PatternMapperFn = this._mask(args.toList())

// -- maskAll() --------------------------------------------------------------------------------------------------------

fun applyMaskAll(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = false
    )
}

internal val _maskAll by dslPatternMapper { args, callInfo -> { p -> p._maskAll(args, callInfo) } }
internal val SprudelPattern._maskAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMaskAll(source = p, maskArg = args.firstOrNull())
}
internal val String._maskAll by dslStringExtension { p, args, callInfo -> p._maskAll(args, callInfo) }
internal val PatternMapperFn._maskAll by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_maskAll(args, callInfo))
}

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * @param mask Pattern that defines the gating structure (all values allowed, not just truthy).
 * @return The source pattern gated by the mask's structure, with all overlapping events kept.
 *
 * ```KlangScript
 * note("c d e f").maskAll("x ~ x ~")  // every other beat silenced by structure
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@SprudelDsl
fun SprudelPattern.maskAll(vararg args: PatternLike): SprudelPattern = this._maskAll(args.toList())

/** Like [maskAll] on a string pattern; gates by mask structure, all values pass. */
@SprudelDsl
fun String.maskAll(vararg args: PatternLike): SprudelPattern = this._maskAll(args.toList())

/**
 * Returns a [PatternMapperFn] that gates the source using the mask's structure (all values pass).
 *
 * @param mask Pattern that defines the gating structure.
 * @return A [PatternMapperFn] that gates the source, keeping all overlapping events.
 *
 * ```KlangScript
 * note("c d e f").apply(maskAll("x ~ x ~"))  // via mapper
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@SprudelDsl
fun maskAll(vararg args: PatternLike): PatternMapperFn = _maskAll(args.toList())

/** Chains a maskAll onto this [PatternMapperFn]; gates the result keeping all overlapping events. */
@SprudelDsl
fun PatternMapperFn.maskAll(vararg args: PatternLike): PatternMapperFn = this._maskAll(args.toList())

// -- jux() ------------------------------------------------------------------------------------------------------------

fun applyJux(source: SprudelPattern, transform: PatternMapperFn): SprudelPattern {
    // Pan is unipolar (0.0 to 1.0).
    // jux pans original hard left (0.0) and transformed hard right (1.0).
    val left = source.pan(0.0)
    val right = transform(source).pan(1.0)
    return StackPattern(listOf(left, right))
}

internal val _jux by dslPatternMapper { args, callInfo -> { p -> p._jux(args, callInfo) } }
internal val SprudelPattern._jux by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transform = args.firstOrNull().toPatternMapper() ?: { it }
    applyJux(p, transform)
}
internal val String._jux by dslStringExtension { p, args, callInfo -> p._jux(args, callInfo) }
internal val PatternMapperFn._jux by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_jux(args, callInfo))
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
 * ```KlangScript
 * s("bd sd").jux(x => x.rev())       // reversed pattern panned right
 * ```
 *
 * ```KlangScript
 * note("c e g").jux(x => x.fast(2))  // double-speed version panned right
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@SprudelDsl
fun SprudelPattern.jux(transform: PatternMapperFn): SprudelPattern =
    this._jux(listOf(transform).asSprudelDslArgs())

/** Pans this string pattern hard left and a transformed version hard right. */
@SprudelDsl
fun String.jux(transform: PatternMapperFn): SprudelPattern =
    this._jux(listOf(transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that pans the source hard left and a transformed version hard right.
 *
 * @param transform Function applied to the right-channel copy of the source pattern.
 * @return A [PatternMapperFn] producing a stereo pattern.
 *
 * ```KlangScript
 * s("bd sd").apply(jux(x => x.rev()))   // via mapper
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@SprudelDsl
fun jux(transform: PatternMapperFn): PatternMapperFn = _jux(listOf(transform).asSprudelDslArgs())

/** Chains a jux onto this [PatternMapperFn]; pans left and transformed-right. */
@SprudelDsl
fun PatternMapperFn.jux(transform: PatternMapperFn): PatternMapperFn =
    this._jux(listOf(transform).asSprudelDslArgs())

// -- juxBy() ----------------------------------------------------------------------------------------------------------

fun applyJuxBy(source: SprudelPattern, amount: Double, transform: PatternMapperFn): SprudelPattern {
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

internal val _juxBy by dslPatternMapper { args, callInfo -> { p -> p._juxBy(args, callInfo) } }
internal val SprudelPattern._juxBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val amount = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 1.0
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyJuxBy(p, amount, transform)
}
internal val String._juxBy by dslStringExtension { p, args, callInfo -> p._juxBy(args, callInfo) }
internal val PatternMapperFn._juxBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_juxBy(args, callInfo))
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
 * ```KlangScript
 * s("bd sd").juxBy(0.5, x => x.rev())        // half stereo width, reversed on right
 * ```
 *
 * ```KlangScript
 * note("c e g").juxBy(0.75, x => x.fast(2))  // 75% stereo, faster on right
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@SprudelDsl
fun SprudelPattern.juxBy(amount: Double, transform: PatternMapperFn): SprudelPattern =
    this._juxBy(listOf(amount, transform).asSprudelDslArgs())

/** Like [jux] with adjustable stereo width on a string pattern. */
@SprudelDsl
fun String.juxBy(amount: Double, transform: PatternMapperFn): SprudelPattern =
    this._juxBy(listOf(amount, transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that pans the source with adjustable stereo width.
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan).
 * @param transform Function applied to the right-channel copy.
 * @return A [PatternMapperFn] producing a stereo pattern with the given width.
 *
 * ```KlangScript
 * s("bd sd").apply(juxBy(0.5, x => x.rev()))  // via mapper
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@SprudelDsl
fun juxBy(amount: Double, transform: PatternMapperFn): PatternMapperFn =
    _juxBy(listOf(amount, transform).asSprudelDslArgs())

/** Chains a juxBy onto this [PatternMapperFn]; pans with adjustable stereo width. */
@SprudelDsl
fun PatternMapperFn.juxBy(amount: Double, transform: PatternMapperFn): PatternMapperFn =
    this._juxBy(listOf(amount, transform).asSprudelDslArgs())

// -- off() ------------------------------------------------------------------------------------------------------------

internal val _off by dslPatternMapper { args, callInfo -> { p -> p._off(args, callInfo) } }
internal val SprudelPattern._off by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val time = args.getOrNull(0)?.value?.asRationalOrNull() ?: Rational.QUARTER

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    p.stack(transform(p).late(time))
}
internal val String._off by dslStringExtension { p, args, callInfo -> p._off(args, callInfo) }
internal val PatternMapperFn._off by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_off(args, callInfo))
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
 * ```KlangScript
 * s("bd sd").off(0.125, x => x.gain(0.2))       // quiet echo 1/8 cycle behind
 * ```
 *
 * ```KlangScript
 * note("c e g").off(0.25, x => x.transpose(12)) // octave-up copy a quarter cycle behind
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@SprudelDsl
fun SprudelPattern.off(time: Double, transform: PatternMapperFn): SprudelPattern =
    this._off(listOf(time, transform).asSprudelDslArgs())

/** Layers a time-shifted, transformed copy of this string pattern on top of itself. */
@SprudelDsl
fun String.off(time: Double, transform: PatternMapperFn): SprudelPattern =
    this._off(listOf(time, transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that layers a time-shifted, transformed copy on top of the source.
 *
 * @param time Time offset in cycles for the delayed copy.
 * @param transform Function applied to the delayed copy.
 * @return A [PatternMapperFn] that stacks the source with a late, transformed copy.
 *
 * ```KlangScript
 * s("bd sd").apply(off(0.125, x => x.gain(0.2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@SprudelDsl
fun off(time: Double, transform: PatternMapperFn): PatternMapperFn =
    _off(listOf(time, transform).asSprudelDslArgs())

/** Chains an off onto this [PatternMapperFn]; layers a time-shifted, transformed copy. */
@SprudelDsl
fun PatternMapperFn.off(time: Double, transform: PatternMapperFn): PatternMapperFn =
    this._off(listOf(time, transform).asSprudelDslArgs())

// -- filter() ---------------------------------------------------------------------------------------------------------

fun applyFilter(source: SprudelPattern, predicate: (SprudelPatternEvent) -> Boolean): SprudelPattern {
    return source.map { events -> events.filter(predicate) }
}

internal val _filter by dslPatternMapper { args, callInfo -> { p -> p._filter(args, callInfo) } }
internal val SprudelPattern._filter by dslPatternExtension { p, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((SprudelPatternEvent) -> Boolean)? =
        args.firstOrNull()?.value as? (SprudelPatternEvent) -> Boolean

    if (predicate != null) applyFilter(source = p, predicate = predicate) else p
}
internal val String._filter by dslStringExtension { p, args, callInfo -> p._filter(args, callInfo) }
internal val PatternMapperFn._filter by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_filter(args, callInfo))
}

/**
 * Filters events from this pattern using a predicate function.
 *
 * Only events for which [predicate] returns `true` are kept.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A pattern containing only the events that satisfy the predicate.
 *
 * ```KlangScript
 * s("bd sd hh cp").filter(x => x.part.begin < 0.5)  // keep first-half events
 * ```
 *
 * ```KlangScript
 * note("c d e f").filter(x => x.isOnset)             // keep only onset events
 * ```
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@SprudelDsl
fun SprudelPattern.filter(predicate: (SprudelPatternEvent) -> Boolean): SprudelPattern =
    this._filter(listOf(predicate).asSprudelDslArgs())

/** Filters events from this string pattern using a predicate function. */
@SprudelDsl
fun String.filter(predicate: (SprudelPatternEvent) -> Boolean): SprudelPattern =
    this._filter(listOf(predicate).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that filters events from the source using a predicate.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A [PatternMapperFn] that keeps only events satisfying the predicate.
 *
 * ```KlangScript
 * s("bd sd hh cp").apply(filter(x => x.part.begin < 0.5))  // via mapper
 * ```
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@SprudelDsl
fun filter(predicate: (SprudelPatternEvent) -> Boolean): PatternMapperFn =
    _filter(listOf(predicate).asSprudelDslArgs())

/** Chains a filter onto this [PatternMapperFn]; keeps only events satisfying the predicate. */
@SprudelDsl
fun PatternMapperFn.filter(predicate: (SprudelPatternEvent) -> Boolean): PatternMapperFn =
    this._filter(listOf(predicate).asSprudelDslArgs())

// -- filterWhen() -----------------------------------------------------------------------------------------------------

internal val _filterWhen by dslPatternMapper { args, callInfo -> { p -> p._filterWhen(args, callInfo) } }
internal val SprudelPattern._filterWhen by dslPatternExtension { source, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.firstOrNull()?.value as? (Double) -> Boolean

    if (predicate != null) applyFilter(source = source) { predicate(it.part.begin.toDouble()) } else source
}
internal val String._filterWhen by dslStringExtension { source, args, callInfo ->
    source._filterWhen(args, callInfo)
}
internal val PatternMapperFn._filterWhen by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_filterWhen(args, callInfo))
}

/**
 * Filters events from this pattern based on their begin time.
 *
 * Only events whose `part.begin` (as `Double`) satisfies [predicate] are kept.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A pattern with only the events whose begin time satisfies the predicate.
 *
 * ```KlangScript
 * note("c d e f").filterWhen(t => t < 0.5)       // keep only first-half events
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").filterWhen(t => t % 0.25 == 0) // keep only events on beat boundaries
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@SprudelDsl
fun SprudelPattern.filterWhen(predicate: (Double) -> Boolean): SprudelPattern =
    this._filterWhen(listOf(predicate).asSprudelDslArgs())

/** Filters events from this string pattern based on their begin time. */
@SprudelDsl
fun String.filterWhen(predicate: (Double) -> Boolean): SprudelPattern =
    this._filterWhen(listOf(predicate).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that filters events from the source based on their begin time.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A [PatternMapperFn] that filters events by their begin time.
 *
 * ```KlangScript
 * note("c d e f").apply(filterWhen(t => t < 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@SprudelDsl
fun filterWhen(predicate: (Double) -> Boolean): PatternMapperFn =
    _filterWhen(listOf(predicate).asSprudelDslArgs())

/** Chains a filterWhen onto this [PatternMapperFn]; filters events by their begin time. */
@SprudelDsl
fun PatternMapperFn.filterWhen(predicate: (Double) -> Boolean): PatternMapperFn =
    this._filterWhen(listOf(predicate).asSprudelDslArgs())

// -- superimpose() ----------------------------------------------------------------------------------------------------

internal val _superimpose by dslPatternMapper { args, callInfo -> { p -> p._superimpose(args, callInfo) } }
internal val SprudelPattern._superimpose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transformed = args
        .map { arg -> arg.toPatternMapper() ?: { it } }
        .map { it(p) }

    p.stack(*transformed.toTypedArray())
}
internal val String._superimpose by dslStringExtension { p, args, callInfo -> p._superimpose(args, callInfo) }
internal val PatternMapperFn._superimpose by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_superimpose(args, callInfo))
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
 * ```KlangScript
 * s("bd sd").superimpose(x => x.fast(2))                         // double-speed layer on top
 * ```
 *
 * ```KlangScript
 * note("c e g").superimpose(x => x.transpose(7), x => x.transpose(12))  // fifth and octave stacked
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@SprudelDsl
fun SprudelPattern.superimpose(vararg transforms: PatternMapperFn): SprudelPattern =
    this._superimpose(transforms.toList().asSprudelDslArgs())

/** Layers a transformed copy of this string pattern on top of itself. */
@SprudelDsl
fun String.superimpose(vararg transforms: PatternMapperFn): SprudelPattern =
    this._superimpose(transforms.toList().asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that layers a transformed copy of the source on top of itself.
 *
 * @param transforms Functions applied to produce the additional layers.
 * @return A [PatternMapperFn] that stacks the source with its transformed copy.
 *
 * ```KlangScript
 * s("bd sd").apply(superimpose(x => x.fast(2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@SprudelDsl
fun superimpose(vararg transforms: PatternMapperFn): PatternMapperFn =
    _superimpose(transforms.toList().asSprudelDslArgs())

/** Chains a superimpose onto this [PatternMapperFn]; layers a transformed copy on top. */
@SprudelDsl
fun PatternMapperFn.superimpose(vararg transforms: PatternMapperFn): PatternMapperFn =
    this._superimpose(transforms.toList().asSprudelDslArgs())

// -- layer() ----------------------------------------------------------------------------------------------------------

internal val _layer by dslPatternMapper { args, callInfo -> { p -> p._layer(args, callInfo) } }
internal val SprudelPattern._layer by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transforms: List<PatternMapperFn> = args.mapNotNull { it.toPatternMapper() }

    if (transforms.isEmpty()) {
        p // we keep the pattern as is
    } else {
        val patterns = transforms.map { transform ->
            try {
                transform(p)
            } catch (e: Exception) {
                println("Error applying layer transform: ${e.stackTraceToString()}")
                p
            }
        }

        if (patterns.size == 1) {
            patterns.first()
        } else {
            StackPattern(patterns)
        }
    }
}
internal val String._layer by dslStringExtension { p, args, callInfo -> p._layer(args, callInfo) }
internal val PatternMapperFn._layer by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_layer(args, callInfo))
}

internal val _apply by dslPatternMapper { args, callInfo -> _layer(args, callInfo) }
internal val SprudelPattern._apply by dslPatternExtension { p, args, callInfo -> p._layer(args, callInfo) }
internal val String._apply by dslStringExtension { p, args, callInfo -> p._apply(args, callInfo) }
internal val PatternMapperFn._apply by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_layer(args, callInfo))
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
 * ```KlangScript
 * s("bd hh sd oh").layer(x => x.fast(2), x => x.rev())              // two transformed layers stacked
 * ```
 *
 * ```KlangScript
 * note("c e g").layer(x => x.transpose(7), x => x.transpose(12))    // fifth and octave stacked
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@SprudelDsl
fun SprudelPattern.layer(vararg transforms: PatternMapperFn): SprudelPattern =
    this._layer(transforms.toList().asSprudelDslArgs())

/** Applies transformations to this string pattern and stacks the results. */
@SprudelDsl
fun String.layer(vararg transforms: PatternMapperFn): SprudelPattern =
    this._layer(transforms.toList().asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies the given transforms to the source and stacks results.
 *
 * @param transforms One or more functions; each is applied to the source and results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // via mapper
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@SprudelDsl
fun layer(vararg transforms: PatternMapperFn): PatternMapperFn =
    _layer(transforms.toList().asSprudelDslArgs())

/** Chains a layer onto this [PatternMapperFn]; stacks the transformed copies. */
@SprudelDsl
fun PatternMapperFn.layer(vararg transforms: PatternMapperFn): PatternMapperFn =
    this._layer(transforms.toList().asSprudelDslArgs())

/**
 * Alias for [layer] — applies multiple transformation functions and stacks the results.
 *
 * @param transforms One or more functions to apply; results are stacked.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript
 * s("bd hh sd oh").apply(x => x.fast(2), x => x.rev())   // two layers stacked
 * ```
 *
 * ```KlangScript
 * note("c e").apply(x => x.transpose(7))                  // fifth layer stacked
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@SprudelDsl
fun SprudelPattern.apply(vararg transforms: PatternMapperFn): SprudelPattern =
    this._apply(transforms.toList().asSprudelDslArgs())

/** Alias for [layer] on a string pattern. */
@SprudelDsl
fun String.apply(vararg transforms: PatternMapperFn): SprudelPattern =
    this._apply(transforms.toList().asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] — alias for [layer] — that applies transforms and stacks results.
 *
 * @param transforms One or more functions; results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // apply the layer mapper
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@SprudelDsl
fun apply(vararg transforms: PatternMapperFn): PatternMapperFn =
    _apply(transforms.toList().asSprudelDslArgs())

/** Chains an apply (alias for [layer]) onto this [PatternMapperFn]; stacks the transformed copies. */
@SprudelDsl
fun PatternMapperFn.apply(vararg transforms: PatternMapperFn): PatternMapperFn =
    this._apply(transforms.toList().asSprudelDslArgs())

// -- zoom() -----------------------------------------------------------------------------------------------------------

fun applyZoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _zoom by dslPatternMapper { args, callInfo -> { p -> p._zoom(args, callInfo) } }
internal val SprudelPattern._zoom by dslPatternExtension { p, args, /* callInfo */ _ -> applyZoom(p, args) }
internal val String._zoom by dslStringExtension { p, args, callInfo -> p._zoom(args, callInfo) }
internal val PatternMapperFn._zoom by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_zoom(args, callInfo))
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
 * ```KlangScript
 * s("bd hh sd hh").zoom(0.0, 0.5)   // plays only first half, stretched to full cycle
 * ```
 *
 * ```KlangScript
 * note("c d e f").zoom(0.25, 0.75)  // plays middle two notes, stretched to full cycle
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@SprudelDsl
fun SprudelPattern.zoom(start: PatternLike, end: PatternLike): SprudelPattern =
    this._zoom(listOf(start, end).asSprudelDslArgs())

/** Plays a portion of this string pattern within a time window, stretched to fill a cycle. */
@SprudelDsl
fun String.zoom(start: PatternLike, end: PatternLike): SprudelPattern =
    this._zoom(listOf(start, end).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that plays a portion of the source, stretching it to fill a cycle.
 *
 * @param start Start of the zoom window (0.0 to 1.0).
 * @param end End of the zoom window (0.0 to 1.0).
 * @return A [PatternMapperFn] that zooms the source into the given window.
 *
 * ```KlangScript
 * s("bd hh sd hh").apply(zoom(0.0, 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@SprudelDsl
fun zoom(start: PatternLike, end: PatternLike): PatternMapperFn =
    _zoom(listOf(start, end).asSprudelDslArgs())

/** Chains a zoom onto this [PatternMapperFn]; plays a window of the result stretched to one cycle. */
@SprudelDsl
fun PatternMapperFn.zoom(start: PatternLike, end: PatternLike): PatternMapperFn =
    this._zoom(listOf(start, end).asSprudelDslArgs())

// -- within() ---------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val SprudelPattern._within by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns for start and end
    val start = args.getOrNull(0)?.value?.asRationalOrNull() ?: Rational.ZERO
    val end = args.getOrNull(1)?.value?.asRationalOrNull() ?: Rational.ONE
    val transform = args.getOrNull(2).toPatternMapper() ?: { it }

    if (start >= end || start < Rational.ZERO || end > Rational.ONE) {
        p // Return unchanged if invalid window
    } else {
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

        val inside = p.filter(isBeginInWindow)
        val outside = p.filter { !isBeginInWindow(it) }

        StackPattern(listOf(transform(inside), outside))
    }
}

internal val String._within by dslStringExtension { p, args, callInfo -> p._within(args, callInfo) }
internal val _within by dslPatternMapper { args, callInfo -> { p -> p._within(args, callInfo) } }
internal val PatternMapperFn._within by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_within(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd hh cp").within(0.0, 0.5, x => x.fast(2))  // double-speed in first half
 * ```
 *
 * ```KlangScript
 * note("c d e f").within(0.25, 0.75, x => x.transpose(12))  // octave up in the middle
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
fun SprudelPattern.within(start: Double, end: Double, transform: PatternMapperFn): SprudelPattern =
    this._within(listOf(start, end, transform).asSprudelDslArgs())

/**
 * Applies a transformation to the portion of this string-parsed pattern that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript
 * "bd sd hh cp".within(0.0, 0.5, x => x.fast(2)).s()  // double-speed in first half
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
fun String.within(start: Double, end: Double, transform: PatternMapperFn): SprudelPattern =
    this._within(listOf(start, end, transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a transformation to the portion of the source pattern
 * that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return A [PatternMapperFn] that applies `transform` to events in `[start, end)`.
 *
 * ```KlangScript
 * s("bd sd hh cp").apply(within(0.0, 0.5, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@SprudelDsl
fun within(start: Double, end: Double, transform: PatternMapperFn): PatternMapperFn =
    _within(listOf(start, end, transform).asSprudelDslArgs())

/** Chains a within onto this [PatternMapperFn]; applies `transform` to events in the time window of the result. */
@SprudelDsl
fun PatternMapperFn.within(start: Double, end: Double, transform: PatternMapperFn): PatternMapperFn =
    this._within(listOf(start, end, transform).asSprudelDslArgs())

// -- chunk() ----------------------------------------------------------------------------------------------------------

fun applyChunk(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

// delegates - still register with KlangScript
internal val SprudelPattern._chunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
internal val String._chunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

internal val SprudelPattern._slowchunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
internal val String._slowchunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

internal val SprudelPattern._slowChunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
internal val String._slowChunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * seq("0 1 2 3").chunk(4) { it.add(7) }.scale("c:minor").n()  // one chunk transformed per cycle
 * ```
 *
 * ```KlangScript
 * s("bd sd ht lt").chunk(4) { it.gain(1.5) }  // one hit louder, cycling forward
 * ```
 * @alias slowchunk, slowChunk
 * @category structural
 * @tags chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
fun SprudelPattern.chunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = applyChunk(this, listOf(n, transform, back, fast).asSprudelDslArgs())

/** Like [chunk] applied to a mini-notation string. */
@SprudelDsl
fun String.chunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = this._chunk(listOf(n, transform, back, fast).asSprudelDslArgs())

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript
 * s("bd sd ht lt").slowchunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript
 * note("c d e f").slowchunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowChunk
 * @category structural
 * @tags slowchunk, chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
fun SprudelPattern.slowchunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = applyChunk(this, listOf(n, transform, back, fast).asSprudelDslArgs())

/** Alias for [chunk]. */
@SprudelDsl
fun String.slowchunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = this._slowchunk(listOf(n, transform, back, fast).asSprudelDslArgs())

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript
 * s("bd sd ht lt").slowChunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript
 * note("c d e f").slowChunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowchunk
 * @category structural
 * @tags slowChunk, chunk, cycle, transform, rotate, slice
 */
@SprudelDsl
fun SprudelPattern.slowChunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = applyChunk(this, listOf(n, transform, back, fast).asSprudelDslArgs())

/** Alias for [chunk]. */
@SprudelDsl
fun String.slowChunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapperFn,
): SprudelPattern = this._slowChunk(listOf(n, transform, back, fast).asSprudelDslArgs())

// -- chunkBack() / chunkback() ----------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val SprudelPattern._chunkBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, true, false).asSprudelDslArgs())
}

internal val String._chunkBack by dslStringExtension { p, args, callInfo -> p._chunkBack(args, callInfo) }

internal val SprudelPattern._chunkback by dslPatternExtension { p, args, callInfo ->
    p._chunkBack(args, callInfo)
}

internal val String._chunkback by dslStringExtension { p, args, callInfo -> p._chunkBack(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * seq("0 1 2 3").chunkBack(4, x => x.add(7)).scale("c:minor").n()  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript
 * s("bd sd ht lt").chunkBack(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@SprudelDsl
fun SprudelPattern.chunkBack(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkBack(listOf(n, transform).asSprudelDslArgs())

/**
 * Like [chunk] on a string-parsed pattern, but cycles backward through parts.
 *
 * ```KlangScript
 * "bd sd ht lt".chunkBack(4, x => x.gain(0.1)).s()  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 */
@SprudelDsl
fun String.chunkBack(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkBack(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [chunkBack] — cycles backward through transformed chunks.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern cycling backward through transformed chunks.
 *
 * ```KlangScript
 * seq("0 1 2 3").chunkback(4, x => x.add(7))  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript
 * s("bd sd ht lt").chunkback(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkBack
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@SprudelDsl
fun SprudelPattern.chunkback(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkback(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [chunkBack] on a string-parsed pattern.
 *
 * ```KlangScript
 * "bd sd ht lt".chunkback(4, x => x.gain(0.8)).s()  // one hit boosted, cycling back
 * ```
 * @alias chunkBack
 */
@SprudelDsl
fun String.chunkback(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkback(listOf(n, transform).asSprudelDslArgs())

// -- fastChunk() / fastchunk() ----------------------------------------------------------------------------------------

//  delegates - still register with KlangScript
internal val SprudelPattern._fastChunk by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, false, true).asSprudelDslArgs())
}

internal val String._fastChunk by dslStringExtension { p, args, callInfo -> p._fastChunk(args, callInfo) }

internal val SprudelPattern._fastchunk by dslPatternExtension { p, args, callInfo ->
    p._fastChunk(args, callInfo)
}

internal val String._fastchunk by dslStringExtension { p, args, callInfo -> p._fastChunk(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * seq("0 1 2 3").fastChunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript
 * s("bd sd ht lt").fastChunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastchunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@SprudelDsl
fun SprudelPattern.fastChunk(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._fastChunk(listOf(n, transform).asSprudelDslArgs())

/**
 * Like [chunk] on a string-parsed pattern but at natural speed.
 *
 * ```KlangScript
 * "bd sd ht lt".fastChunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastchunk
 */
@SprudelDsl
fun String.fastChunk(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._fastChunk(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [fastChunk] — like [chunk] but pattern plays at natural speed.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern at natural speed with chunks cycling through the transformation.
 *
 * ```KlangScript
 * seq("0 1 2 3").fastchunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript
 * s("bd sd ht lt").fastchunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastChunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@SprudelDsl
fun SprudelPattern.fastchunk(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._fastchunk(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [fastChunk] on a string-parsed pattern.
 *
 * ```KlangScript
 * "bd sd ht lt".fastchunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastChunk
 */
@SprudelDsl
fun String.fastchunk(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._fastchunk(listOf(n, transform).asSprudelDslArgs())

// -- chunkInto() ------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val SprudelPattern._chunkInto by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, false, true).asSprudelDslArgs())
}
internal val String._chunkInto by dslStringExtension { p, args, callInfo -> p._chunkInto(args, callInfo) }
internal val SprudelPattern._chunkinto by dslPatternExtension { p, args, callInfo -> p._chunkInto(args, callInfo) }
internal val String._chunkinto by dslStringExtension { p, args, callInfo -> p._chunkInto(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd ht lt").chunkInto(4, x => x.hurry(2))  // transform cycles, no repeat
 * ```
 *
 * ```KlangScript
 * note("c d e f g h").chunkInto(3, x => x.transpose(7))  // 3 chunks, each transposed
 * ```
 * @alias chunkinto
 * @category structural
 * @tags chunkInto, chunk, cycle, transform, fast, slice
 */
@SprudelDsl
fun SprudelPattern.chunkInto(n: Int, transform: PatternMapperFn): SprudelPattern =
    applyChunk(this, listOf(n, transform, false, true).asSprudelDslArgs())

/** Like [chunkInto] applied to a mini-notation string. */
@SprudelDsl
fun String.chunkInto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkInto(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [chunkInto] — applies [transform] to a fast-looped subcycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the active chunk each cycle.
 * @return A pattern at natural speed with the transform cycling through chunks.
 *
 * ```KlangScript
 * s("bd sd ht lt").chunkinto(4, x => x.hurry(2))  // lowercase alias
 * ```
 *
 * ```KlangScript
 * note("c d e f").chunkinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkInto
 * @category structural
 * @tags chunkinto, chunkInto, chunk, cycle, transform, fast, slice
 */
@SprudelDsl
fun SprudelPattern.chunkinto(n: Int, transform: PatternMapperFn): SprudelPattern =
    chunkInto(n, transform)

/** Alias for [chunkInto]. */
@SprudelDsl
fun String.chunkinto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this.chunkInto(n, transform)

// -- chunkBackInto() --------------------------------------------------------------------------------------------------

internal val SprudelPattern._chunkBackInto by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    applyChunk(p, listOf(nArg, transform, true, true, 1).asSprudelDslArgs())
}

internal val String._chunkBackInto by dslStringExtension { p, args, callInfo ->
    p._chunkBackInto(args, callInfo)
}

internal val SprudelPattern._chunkbackinto by dslPatternExtension { p, args, callInfo ->
    p._chunkBackInto(args, callInfo)
}

internal val String._chunkbackinto by dslStringExtension { p, args, callInfo ->
    p._chunkBackInto(args, callInfo)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Divides a pattern into `n` chunks and applies a transform to the active chunk, cycling backwards.
 *
 * Like [chunkInto], but advances through chunks in reverse order each cycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript
 * s("bd sd ht lt").chunkBackInto(4, x => x.hurry(2))  // transform cycles backward
 * ```
 *
 * ```KlangScript
 * note("c d e f g h").chunkBackInto(3, x => x.transpose(7))  // 3 chunks, reversed order
 * ```
 * @alias chunkbackinto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@SprudelDsl
fun SprudelPattern.chunkBackInto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkBackInto(listOf(n, transform).asSprudelDslArgs())

/** Like [chunkBackInto] applied to a mini-notation string. */
@SprudelDsl
fun String.chunkBackInto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkBackInto(listOf(n, transform).asSprudelDslArgs())

/**
 * Alias for [chunkBackInto] — divides the pattern into `n` chunks, applying a transform cycling backwards.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript
 * s("bd sd ht lt").chunkbackinto(4, x => x.hurry(2))  // backwards chunk transform
 * ```
 *
 * ```KlangScript
 * note("c d e f").chunkbackinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkBackInto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@SprudelDsl
fun SprudelPattern.chunkbackinto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkbackinto(listOf(n, transform).asSprudelDslArgs())

/** Alias for [chunkBackInto]. */
@SprudelDsl
fun String.chunkbackinto(n: Int, transform: PatternMapperFn): SprudelPattern =
    this._chunkbackinto(listOf(n, transform).asSprudelDslArgs())

// -- linger() ---------------------------------------------------------------------------------------------------------

fun applyLinger(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val SprudelPattern._linger by dslPatternExtension { p, args, /* callInfo */ _ -> applyLinger(p, args) }
internal val String._linger by dslStringExtension { p, args, callInfo -> p._linger(args, callInfo) }
internal val _linger by dslPatternMapper { args, callInfo -> { p -> p._linger(args, callInfo) } }
internal val PatternMapperFn._linger by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_linger(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd ht lt").linger(0.5)  // repeats "bd sd" throughout the cycle
 * ```
 *
 * ```KlangScript
 * s("lt ht mt cp").linger("<1 .5 .25 .125 .0625 .03125>")  // different fraction each cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
fun SprudelPattern.linger(t: PatternLike): SprudelPattern = this._linger(listOf(t).asSprudelDslArgs())

/**
 * Selects the given fraction of this string-parsed pattern and repeats that part to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript
 * "bd sd ht lt".linger(0.5).s()  // repeats "bd sd" throughout the cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
fun String.linger(t: PatternLike): SprudelPattern = this._linger(listOf(t).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects the given fraction of the source pattern and repeats it to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A [PatternMapperFn] that lingers on the selected fraction of the source.
 *
 * ```KlangScript
 * s("bd sd ht lt").apply(linger(0.5))  // via mapper
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@SprudelDsl
fun linger(t: PatternLike): PatternMapperFn = _linger(listOf(t).asSprudelDslArgs())

/** Chains a linger onto this [PatternMapperFn]; repeats the selected fraction of the result to fill the cycle. */
@SprudelDsl
fun PatternMapperFn.linger(t: PatternLike): PatternMapperFn = this._linger(listOf(t).asSprudelDslArgs())

// -- echo() / stut() --------------------------------------------------------------------------------------------------

internal val SprudelPattern._echo by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns for times, delay and decay
    val times = args.getOrNull(0)?.value?.asIntOrNull() ?: 1
    val delay = args.getOrNull(1)?.value?.asRationalOrNull() ?: Rational.QUARTER
    val decay = args.getOrNull(2)?.value?.asRationalOrNull() ?: Rational.HALF

    if (times < 1) {
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

internal val String._echo by dslStringExtension { p, args, callInfo -> p._echo(args, callInfo) }
internal val _echo by dslPatternMapper { args, callInfo -> { p -> p._echo(args, callInfo) } }
internal val PatternMapperFn._echo by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_echo(args, callInfo))
}

internal val SprudelPattern._stut by dslPatternExtension { p, args, callInfo -> p._echo(args, callInfo) }
internal val String._stut by dslStringExtension { p, args, callInfo -> p._echo(args, callInfo) }
internal val _stut by dslPatternMapper { args, callInfo -> { p -> p._stut(args, callInfo) } }
internal val PatternMapperFn._stut by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_stut(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd").echo(3, 0.125, 0.7)  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * ```KlangScript
 * note("c e g").echo(4, 0.25, 0.5)  // 4 layers, quarter-cycle spacing, halving gain
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
fun SprudelPattern.echo(times: Int, delay: Double, decay: Double): SprudelPattern =
    this._echo(listOf(times, delay, decay).asSprudelDslArgs())

/**
 * Like [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript
 * "bd sd".echo(3, 0.125, 0.7).s()  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
fun String.echo(times: Int, delay: Double, decay: Double): SprudelPattern =
    this._echo(listOf(times, delay, decay).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that superimposes delayed and decayed copies of the source pattern.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript
 * s("bd sd").apply(echo(3, 0.125, 0.7))  // via mapper
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@SprudelDsl
fun echo(times: Int, delay: Double, decay: Double): PatternMapperFn =
    _echo(listOf(times, delay, decay).asSprudelDslArgs())

/** Chains an echo onto this [PatternMapperFn]; superimposes delayed and decayed copies of the result. */
@SprudelDsl
fun PatternMapperFn.echo(times: Int, delay: Double, decay: Double): PatternMapperFn =
    this._echo(listOf(times, delay, decay).asSprudelDslArgs())

/**
 * Alias for [echo] — superimposes delayed and decayed copies of the pattern.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript
 * n("0").stut(4, 0.5, 0.5)  // 4 echoes, half-cycle spacing, halving gain
 * ```
 *
 * ```KlangScript
 * s("hh").stut(3, 0.125, 0.8)  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
fun SprudelPattern.stut(times: Int, delay: Double, decay: Double): SprudelPattern =
    this._stut(listOf(times, delay, decay).asSprudelDslArgs())

/**
 * Alias for [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript
 * "hh".stut(3, 0.125, 0.8).s()  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
fun String.stut(times: Int, delay: Double, decay: Double): SprudelPattern =
    this._stut(listOf(times, delay, decay).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that is an alias for [echo] — superimposes delayed and decayed copies.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript
 * s("hh").apply(stut(3, 0.125, 0.8))  // via mapper
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@SprudelDsl
fun stut(times: Int, delay: Double, decay: Double): PatternMapperFn =
    _stut(listOf(times, delay, decay).asSprudelDslArgs())

/** Chains a stut onto this [PatternMapperFn]; alias for [PatternMapperFn.echo]. */
@SprudelDsl
fun PatternMapperFn.stut(times: Int, delay: Double, decay: Double): PatternMapperFn =
    this._stut(listOf(times, delay, decay).asSprudelDslArgs())

// -- echoWith() / stutWith() ------------------------------------------------------------------------------------------

internal val SprudelPattern._echoWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns
    val times = args.getOrNull(0)?.value?.asIntOrNull() ?: 1
    val delay = args.getOrNull(1)?.value?.asRationalOrNull() ?: Rational.QUARTER
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

internal val String._echoWith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

internal val SprudelPattern._stutWith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
internal val String._stutWith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

internal val SprudelPattern._stutwith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
internal val String._stutwith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

internal val SprudelPattern._echowith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
internal val String._echowith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * n("0").echoWith(4, 0.125, x => x.add(2))  // layers at n=0,2,4,6, each 0.125 apart
 * ```
 *
 * ```KlangScript
 * s("bd").echoWith(3, 0.25, x => x.fast(2))  // original + 2× and 4× faster copies
 * ```
 * @alias stutWith, stutwith, echowith
 * @category structural
 * @tags echoWith, stutWith, delay, transform, layers, effect
 */
@SprudelDsl
fun SprudelPattern.echoWith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._echoWith(listOf(times, delay, transform).asSprudelDslArgs())

/** Like [echoWith] applied to a mini-notation string. */
@SprudelDsl
fun String.echoWith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._echoWith(listOf(times, delay, transform).asSprudelDslArgs())

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript
 * n("0").stutWith(4, 0.125, x => x.add(2))  // stut alias with additive transform
 * ```
 *
 * ```KlangScript
 * s("hh").stutWith(3, 0.25, x => x.gain(0.7))  // quieter copies
 * ```
 * @alias echoWith, stutwith, echowith
 * @category structural
 * @tags stutWith, echoWith, delay, transform, layers, effect
 */
@SprudelDsl
fun SprudelPattern.stutWith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._stutWith(listOf(times, delay, transform).asSprudelDslArgs())

/** Alias for [echoWith]. */
@SprudelDsl
fun String.stutWith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._stutWith(listOf(times, delay, transform).asSprudelDslArgs())

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript
 * n("0").stutwith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript
 * s("sd").stutwith(3, 0.25, x => x.speed(1.5))  // each copy 50% faster
 * ```
 * @alias echoWith, stutWith, echowith
 * @category structural
 * @tags stutwith, echoWith, stutWith, delay, transform, layers
 */
@SprudelDsl
fun SprudelPattern.stutwith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._stutwith(listOf(times, delay, transform).asSprudelDslArgs())

/** Alias for [echoWith]. */
@SprudelDsl
fun String.stutwith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._stutwith(listOf(times, delay, transform).asSprudelDslArgs())

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript
 * n("0").echowith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript
 * s("bd").echowith(3, 0.5, x => x.rev())  // each copy reversed
 * ```
 * @alias echoWith, stutWith, stutwith
 * @category structural
 * @tags echowith, echoWith, stutWith, delay, transform, layers
 */
@SprudelDsl
fun SprudelPattern.echowith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._echowith(listOf(times, delay, transform).asSprudelDslArgs())

/** Alias for [echoWith]. */
@SprudelDsl
fun String.echowith(times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern =
    this._echowith(listOf(times, delay, transform).asSprudelDslArgs())

// -- bite() -----------------------------------------------------------------------------------------------------------

fun applyBite(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return silence

    val nPattern = args.take(1).toPattern()
    val indicesPattern = args.drop(1).toPattern()
    val indicesSteps: Rational = indicesPattern.numSteps ?: return silence

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

internal val SprudelPattern._bite by dslPatternExtension { p, args, /* callInfo */ _ -> applyBite(p, args) }
internal val String._bite by dslStringExtension { p, args, callInfo -> p._bite(args, callInfo) }
internal val _bite by dslPatternMapper { args, callInfo -> { p -> p._bite(args, callInfo) } }
internal val PatternMapperFn._bite by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bite(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * n("0 1 2 3").bite(4, "3 2 1 0").scale("c3:major")  // reverse the pattern
 * ```
 *
 * ```KlangScript
 * n("0 1 2 3").bite(4, "0!2 1").scale("c3:major")  // play slice 0 twice then slice 1
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
fun SprudelPattern.bite(n: PatternLike, indices: PatternLike): SprudelPattern =
    this._bite(listOf(n, indices).asSprudelDslArgs())

/**
 * Like [bite] applied to a mini-notation string.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A new pattern built by playing slices in the order specified by `indices`.
 *
 * ```KlangScript
 * "0 1 2 3".bite(4, "3 2 1 0").n().scale("c3:major")  // reverse the pattern
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
fun String.bite(n: PatternLike, indices: PatternLike): SprudelPattern =
    this._bite(listOf(n, indices).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that splits the source into `n` equal slices and plays them in `indices` order.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A [PatternMapperFn] that rearranges slices of the source pattern.
 *
 * ```KlangScript
 * n("0 1 2 3").apply(bite(4, "3 2 1 0")).scale("c3:major")  // via mapper
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@SprudelDsl
fun bite(n: PatternLike, indices: PatternLike): PatternMapperFn =
    _bite(listOf(n, indices).asSprudelDslArgs())

/** Chains a bite onto this [PatternMapperFn]; rearranges slices of the result in `indices` order. */
@SprudelDsl
fun PatternMapperFn.bite(n: PatternLike, indices: PatternLike): PatternMapperFn =
    this._bite(listOf(n, indices).asSprudelDslArgs())

// -- segment() --------------------------------------------------------------------------------------------------------

fun applySegment(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val SprudelPattern._segment by dslPatternExtension { p, args, /* callInfo */ _ -> applySegment(p, args) }
internal val String._segment by dslStringExtension { p, args, callInfo -> p._segment(args, callInfo) }
internal val SprudelPattern._seg by dslPatternExtension { p, args, callInfo -> p._segment(args, callInfo) }
internal val String._seg by dslStringExtension { p, args, callInfo -> p._segment(args, callInfo) }
internal val _segment by dslPatternMapper { args, callInfo -> { p -> p._segment(args, callInfo) } }
internal val PatternMapperFn._segment by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_segment(args, callInfo))
}
internal val _seg by dslPatternMapper { args, callInfo -> { p -> p._seg(args, callInfo) } }
internal val PatternMapperFn._seg by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_seg(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Samples the pattern at a rate of `n` events per cycle.
 *
 * Useful for turning a continuous pattern (e.g. from a signal) into a discrete stepped one.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript
 * note(saw.range(40, 52).segment(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript
 * note(sine.range(48, 60).segment(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
fun SprudelPattern.segment(n: PatternLike): SprudelPattern = this._segment(listOf(n).asSprudelDslArgs())

/**
 * Like [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript
 * "0".segment(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
fun String.segment(n: PatternLike): SprudelPattern = this._segment(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that samples the source pattern at `n` events per cycle.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript
 * sine.range(40, 60).apply(segment(8)).note()  // via mapper
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@SprudelDsl
fun segment(n: PatternLike): PatternMapperFn = _segment(listOf(n).asSprudelDslArgs())

/** Chains a segment onto this [PatternMapperFn]; samples the result at `n` events per cycle. */
@SprudelDsl
fun PatternMapperFn.segment(n: PatternLike): PatternMapperFn = this._segment(listOf(n).asSprudelDslArgs())

/**
 * Alias for [segment] — samples the pattern at a rate of `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript
 * note(saw.range(40, 52).seg(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript
 * note(sine.range(48, 60).seg(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
fun SprudelPattern.seg(n: PatternLike): SprudelPattern = this._seg(listOf(n).asSprudelDslArgs())

/**
 * Alias for [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript
 * "0".seg(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
fun String.seg(n: PatternLike): SprudelPattern = this._seg(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that is an alias for [segment] — samples the source at `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript
 * sine.range(40, 60).apply(seg(8)).note()  // via mapper
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@SprudelDsl
fun seg(n: PatternLike): PatternMapperFn = _seg(listOf(n).asSprudelDslArgs())

/** Chains a seg onto this [PatternMapperFn]; alias for [PatternMapperFn.segment]. */
@SprudelDsl
fun PatternMapperFn.seg(n: PatternLike): PatternMapperFn = this._seg(listOf(n).asSprudelDslArgs())

// -- run() ------------------------------------------------------------------------------------------------------------

fun applyRun(n: Int): SprudelPattern {
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

internal val _run by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    applyRun(n)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a discrete pattern of integers from 0 to `n - 1`.
 *
 * Equivalent to `n("0 1 2 … n-1")`. Useful for driving scale or sample index patterns.
 *
 * @param n Number of steps; the pattern produces values 0, 1, …, n-1.
 * @return A sequential pattern of integers from 0 to n-1.
 *
 * ```KlangScript
 * n(run(4)).scale("C4:pentatonic")  // 4 scale degrees per cycle
 * ```
 *
 * ```KlangScript
 * n(run(8)).s("piano")  // 8 sequential notes
 * ```
 * @category structural
 * @tags run, sequence, range, index, discrete
 */
@SprudelDsl
fun run(n: Int): SprudelPattern = _run(listOf(n).asSprudelDslArgs())

// -- binaryN() --------------------------------------------------------------------------------------------------------

fun applyBinaryN(n: Int, bits: Int): SprudelPattern {
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

internal val _binaryN by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.value?.asIntOrNull() ?: 16
    applyBinaryN(n, bits)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a binary pattern from a number, padded to `bits` bits long (MSB first).
 *
 * @param n    The integer to convert to binary.
 * @param bits Total pattern length in bits (default 16).
 * @return A sequential pattern of 0s and 1s representing the binary value.
 *
 * ```KlangScript
 * s("hh").struct(binaryN(9, 4))  // 1 0 0 1 — binary 9 in 4 bits
 * ```
 *
 * ```KlangScript
 * s("hh").struct(binaryN(146, 8))  // 1 0 0 1 0 0 1 0 - binary 146 in 8 bits
 * ```
 * @category structural
 * @tags binaryN, binary, bits, structure, pattern
 */
@SprudelDsl
fun binaryN(n: Int, bits: Int = 16): SprudelPattern = _binaryN(listOf(n, bits).asSprudelDslArgs())

// -- binary() ---------------------------------------------------------------------------------------------------------

internal val _binary by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    if (n == 0) {
        applyBinaryN(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryN(n, bits)
    }
}

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a binary pattern from a number, with bit length calculated automatically.
 *
 * @param n The integer to convert to binary.
 * @return A sequential pattern of 0s and 1s with minimal bit width.
 *
 * ```KlangScript
 * s("hh").struct(binary(5))  // 1 0 1 — 3 bits
 * ```
 *
 * ```KlangScript
 * s("hh").struct(binary(13))  // 1 1 0 1 — 4 bits
 * ```
 * @category structural
 * @tags binary, binaryN, bits, structure, pattern
 */
@SprudelDsl
fun binary(n: Int): SprudelPattern = _binary(listOf(n).asSprudelDslArgs())

// -- binaryNL() -------------------------------------------------------------------------------------------------------

fun applyBinaryNL(n: Int, bits: Int): SprudelPattern {
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

internal val _binaryNL by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    val bits = args.getOrNull(1)?.value?.asIntOrNull() ?: 16
    applyBinaryNL(n, bits)
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("hh").struct(binaryNL(5, 4))  // list [0, 1, 0, 1]
 * ```
 *
 * ```KlangScript
 * s("hh").struct(binaryNL(255, 8))  // list [1, 1, 1, 1, 1, 1, 1, 1]
 * ```
 * @category structural
 * @tags binaryNL, binary, bits, list, structure
 */
@SprudelDsl
fun binaryNL(n: Int, bits: Int = 16): SprudelPattern = _binaryNL(listOf(n, bits).asSprudelDslArgs())

// -- binaryL() --------------------------------------------------------------------------------------------------------

internal val _binaryL by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0)?.value?.asIntOrNull() ?: 0
    if (n == 0) {
        applyBinaryNL(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryNL(n, bits)
    }
}

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a binary list pattern from a number, with bit length calculated automatically.
 *
 * Like [binary] but returns bits as a list value in a single event.
 *
 * @param n The integer to convert to binary.
 * @return A single-event pattern whose value is a list of 0s and 1s with minimal bit width.
 *
 * ```KlangScript
 * s("hh").struct(binaryL(5))  // list [1, 0, 1]
 * ```
 *
 * ```KlangScript
 * s("hh").struct(binaryL(13))  // list [1, 1, 0, 1]
 * ```
 * @category structural
 * @tags binaryL, binaryNL, binary, bits, list, structure
 */
@SprudelDsl
fun binaryL(n: Int): SprudelPattern = _binaryL(listOf(n).asSprudelDslArgs())

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

internal val _ratio by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(ratioMutation) }

internal val SprudelPattern._ratio by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    p.reinterpretVoice { it.ratioMutation(it.value?.asString) }
}

internal val String._ratio by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p._ratio() }

// ===== USER-FACING OVERLOADS =====

/**
 * Parses colon-separated ratios into numbers: `"5:4"` → 1.25, `"3:2"` → 1.5, `"12:3:2"` → 2.0.
 *
 * Useful for specifying tuning ratios or rhythmic proportions using familiar ratio notation.
 *
 * @param values One or more ratio strings or numbers to convert.
 * @return A pattern of the computed ratio values.
 *
 * ```KlangScript
 * ratio("5:4", "3:2", "2:1").note()  // major third, fifth, octave as ratios
 * ```
 *
 * ```KlangScript
 * ratio("3:2").note()  // perfect fifth ratio
 * ```
 * @category structural
 * @tags ratio, tuning, fraction, colon, notation
 */
@SprudelDsl
fun ratio(vararg values: PatternLike): SprudelPattern = _ratio(values.toList())

/** Converts colon-ratio notation in the pattern's values to numbers. */
@SprudelDsl
fun SprudelPattern.ratio(): SprudelPattern = this._ratio()

/** Converts colon-ratio notation in the mini-notation string to numbers. */
@SprudelDsl
fun String.ratio(): SprudelPattern = this._ratio()

// -- pace() / steps() -------------------------------------------------------------------------------------------------

fun applyPace(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asRationalOrNull() ?: Rational.ONE
    val currentSteps = source.numSteps ?: Rational.ONE

    if (targetSteps <= Rational.ZERO || currentSteps <= Rational.ZERO) {
        return source
    }

    // Calculate speed adjustment: fast(targetSteps / currentSteps)
    val speedFactor = targetSteps / currentSteps

    return source.fast(speedFactor)
}

internal val _pace by dslPatternMapper { args, callInfo -> { p -> p._pace(args, callInfo) } }
internal val SprudelPattern._pace by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPace(p, args)
}
internal val String._pace by dslStringExtension { p, args, callInfo -> p._pace(args, callInfo) }
internal val PatternMapperFn._pace by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pace(args, callInfo))
}

internal val _steps by dslPatternMapper { args, callInfo -> _pace(args, callInfo) }
internal val SprudelPattern._steps by dslPatternExtension { p, args, callInfo -> p._pace(args, callInfo) }
internal val String._steps by dslStringExtension { p, args, callInfo -> p._pace(args, callInfo) }
internal val PatternMapperFn._steps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pace(args, callInfo))
}

/**
 * Adjusts this pattern's speed so it plays exactly `n` steps per cycle.
 *
 * Computes the speed factor relative to the pattern's natural step count and applies [fast].
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript
 * note("c d e f").pace(8)   // 4-step pattern runs at 8 steps/cycle (double speed)
 * ```
 *
 * ```KlangScript
 * note("c d e f g").pace(4) // 5-step pattern runs at 4 steps/cycle
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@SprudelDsl
fun SprudelPattern.pace(n: PatternLike): SprudelPattern = this._pace(listOf(n).asSprudelDslArgs())

/** Adjusts this string pattern to play `n` steps per cycle. */
@SprudelDsl
fun String.pace(n: PatternLike): SprudelPattern = this._pace(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that adjusts the source to play `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return A [PatternMapperFn] that speeds up or slows down the source to fit `n` steps.
 *
 * ```KlangScript
 * note("c d e f").apply(pace(8))   // via mapper
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@SprudelDsl
fun pace(n: PatternLike): PatternMapperFn = _pace(listOf(n).asSprudelDslArgs())

/** Chains a pace onto this [PatternMapperFn]; adjusts to play `n` steps per cycle. */
@SprudelDsl
fun PatternMapperFn.pace(n: PatternLike): PatternMapperFn = this._pace(listOf(n).asSprudelDslArgs())

/**
 * Alias for [pace] — adjusts this pattern's speed so it plays `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript
 * note("c d e f").steps(8)   // 4-step pattern runs at 8 steps/cycle
 * ```
 *
 * @alias pace
 * @category structural
 * @tags steps, pace, tempo, speed, cycle
 */
@SprudelDsl
fun SprudelPattern.steps(n: PatternLike): SprudelPattern = this._steps(listOf(n).asSprudelDslArgs())

/** Alias for [pace] on a string pattern. */
@SprudelDsl
fun String.steps(n: PatternLike): SprudelPattern = this._steps(listOf(n).asSprudelDslArgs())

/** Returns a [PatternMapperFn] — alias for [pace] — that adjusts to play `n` steps per cycle. */
@SprudelDsl
fun steps(n: PatternLike): PatternMapperFn = _steps(listOf(n).asSprudelDslArgs())

/** Chains a steps (alias for [pace]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.steps(n: PatternLike): PatternMapperFn = this._steps(listOf(n).asSprudelDslArgs())

// -- take() -----------------------------------------------------------------------------------------------------------

fun applyTake(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _take by dslPatternMapper { args, callInfo -> { p -> p._take(args, callInfo) } }
internal val SprudelPattern._take by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTake(p, args)
}
internal val String._take by dslStringExtension { p, args, callInfo -> p._take(args, callInfo) }
internal val PatternMapperFn._take by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_take(args, callInfo))
}

/**
 * Keeps only the first `n` steps of this pattern, stretched to fill the cycle.
 *
 * @param n Number of steps to keep from the start.
 * @return A pattern containing only the first `n` steps, stretched to fill one cycle.
 *
 * ```KlangScript
 * note("c d e f").take(2)  // keeps "c d", stretched to fill the cycle
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").take(3)  // keeps first 3 sounds
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@SprudelDsl
fun SprudelPattern.take(n: PatternLike): SprudelPattern = this._take(listOf(n).asSprudelDslArgs())

/** Keeps the first `n` steps of this string pattern, stretched to fill the cycle. */
@SprudelDsl
fun String.take(n: PatternLike): SprudelPattern = this._take(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that keeps only the first `n` steps of the source.
 *
 * @param n Number of steps to keep from the start.
 * @return A [PatternMapperFn] that truncates the source to `n` steps.
 *
 * ```KlangScript
 * note("c d e f").apply(take(2))  // via mapper
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@SprudelDsl
fun take(n: PatternLike): PatternMapperFn = _take(listOf(n).asSprudelDslArgs())

/** Chains a take onto this [PatternMapperFn]; keeps only the first `n` steps. */
@SprudelDsl
fun PatternMapperFn.take(n: PatternLike): PatternMapperFn = this._take(listOf(n).asSprudelDslArgs())

// -- drop() -----------------------------------------------------------------------------------------------------------

fun applyDrop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _drop by dslPatternMapper { args, callInfo -> { p -> p._drop(args, callInfo) } }
internal val SprudelPattern._drop by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDrop(p, args)
}
internal val String._drop by dslStringExtension { p, args, callInfo -> p._drop(args, callInfo) }
internal val PatternMapperFn._drop by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_drop(args, callInfo))
}

/**
 * Skips the first `n` steps of this pattern and stretches the remainder to fill the cycle.
 *
 * Use a negative `n` to drop from the end instead.
 *
 * @param n Number of steps to skip from the start (negative = skip from end).
 * @return A pattern with the first `n` steps removed, stretched to fill one cycle.
 *
 * ```KlangScript
 * note("c d e f").drop(1)  // drops "c", plays "d e f" stretched
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").drop(2)  // drops "bd sd", plays "hh cp" stretched
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@SprudelDsl
fun SprudelPattern.drop(n: PatternLike): SprudelPattern = this._drop(listOf(n).asSprudelDslArgs())

/** Skips the first `n` steps of this string pattern, stretched to fill the cycle. */
@SprudelDsl
fun String.drop(n: PatternLike): SprudelPattern = this._drop(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that skips the first `n` steps of the source.
 *
 * @param n Number of steps to skip from the start.
 * @return A [PatternMapperFn] that drops the first `n` steps of the source.
 *
 * ```KlangScript
 * note("c d e f").apply(drop(1))  // via mapper
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@SprudelDsl
fun drop(n: PatternLike): PatternMapperFn = _drop(listOf(n).asSprudelDslArgs())

/** Chains a drop onto this [PatternMapperFn]; skips the first `n` steps. */
@SprudelDsl
fun PatternMapperFn.drop(n: PatternLike): PatternMapperFn = this._drop(listOf(n).asSprudelDslArgs())

// -- repeatCycles() ---------------------------------------------------------------------------------------------------

fun applyRepeatCycles(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _repeatCycles by dslPatternMapper { args, callInfo -> { p -> p._repeatCycles(args, callInfo) } }
internal val SprudelPattern._repeatCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRepeatCycles(p, args)
}
internal val String._repeatCycles by dslStringExtension { p, args, callInfo ->
    p._repeatCycles(args, callInfo)
}
internal val PatternMapperFn._repeatCycles by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_repeatCycles(args, callInfo))
}

/**
 * Repeats each cycle of this pattern `n` times before advancing.
 *
 * Cycle 0 plays `n` times, then cycle 1 plays `n` times, and so on. Supports control patterns.
 *
 * @param n Number of times to repeat each cycle.
 * @return A pattern where each cycle is repeated `n` times.
 *
 * ```KlangScript
 * note("c d e f").repeatCycles(3)    // each cycle repeats 3 times
 * ```
 *
 * ```KlangScript
 * s("bd sd").repeatCycles("<1 2 4>") // varying repetitions each cycle
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@SprudelDsl
fun SprudelPattern.repeatCycles(n: PatternLike): SprudelPattern =
    this._repeatCycles(listOf(n).asSprudelDslArgs())

/** Repeats each cycle of this string pattern `n` times. */
@SprudelDsl
fun String.repeatCycles(n: PatternLike): SprudelPattern =
    this._repeatCycles(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that repeats each cycle of the source `n` times.
 *
 * @param n Number of times to repeat each cycle.
 * @return A [PatternMapperFn] that repeats cycles.
 *
 * ```KlangScript
 * note("c d e f").apply(repeatCycles(3))  // via mapper
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@SprudelDsl
fun repeatCycles(n: PatternLike): PatternMapperFn = _repeatCycles(listOf(n).asSprudelDslArgs())

/** Chains a repeatCycles onto this [PatternMapperFn]; repeats each cycle `n` times. */
@SprudelDsl
fun PatternMapperFn.repeatCycles(n: PatternLike): PatternMapperFn =
    this._repeatCycles(listOf(n).asSprudelDslArgs())

// -- extend() ---------------------------------------------------------------------------------------------------------

internal val _extend by dslPatternMapper { args, callInfo -> { p -> p._fast(args, callInfo) } }
internal val SprudelPattern._extend by dslPatternExtension { p, args, callInfo -> p._fast(args, callInfo) }
internal val String._extend by dslStringExtension { p, args, callInfo -> p._extend(args, callInfo) }
internal val PatternMapperFn._extend by dslPatternMapperExtension { m, args, callInfo ->
    m.chain({ p -> p._fast(args, callInfo) })
}

/**
 * Speeds up this pattern by the given factor — alias for [fast].
 *
 * `extend(2)` is identical to `fast(2)`: events play twice as fast.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript
 * note("c d e f").extend(2)      // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript
 * s("bd sd hh").extend("<1 2 4>") // varying speed each cycle
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@SprudelDsl
fun SprudelPattern.extend(factor: PatternLike): SprudelPattern =
    this._extend(listOf(factor).asSprudelDslArgs())

/** Speeds up this string pattern by `factor` — alias for [fast]. */
@SprudelDsl
fun String.extend(factor: PatternLike): SprudelPattern =
    this._extend(listOf(factor).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that speeds up the source by `factor` — alias for [fast].
 *
 * @param factor Speed-up factor.
 * @return A [PatternMapperFn] that speeds up the source.
 *
 * ```KlangScript
 * note("c d e f").apply(extend(2))  // via mapper
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@SprudelDsl
fun extend(factor: PatternLike): PatternMapperFn = _extend(listOf(factor).asSprudelDslArgs())

/** Chains an extend (alias for [fast]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.extend(factor: PatternLike): PatternMapperFn =
    this._extend(listOf(factor).asSprudelDslArgs())

// -- iter() -----------------------------------------------------------------------------------------------------------

fun applyIter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _iter by dslPatternMapper { args, callInfo -> { p -> p._iter(args, callInfo) } }
internal val SprudelPattern._iter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIter(p, args)
}
internal val String._iter by dslStringExtension { p, args, callInfo -> p._iter(args, callInfo) }
internal val PatternMapperFn._iter by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_iter(args, callInfo))
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
 * ```KlangScript
 * note("c d e f").iter(4)  // cycle 0: c d e f, cycle 1: d e f c, …
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").iter(4)  // rotating drum pattern every cycle
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@SprudelDsl
fun SprudelPattern.iter(n: Int): SprudelPattern = this._iter(listOf(n).asSprudelDslArgs())

/** Rotates this string pattern forward by one slice each cycle, dividing into `n` slices. */
@SprudelDsl
fun String.iter(n: Int): SprudelPattern = this._iter(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that rotates the source forward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source forward each cycle.
 *
 * ```KlangScript
 * note("c d e f").apply(iter(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@SprudelDsl
fun iter(n: Int): PatternMapperFn = _iter(listOf(n).asSprudelDslArgs())

/** Chains an iter onto this [PatternMapperFn]; rotates forward by one slice per cycle. */
@SprudelDsl
fun PatternMapperFn.iter(n: Int): PatternMapperFn = this._iter(listOf(n).asSprudelDslArgs())

// -- iterBack() -------------------------------------------------------------------------------------------------------

fun applyIterBack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _iterBack by dslPatternMapper { args, callInfo -> { p -> p._iterBack(args, callInfo) } }
internal val SprudelPattern._iterBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIterBack(p, args)
}
internal val String._iterBack by dslStringExtension { p, args, callInfo -> p._iterBack(args, callInfo) }
internal val PatternMapperFn._iterBack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_iterBack(args, callInfo))
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
 * ```KlangScript
 * note("c d e f").iterBack(4)  // cycle 0: c d e f, cycle 1: f c d e, …
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").iterBack(4)  // backward-rotating drum pattern
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@SprudelDsl
fun SprudelPattern.iterBack(n: Int): SprudelPattern = this._iterBack(listOf(n).asSprudelDslArgs())

/** Rotates this string pattern backward by one slice each cycle, dividing into `n` slices. */
@SprudelDsl
fun String.iterBack(n: Int): SprudelPattern = this._iterBack(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that rotates the source backward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source backward each cycle.
 *
 * ```KlangScript
 * note("c d e f").apply(iterBack(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@SprudelDsl
fun iterBack(n: Int): PatternMapperFn = _iterBack(listOf(n).asSprudelDslArgs())

/** Chains an iterBack onto this [PatternMapperFn]; rotates backward by one slice per cycle. */
@SprudelDsl
fun PatternMapperFn.iterBack(n: Int): PatternMapperFn = this._iterBack(listOf(n).asSprudelDslArgs())

// -- invert() / inv() ------------------------------------------------------------------------------------------------

/**
 * Inverts boolean values in a pattern: true <-> false, 1 <-> 0.
 * Useful for inverting structural patterns and masks.
 *
 * JavaScript: `pat.fmap((x) => !x)`
 */
fun applyInvert(pattern: SprudelPattern): SprudelPattern {
    return pattern.mapEvents { event ->
        val currentBool = event.data.value?.asBoolean ?: false
        val invertedBool = !currentBool
        event.copy(data = event.data.copy(value = SprudelVoiceValue.Bool(invertedBool)))
    }
}

internal val _invert by dslPatternMapper { args, callInfo -> { p -> p._invert(args, callInfo) } }
internal val _inv by dslPatternMapper { args, callInfo -> _invert(args, callInfo) }

internal val SprudelPattern._invert by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyInvert(p)
}
internal val SprudelPattern._inv by dslPatternExtension { p, args, callInfo -> p._invert(args, callInfo) }
internal val String._invert by dslStringExtension { p, args, callInfo -> p._invert(args, callInfo) }
internal val String._inv by dslStringExtension { p, args, callInfo -> p._inv(args, callInfo) }

internal val PatternMapperFn._invert by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_invert(args, callInfo))
}
internal val PatternMapperFn._inv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_invert(args, callInfo))
}

/**
 * Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * Useful for flipping structural masks so that silent beats become active and vice-versa.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript
 * "1 0 1 1".invert()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript
 * note("c d e f").struct("1 0 1 0").invert() // swaps active and silent beats
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
fun SprudelPattern.invert(): SprudelPattern = this._invert()

/** Inverts boolean values in this string pattern. */
@SprudelDsl
fun String.invert(): SprudelPattern = this._invert()

/**
 * Returns a [PatternMapperFn] that inverts boolean values in the source pattern.
 *
 * @return A [PatternMapperFn] that toggles all boolean values.
 *
 * ```KlangScript
 * seq("1 0 1 1").apply(invert())  // via mapper
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
fun invert(): PatternMapperFn = _invert(emptyList())

/** Chains an invert onto this [PatternMapperFn]; toggles all boolean values. */
@SprudelDsl
fun PatternMapperFn.invert(): PatternMapperFn = this._invert(emptyList())

/**
 * Alias for [invert]. Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript
 * "1 0 1 1".inv()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript
 * note("c d e f").struct("1 0 1 0").inv() // swaps active and silent beats
 * ```
 *
 * @alias invert
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@SprudelDsl
fun SprudelPattern.inv(): SprudelPattern = this._inv()

/** Alias for [invert] on a string pattern. */
@SprudelDsl
fun String.inv(): SprudelPattern = this._inv()

/** Returns a [PatternMapperFn] — alias for [invert] — that toggles all boolean values. */
@SprudelDsl
fun inv(): PatternMapperFn = _inv(emptyList())

/** Chains an inv (alias for [invert]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.inv(): PatternMapperFn = this._inv(emptyList())

// -- applyN() --------------------------------------------------------------------------------------------------------

/**
 * Applies a function to a pattern n times sequentially.
 * Supports control patterns for n.
 *
 * Example: `pattern.applyN(3, x => x.fast(2))` applies fast(2) three times
 *
 * JavaScript: `applyN(n, func, pat)`
 */
fun applyApplyN(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return pattern

    // Use _innerJoin to support control patterns for n
    return pattern._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 0

        var result = src
        repeat(n) { result = transform(result) }
        result
    }
}

internal val _applyN by dslPatternMapper { args, callInfo -> { p -> p._applyN(args, callInfo) } }
internal val SprudelPattern._applyN by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyApplyN(p, args)
}
internal val String._applyN by dslStringExtension { p, args, callInfo -> p._applyN(args, callInfo) }
internal val PatternMapperFn._applyN by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_applyN(args, callInfo))
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
 * ```KlangScript
 * note("c d e f").applyN(2, x => x.fast(2))      // fast(2) applied twice
 * ```
 *
 * ```KlangScript
 * s("bd sd").applyN(3, x => x.echo(2, 0.25, 0.5)) // echo applied 3 times
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@SprudelDsl
fun SprudelPattern.applyN(n: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._applyN(listOf(n, transform).asSprudelDslArgs())

/** Applies `transform` to this string pattern `n` times. */
@SprudelDsl
fun String.applyN(n: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._applyN(listOf(n, transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies `transform` to the source `n` times.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly.
 * @return A [PatternMapperFn] that applies `transform` `n` times.
 *
 * ```KlangScript
 * note("c d e f").apply(applyN(2, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@SprudelDsl
fun applyN(n: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _applyN(listOf(n, transform).asSprudelDslArgs())

/** Chains an applyN onto this [PatternMapperFn]; applies `transform` `n` times. */
@SprudelDsl
fun PatternMapperFn.applyN(n: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    this._applyN(listOf(n, transform).asSprudelDslArgs())

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
fun applyPressBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _pressBy by dslPatternMapper { args, callInfo -> { p -> p._pressBy(args, callInfo) } }
internal val SprudelPattern._pressBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPressBy(p, args)
}
internal val String._pressBy by dslStringExtension { p, args, callInfo -> p._pressBy(args, callInfo) }
internal val PatternMapperFn._pressBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pressBy(args, callInfo))
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
 * ```KlangScript
 * s("bd mt sd ht").pressBy(0.5)          // classic syncopation
 * ```
 *
 * ```KlangScript
 * s("bd mt sd ht").pressBy("<0 0.5 0.25>") // varying syncopation each cycle
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@SprudelDsl
fun SprudelPattern.pressBy(r: PatternLike): SprudelPattern =
    this._pressBy(listOf(r).asSprudelDslArgs())

/** Syncopates this string pattern by compressing events to start at position `r`. */
@SprudelDsl
fun String.pressBy(r: PatternLike): SprudelPattern =
    this._pressBy(listOf(r).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that syncopates the source by compressing events to position `r`.
 *
 * @param r Compression ratio in the range [0, 1].
 * @return A [PatternMapperFn] that syncopates the source.
 *
 * ```KlangScript
 * s("bd mt sd ht").apply(pressBy(0.5))   // classic syncopation via mapper
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@SprudelDsl
fun pressBy(r: PatternLike): PatternMapperFn = _pressBy(listOf(r).asSprudelDslArgs())

/** Chains a pressBy onto this [PatternMapperFn]; compresses events to position `r`. */
@SprudelDsl
fun PatternMapperFn.pressBy(r: PatternLike): PatternMapperFn = this._pressBy(listOf(r).asSprudelDslArgs())

// -- press() ----------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by shifting each event halfway into its timespan.
 * Equivalent to `pressBy(0.5)`.
 *
 * Example: s("bd mt sd ht").every(4, { it.press() })
 */
fun applyPress(pattern: SprudelPattern): SprudelPattern {
    return applyPressBy(pattern, listOf(SprudelDslArg.of(0.5)))
}

internal val _press by dslPatternMapper { args, callInfo -> { p -> p._press(args, callInfo) } }
internal val SprudelPattern._press by dslPatternExtension { p, _, /* callInfo */ _ ->
    applyPress(p)
}
internal val String._press by dslStringExtension { p, args, callInfo -> p._press(args, callInfo) }
internal val PatternMapperFn._press by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_press(args, callInfo))
}

/**
 * Syncopates this pattern by shifting each event halfway into its timespan.
 *
 * Equivalent to `pressBy(0.5)`. Creates a classic off-beat feel.
 *
 * @return A syncopated pattern where events start halfway through their slots.
 *
 * ```KlangScript
 * s("bd mt sd ht").press()                   // classic off-beat feel
 * ```
 *
 * ```KlangScript
 * note("c d e f").every(4, x => x.press())   // press every 4th cycle
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@SprudelDsl
fun SprudelPattern.press(): SprudelPattern = this._press()

/** Syncopates this string pattern by shifting events halfway into their timespan. */
@SprudelDsl
fun String.press(): SprudelPattern = this._press()

/**
 * Returns a [PatternMapperFn] that syncopates the source by shifting events halfway.
 *
 * Equivalent to `pressBy(0.5)` as a mapper. Apply using `.apply()`.
 *
 * @return A [PatternMapperFn] that shifts each event halfway into its slot.
 *
 * ```KlangScript
 * s("bd mt sd ht").apply(press())   // classic off-beat feel via mapper
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@SprudelDsl
fun press(): PatternMapperFn = _press(emptyList())

/** Chains a press onto this [PatternMapperFn]; shifts each event halfway into its timespan. */
@SprudelDsl
fun PatternMapperFn.press(): PatternMapperFn = this._press(emptyList())

// -- ribbon() ---------------------------------------------------------------------------------------------------------

fun applyRibbon(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val SprudelPattern._ribbon by dslPatternExtension { p, args, /* callInfo */ _ -> applyRibbon(p, args) }
internal val String._ribbon by dslStringExtension { p, args, callInfo -> p._ribbon(args, callInfo) }
internal val SprudelPattern._rib by dslPatternExtension { p, args, callInfo -> p._ribbon(args, callInfo) }
internal val String._rib by dslStringExtension { p, args, callInfo -> p._ribbon(args, callInfo) }
internal val _ribbon by dslPatternMapper { args, callInfo -> { p -> p._ribbon(args, callInfo) } }
internal val PatternMapperFn._ribbon by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ribbon(args, callInfo))
}
internal val _rib by dslPatternMapper { args, callInfo -> { p -> p._rib(args, callInfo) } }
internal val PatternMapperFn._rib by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rib(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("<c d e f>").ribbon(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").ribbon(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
fun SprudelPattern.ribbon(offset: PatternLike, cycles: PatternLike = 1.0): SprudelPattern =
    this._ribbon(listOf(offset, cycles).asSprudelDslArgs())

/**
 * Loops a segment of the mini-notation string pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript
 * "bd sd hh cp".ribbon(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
fun String.ribbon(offset: PatternLike, cycles: PatternLike = 1.0): SprudelPattern =
    this._ribbon(listOf(offset, cycles).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that loops a segment of the source pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript
 * note("<c d e f>").apply(ribbon(1, 2))  // via mapper
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@SprudelDsl
fun ribbon(offset: PatternLike, cycles: PatternLike = 1.0): PatternMapperFn =
    _ribbon(listOf(offset, cycles).asSprudelDslArgs())

/** Chains a ribbon onto this [PatternMapperFn]; loops a segment of the result starting at `offset`. */
@SprudelDsl
fun PatternMapperFn.ribbon(offset: PatternLike, cycles: PatternLike = 1.0): PatternMapperFn =
    this._ribbon(listOf(offset, cycles).asSprudelDslArgs())

/**
 * Alias for [ribbon]. Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript
 * note("<c d e f>").rib(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").rib(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
fun SprudelPattern.rib(offset: PatternLike, cycles: PatternLike = 1.0): SprudelPattern =
    this._rib(listOf(offset, cycles).asSprudelDslArgs())

/**
 * Alias for [ribbon] applied to a mini-notation string.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript
 * "bd sd hh cp".rib(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
fun String.rib(offset: PatternLike, cycles: PatternLike = 1.0): SprudelPattern =
    this._rib(listOf(offset, cycles).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that is an alias for [ribbon] — loops a segment of the source pattern.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript
 * note("<c d e f>").apply(rib(1, 2))  // via mapper
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@SprudelDsl
fun rib(offset: PatternLike, cycles: PatternLike = 1.0): PatternMapperFn =
    _rib(listOf(offset, cycles).asSprudelDslArgs())

/** Chains a rib onto this [PatternMapperFn]; alias for [PatternMapperFn.ribbon]. */
@SprudelDsl
fun PatternMapperFn.rib(offset: PatternLike, cycles: PatternLike = 1.0): PatternMapperFn =
    this._rib(listOf(offset, cycles).asSprudelDslArgs())
