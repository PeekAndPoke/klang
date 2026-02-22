@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.addons.timeLoop
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.lcm
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
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

// -- hush() / bypass() / mute() --------------------------------------------------------------------------------------

/**
 * Applies hush/mute with control pattern support.
 * Returns silence when the condition is true.
 * When called without arguments, unconditionally returns silence.
 */
fun applyHush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

// delegates - still register with KlangScript
internal val _hush by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._hush by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._hush by dslStringExtension { p, args, callInfo -> p._hush(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Silences the pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition
 *
 * ```KlangScript
 * s("bd sd").hush()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").hush("<1 0>")  // Silence on odd cycles, play on even
 * ```
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@StrudelDsl
fun hush(vararg args: PatternLike): StrudelPattern = _hush(args.toList())

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when truthy.
 *
 * ```KlangScript
 * s("bd sd").hush()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").hush("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun StrudelPattern.hush(vararg args: PatternLike): StrudelPattern = this._hush(args.toList())

/**
 * Parses this string as a pattern and silences it. Without arguments, unconditionally returns silence.
 *
 * ```KlangScript
 * "bd sd".hush()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * "bd sd".hush("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun String.hush(vararg args: PatternLike): StrudelPattern = this._hush(args.toList())

// delegates - still register with KlangScript
internal val _bypass by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._bypass by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._bypass by dslStringExtension { p, args, callInfo -> p._bypass(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Alias for [hush]. Silences the pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition
 *
 * ```KlangScript
 * s("bd sd").bypass()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").bypass("<1 0>")  // Silence on odd cycles, play on even
 * ```
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@StrudelDsl
fun bypass(vararg args: PatternLike): StrudelPattern = _bypass(args.toList())

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when truthy.
 *
 * ```KlangScript
 * s("bd sd").bypass()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").bypass("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun StrudelPattern.bypass(vararg args: PatternLike): StrudelPattern = this._bypass(args.toList())

/**
 * Parses this string as a pattern and silences it. Without arguments, unconditionally returns silence.
 *
 * ```KlangScript
 * "bd sd".bypass()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * "bd sd".bypass("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun String.bypass(vararg args: PatternLike): StrudelPattern = this._bypass(args.toList())

// delegates - still register with KlangScript
internal val _mute by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._mute by dslPatternExtension { p, args, /* callInfo */ _ -> applyHush(p, args) }
internal val String._mute by dslStringExtension { p, args, callInfo -> p._mute(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Alias for [hush]. Silences the pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition
 *
 * ```KlangScript
 * s("bd sd").mute()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").mute("<1 0>")  // Silence on odd cycles, play on even
 * ```
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@StrudelDsl
fun mute(vararg args: PatternLike): StrudelPattern = _mute(args.toList())

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when truthy.
 *
 * ```KlangScript
 * s("bd sd").mute()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * s("bd sd").mute("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun StrudelPattern.mute(vararg args: PatternLike): StrudelPattern = this._mute(args.toList())

/**
 * Parses this string as a pattern and silences it. Without arguments, unconditionally returns silence.
 *
 * ```KlangScript
 * "bd sd".mute()  // Unconditional silence
 * ```
 *
 * ```KlangScript
 * "bd sd".mute("<1 0>")  // Silent on odd cycles, audible on even
 * ```
 */
@StrudelDsl
fun String.mute(vararg args: PatternLike): StrudelPattern = this._mute(args.toList())

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates a silent pattern occupying the given number of steps. Supports control patterns via _innerJoin. */
fun applyGap(args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
internal val StrudelPattern._gap by dslPatternExtension { _, args, /* callInfo */ _ -> applyGap(args) }
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
@StrudelDsl
fun gap(vararg steps: PatternLike): StrudelPattern = _gap(steps.toList())

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
@StrudelDsl
fun StrudelPattern.gap(vararg steps: PatternLike): StrudelPattern = this._gap(steps.toList())

/**
 * Replaces this string pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript
 * seq("bd", "hh".gap(), "sd").s()  // Middle step replaced by silence
 * ```
 */
@StrudelDsl
fun String.gap(vararg steps: PatternLike): StrudelPattern = this._gap(steps.toList())

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
fun applySeq(patterns: List<StrudelPattern>): StrudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

// delegates - still register with KlangScript
internal val _seq by dslPatternFunction { args, /* callInfo */ _ -> applySeq(args.toListOfPatterns()) }

internal val StrudelPattern._seq by dslPatternExtension { p, args, /* callInfo */ _ ->
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
 * @tags sequence, timing, control, order
 */
@StrudelDsl
fun seq(vararg patterns: PatternLike): StrudelPattern {
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
@StrudelDsl
fun StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern {
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
@StrudelDsl
fun String.seq(vararg patterns: PatternLike): StrudelPattern {
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
@StrudelDsl
fun mini(vararg patterns: PatternLike): StrudelPattern = _mini(patterns.toList())

/**
 * Parses this string as mini-notation and returns the resulting pattern.
 *
 * ```KlangScript
 * "c d e f".mini().note()  // Four notes from mini-notation string
 * ```
 */
@StrudelDsl
fun String.mini(): StrudelPattern = this._mini()

// -- stack() ----------------------------------------------------------------------------------------------------------

fun applyStack(patterns: List<StrudelPattern>): StrudelPattern {
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

internal val StrudelPattern._stack by dslPatternExtension { p, args, /* callInfo */ _ ->
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
@StrudelDsl
fun stack(vararg patterns: PatternLike): StrudelPattern = _stack(patterns.toList())

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
@StrudelDsl
fun StrudelPattern.stack(vararg patterns: PatternLike): StrudelPattern = this._stack(patterns.toList())

/**
 * Parses this string as a pattern and layers it together with additional patterns.
 *
 * ```KlangScript
 * "c e g".stack("g b d").note()  // Two chord voicings layered
 * ```
 */
@StrudelDsl
fun String.stack(vararg patterns: PatternLike): StrudelPattern = this._stack(patterns.toList())

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

// delegates - still register with KlangScript
internal val _arrange by dslPatternFunction { args, /* callInfo */ _ -> applyArrange(args) }

internal val StrudelPattern._arrange by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArrange(listOf(StrudelDslArg.of(p)) + args)
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
@StrudelDsl
fun arrange(vararg segments: PatternLike): StrudelPattern = _arrange(segments.toList())

/**
 * Prepends this pattern (duration 1) and plays it followed by the given segments.
 *
 * ```KlangScript
 * note("c e g").arrange([2, note("f a c")]).s("piano")  // 1 cycle chord, then 2 cycles
 * ```
 */
@StrudelDsl
fun StrudelPattern.arrange(vararg segments: PatternLike): StrudelPattern = this._arrange(segments.toList())

/**
 * Parses this string as a pattern (duration 1) and arranges it together with the given segments.
 *
 * ```KlangScript
 * "c e g".arrange([2, "f a c"]).note()  // 1 cycle, then 2 cycles of second chord
 * ```
 */
@StrudelDsl
fun String.arrange(vararg segments: PatternLike): StrudelPattern = this._arrange(segments.toList())

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

// delegates - still register with KlangScript
internal val _stepcat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val StrudelPattern._stepcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(StrudelDslArg.of(p)) + args)
}

internal val String._stepcat by dslStringExtension { p, args, callInfo -> p._stepcat(args, callInfo) }

internal val _timeCat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val StrudelPattern._timeCat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(StrudelDslArg.of(p)) + args)
}

internal val String._timeCat by dslStringExtension { p, args, callInfo -> p._timeCat(args, callInfo) }

internal val _timecat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val StrudelPattern._timecat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(StrudelDslArg.of(p)) + args)
}

internal val String._timecat by dslStringExtension { p, args, callInfo -> p._timecat(args, callInfo) }

internal val _s_cat by dslPatternFunction { args, /* callInfo */ _ -> applyStepcat(args) }

internal val StrudelPattern._s_cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStepcat(listOf(StrudelDslArg.of(p)) + args)
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
@StrudelDsl
fun stepcat(vararg segments: PatternLike): StrudelPattern = _stepcat(segments.toList())

/**
 * Prepends this pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * note("c").stepcat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun StrudelPattern.stepcat(vararg segments: PatternLike): StrudelPattern = this._stepcat(segments.toList())

/**
 * Parses this string as a pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".stepcat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun String.stepcat(vararg segments: PatternLike): StrudelPattern = this._stepcat(segments.toList())

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
@StrudelDsl
fun timeCat(vararg segments: PatternLike): StrudelPattern = _timeCat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").timeCat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun StrudelPattern.timeCat(vararg segments: PatternLike): StrudelPattern = this._timeCat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".timeCat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun String.timeCat(vararg segments: PatternLike): StrudelPattern = this._timeCat(segments.toList())

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
@StrudelDsl
fun timecat(vararg segments: PatternLike): StrudelPattern = _timecat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").timecat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun StrudelPattern.timecat(vararg segments: PatternLike): StrudelPattern = this._timecat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".timecat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun String.timecat(vararg segments: PatternLike): StrudelPattern = this._timecat(segments.toList())

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
@StrudelDsl
fun s_cat(vararg segments: PatternLike): StrudelPattern = _s_cat(segments.toList())

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript
 * note("c").s_cat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun StrudelPattern.s_cat(vararg segments: PatternLike): StrudelPattern = this._s_cat(segments.toList())

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript
 * "c".s_cat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@StrudelDsl
fun String.s_cat(vararg segments: PatternLike): StrudelPattern = this._s_cat(segments.toList())

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

internal val StrudelPattern._polyrhythm by dslPatternExtension { p, args, /* callInfo */ _ ->
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
@StrudelDsl
fun stackBy(alignment: PatternLike, vararg patterns: PatternLike): StrudelPattern =
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
@StrudelDsl
fun stackLeft(vararg patterns: PatternLike): StrudelPattern = _stackLeft(patterns.toList())

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
@StrudelDsl
fun stackRight(vararg patterns: PatternLike): StrudelPattern = _stackRight(patterns.toList())

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
@StrudelDsl
fun stackCentre(vararg patterns: PatternLike): StrudelPattern = _stackCentre(patterns.toList())

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
@StrudelDsl
fun polyrhythm(vararg patterns: PatternLike): StrudelPattern = _polyrhythm(patterns.toList())

/**
 * Alias for [stack]. Layers this pattern together with additional patterns simultaneously.
 *
 * ```KlangScript
 * s("bd sd").polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@StrudelDsl
fun StrudelPattern.polyrhythm(vararg patterns: PatternLike): StrudelPattern = this._polyrhythm(patterns.toList())

/**
 * Alias for [stack]. Parses this string as a pattern and layers it with additional patterns.
 *
 * ```KlangScript
 * "bd sd".polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@StrudelDsl
fun String.polyrhythm(vararg patterns: PatternLike): StrudelPattern = this._polyrhythm(patterns.toList())

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
@StrudelDsl
fun sequenceP(vararg patterns: PatternLike): StrudelPattern = _sequenceP(patterns.toList())

// -- cat() ------------------------------------------------------------------------------------------------------------

fun applyCat(patterns: List<StrudelPattern>): StrudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : StrudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Rational? = null

        override fun estimateCycleDuration(): Rational {
            return patterns.fold(Rational.ZERO) { acc, p -> acc + p.estimateCycleDuration() }
        }

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            val totalDuration = estimateCycleDuration()
            if (totalDuration <= Rational.ZERO) return emptyList()

            val result = mutableListOf<StrudelPatternEvent>()

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

internal val StrudelPattern._cat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCat(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._cat by dslStringExtension { p, args, callInfo -> p._cat(args, callInfo) }

internal val _fastcat by dslPatternFunction { args, /* callInfo */ _ -> applySeq(patterns = args.toListOfPatterns()) }

internal val StrudelPattern._fastcat by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySeq(patterns = listOf(p) + args.toListOfPatterns())
}

internal val String._fastcat by dslStringExtension { p, args, callInfo -> p._fastcat(args, callInfo) }

internal val _slowcat by dslPatternFunction { args, /* callInfo */ _ -> applyCat(patterns = args.toListOfPatterns()) }

internal val StrudelPattern._slowcat by dslPatternExtension { p, args, /* callInfo */ _ ->
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
@StrudelDsl
fun cat(vararg patterns: PatternLike): StrudelPattern = _cat(patterns.toList())

/**
 * Appends patterns to this pattern, each playing for its natural cycle duration.
 *
 * ```KlangScript
 * note("c d").cat(note("e f g"))  // "c d" then "e f g" in sequence
 * ```
 */
@StrudelDsl
fun StrudelPattern.cat(vararg patterns: PatternLike): StrudelPattern = this._cat(patterns.toList())

/**
 * Parses this string as a pattern and concatenates it with the given patterns in sequence.
 *
 * ```KlangScript
 * "c d".cat("e f g").note()  // "c d" then "e f g" in sequence
 * ```
 */
@StrudelDsl
fun String.cat(vararg patterns: PatternLike): StrudelPattern = this._cat(patterns.toList())

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
@StrudelDsl
fun fastcat(vararg patterns: PatternLike): StrudelPattern = _fastcat(patterns.toList())

/**
 * Alias for [seq]. Appends patterns to this pattern, squeezing all into one cycle.
 *
 * ```KlangScript
 * s("bd").fastcat(s("sd"))  // "bd sd" squeezed into one cycle
 * ```
 */
@StrudelDsl
fun StrudelPattern.fastcat(vararg patterns: PatternLike): StrudelPattern = this._fastcat(patterns.toList())

/**
 * Alias for [seq]. Parses this string and squeezes it together with the given patterns into one cycle.
 *
 * ```KlangScript
 * "bd".fastcat("sd").s()  // "bd sd" squeezed into one cycle
 * ```
 */
@StrudelDsl
fun String.fastcat(vararg patterns: PatternLike): StrudelPattern = this._fastcat(patterns.toList())

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
@StrudelDsl
fun slowcat(vararg patterns: PatternLike): StrudelPattern = _slowcat(patterns.toList())

/**
 * Alias for [cat]. Appends patterns to this pattern, each taking one full cycle.
 *
 * ```KlangScript
 * s("bd sd").slowcat(s("hh hh hh hh"))  // alternates each cycle
 * ```
 */
@StrudelDsl
fun StrudelPattern.slowcat(vararg patterns: PatternLike): StrudelPattern = this._slowcat(patterns.toList())

/**
 * Alias for [cat]. Parses this string and concatenates with the given patterns, each taking one cycle.
 *
 * ```KlangScript
 * "bd sd".slowcat("hh hh hh hh").s()  // alternates each cycle
 * ```
 */
@StrudelDsl
fun String.slowcat(vararg patterns: PatternLike): StrudelPattern = this._slowcat(patterns.toList())

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

// delegates - still register with KlangScript
internal val _slowcatPrime by dslPatternFunction { args, /* callInfo */ _ ->
    applySlowcatPrime(patterns = args.toListOfPatterns())
}

internal val StrudelPattern._slowcatPrime by dslPatternExtension { p, args, /* callInfo */ _ ->
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
@StrudelDsl
fun slowcatPrime(vararg patterns: PatternLike): StrudelPattern = _slowcatPrime(patterns.toList())

/**
 * Appends patterns to this pattern, cycling through them one per cycle at absolute time.
 *
 * ```KlangScript
 * note("c d").slowcatPrime(note("e f g"))  // cycles through at absolute time
 * ```
 */
@StrudelDsl
fun StrudelPattern.slowcatPrime(vararg patterns: PatternLike): StrudelPattern = this._slowcatPrime(patterns.toList())

/**
 * Parses this string and cycles through it with given patterns, one per cycle at absolute time.
 *
 * ```KlangScript
 * "c d".slowcatPrime("e f g").note()  // cycles through at absolute time
 * ```
 */
@StrudelDsl
fun String.slowcatPrime(vararg patterns: PatternLike): StrudelPattern = this._slowcatPrime(patterns.toList())

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

internal val StrudelPattern._polymeter by dslPatternExtension { p, args, /* callInfo */ _ ->
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
    AtomicPattern(StrudelVoiceData.empty.copy(value = value?.asVoiceValue()))
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
@StrudelDsl
fun polymeter(vararg patterns: PatternLike): StrudelPattern = _polymeter(patterns.toList())

/**
 * Prepends this pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript
 * note("c d").polymeter(note("c d e"))  // 2 and 3 steps aligned to LCM
 * ```
 */
@StrudelDsl
fun StrudelPattern.polymeter(vararg patterns: PatternLike): StrudelPattern = this._polymeter(patterns.toList())

/**
 * Parses this string as a pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript
 * "c d".polymeter("c d e").note()  // 2 and 3 steps aligned to LCM
 * ```
 */
@StrudelDsl
fun String.polymeter(vararg patterns: PatternLike): StrudelPattern = this._polymeter(patterns.toList())

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
@StrudelDsl
fun polymeterSteps(vararg args: PatternLike): StrudelPattern = _polymeterSteps(args.toList())

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
@StrudelDsl
fun pure(value: PatternLike): StrudelPattern = _pure(listOf(value))

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

// delegates - still register with KlangScript
internal val _struct by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslPatternFunction silence
    val pattern = listOf(args[1]).toPattern()
    applyStruct(source = pattern, structArg = args.getOrNull(0))
}

internal val StrudelPattern._struct by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStruct(source = p, structArg = args.firstOrNull())
}

internal val String._struct by dslStringExtension { p, args, callInfo -> p._struct(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Restructures the pattern using the timing of a mask pattern, keeping only truthy mask events.
 *
 * The mask provides the rhythmic structure; the source pattern provides the values. Only source
 * events that overlap with truthy events in the mask are kept, clipped to the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm
 *
 * ```KlangScript
 * s("hh").struct("x ~ x x ~ x x ~")  // hats shaped by a boolean rhythm pattern
 * ```
 *
 * ```KlangScript
 * note("c e g").struct("x*4")  // chord hits restructured to 4 equal beats
 * ```
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@StrudelDsl
fun struct(vararg args: PatternLike): StrudelPattern = _struct(args.toList())

/**
 * Restructures this pattern using the timing of the mask, keeping only truthy mask events.
 *
 * ```KlangScript
 * s("hh").struct("x ~ x x")  // hats shaped by a boolean rhythm
 * ```
 */
@StrudelDsl
fun StrudelPattern.struct(vararg args: PatternLike): StrudelPattern = this._struct(args.toList())

/**
 * Parses this string as a pattern and restructures it using the mask's timing.
 *
 * ```KlangScript
 * "hh".struct("x ~ x x").s()  // hats shaped by a boolean rhythm
 * ```
 */
@StrudelDsl
fun String.struct(vararg args: PatternLike): StrudelPattern = this._struct(args.toList())

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

// delegates - still register with KlangScript
internal val _structAll by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslPatternFunction silence
    val pattern = listOf(args[1]).toPattern()
    applyStructAll(source = pattern, structArg = args.getOrNull(0))
}

internal val StrudelPattern._structAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStructAll(source = p, structArg = args.firstOrNull())
}

internal val String._structAll by dslStringExtension { p, args, callInfo -> p._structAll(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Like [struct], but keeps all source events that overlap with the mask — including falsy ones.
 *
 * While [struct] filters to only truthy mask events, `structAll` uses the mask purely for timing
 * without filtering by value. Useful when the mask defines structure but all events should pass.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm, keeping all overlapping events
 *
 * ```KlangScript
 * note("c e").structAll("x x x x")  // both c and e kept within each mask window
 * ```
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@StrudelDsl
fun structAll(vararg args: PatternLike): StrudelPattern = _structAll(args.toList())

/**
 * Like [struct], but keeps all source events overlapping the mask regardless of truthiness.
 *
 * ```KlangScript
 * note("c e g").structAll("x x")  // all chord notes kept within each x window
 * ```
 */
@StrudelDsl
fun StrudelPattern.structAll(vararg args: PatternLike): StrudelPattern = this._structAll(args.toList())

/**
 * Like [struct], but keeps all source events overlapping the mask regardless of truthiness.
 *
 * ```KlangScript
 * "c e g".structAll("x x").note()  // all chord notes kept within each x window
 * ```
 */
@StrudelDsl
fun String.structAll(vararg args: PatternLike): StrudelPattern = this._structAll(args.toList())

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

// delegates - still register with KlangScript
internal val _mask by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslPatternFunction silence
    val pattern = listOf(args[1]).toPattern()
    applyMask(source = pattern, maskArg = args.getOrNull(0))
}

internal val StrudelPattern._mask by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMask(source = p, maskArg = args.firstOrNull())
}

internal val String._mask by dslStringExtension { p, args, callInfo -> p._mask(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Filters the pattern using a boolean mask, keeping source events that overlap truthy mask events.
 *
 * Unlike [struct], which uses the mask's timing for structure, `mask` keeps the source pattern's
 * original timing and simply gates out events where the mask is falsy.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return The source pattern gated by the mask
 *
 * ```KlangScript
 * s("bd sd hh cp").mask("1 0 1 1")  // second beat silenced by the mask
 * ```
 *
 * ```KlangScript
 * note("c d e f").mask("<1 0>")  // entire pattern alternates on/off each cycle
 * ```
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@StrudelDsl
fun mask(vararg args: PatternLike): StrudelPattern = _mask(args.toList())

/**
 * Filters this pattern using a boolean mask, keeping events that overlap truthy mask events.
 *
 * ```KlangScript
 * s("bd sd hh cp").mask("1 0 1 1")  // second beat silenced
 * ```
 */
@StrudelDsl
fun StrudelPattern.mask(vararg args: PatternLike): StrudelPattern = this._mask(args.toList())

/**
 * Parses this string as a pattern and filters it using a boolean mask.
 *
 * ```KlangScript
 * "bd sd hh cp".mask("1 0 1 1").s()  // second beat silenced
 * ```
 */
@StrudelDsl
fun String.mask(vararg args: PatternLike): StrudelPattern = this._mask(args.toList())

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

// delegates - still register with KlangScript
internal val _maskAll by dslPatternFunction { args, /* callInfo */ _ ->
    val maskArg = args.getOrNull(0)
    val source = args.map { it.value }.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg?.value is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslPatternFunction silence
    applyMaskAll(source, maskArg)
}

internal val StrudelPattern._maskAll by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyMaskAll(source = p, maskArg = args.firstOrNull())
}

internal val String._maskAll by dslStringExtension { p, args, callInfo -> p._maskAll(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * @param mask Pattern that defines the gating structure (all values allowed, not just truthy).
 * @return The source pattern gated by the mask's structure
 *
 * ```KlangScript
 * note("c d e f").maskAll("x ~ x ~")  // every other beat silenced by structure
 * ```
 * @category structural
 * @tags mask, gate, filter, rhythm
 */
@StrudelDsl
fun maskAll(vararg args: PatternLike): StrudelPattern = _maskAll(args.toList())

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * ```KlangScript
 * note("c d e f").maskAll("x ~ x ~")  // every other beat silenced
 * ```
 */
@StrudelDsl
fun StrudelPattern.maskAll(vararg args: PatternLike): StrudelPattern = this._maskAll(args.toList())

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * ```KlangScript
 * "c d e f".maskAll("x ~ x ~").note()  // every other beat silenced
 * ```
 */
@StrudelDsl
fun String.maskAll(vararg args: PatternLike): StrudelPattern = this._maskAll(args.toList())

// -- jux() ------------------------------------------------------------------------------------------------------------

fun applyJux(source: StrudelPattern, transform: PatternMapper): StrudelPattern {
    // Pan is unipolar (0.0 to 1.0).
    // jux pans original hard left (0.0) and transformed hard right (1.0).
    val left = source.pan(0.0)
    val right = transform(source).pan(1.0)
    return StackPattern(listOf(left, right))
}

// delegates - still register with KlangScript
internal val StrudelPattern._jux by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transform = args.firstOrNull().toPatternMapper() ?: { it }
    applyJux(p, transform)
}

internal val String._jux by dslStringExtension { p, args, callInfo -> p._jux(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Pans the original pattern hard left and a transformed version hard right.
 *
 * Creates a stereo image by stacking the original pattern panned to 0.0 (left) with the result of
 * [transform] panned to 1.0 (right). Useful for adding stereo width or creating call-and-response effects.
 *
 * @param transform Function applied to the right-channel copy of the pattern.
 * @return A stereo pattern with the original on the left and the transformed copy on the right.
 *
 * ```KlangScript
 * s("bd sd").jux(x => x.rev())  // reversed pattern panned right
 * ```
 *
 * ```KlangScript
 * note("c e g").jux(x => x.fast(2))  // double-speed version panned right
 * ```
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@StrudelDsl
fun StrudelPattern.jux(transform: PatternMapper): StrudelPattern =
    this._jux(listOf(transform).asStrudelDslArgs())

/**
 * Pans this string-parsed pattern hard left and a transformed version hard right.
 *
 * ```KlangScript
 * "bd sd".jux(x => x.rev()).s()  // reversed pattern right, original left
 * ```
 */
@StrudelDsl
fun String.jux(transform: PatternMapper): StrudelPattern =
    this._jux(listOf(transform).asStrudelDslArgs())

// -- juxBy() ----------------------------------------------------------------------------------------------------------

fun applyJuxBy(source: StrudelPattern, amount: Double, transform: PatternMapper): StrudelPattern {
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

// delegates - still register with KlangScript
internal val StrudelPattern._juxBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val amount = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 1.0
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyJuxBy(p, amount, transform)
}

internal val String._juxBy by dslStringExtension { p, args, callInfo -> p._juxBy(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Like [jux], but with adjustable stereo width.
 *
 * Pans the original left by `0.5 * (1 - amount)` and the transformed copy right by `0.5 * (1 + amount)`.
 * At `amount = 1.0` (full stereo) this is equivalent to [jux]. At `amount = 0.0` both copies are centred (mono).
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan). Default is 1.0.
 * @param transform Function applied to the right-channel copy.
 * @return A stereo pattern with width controlled by `amount`.
 *
 * ```KlangScript
 * s("bd sd").juxBy(0.5, x => x.rev())  // half stereo width, reversed on right
 * ```
 *
 * ```KlangScript
 * note("c e g").juxBy(0.75, x => x.fast(2))  // 75% stereo, faster on right
 * ```
 * @category structural
 * @tags jux, pan, stereo, spatial, width
 */
@StrudelDsl
fun StrudelPattern.juxBy(amount: Double, transform: PatternMapper): StrudelPattern =
    this._juxBy(listOf(amount, transform).asStrudelDslArgs())

/**
 * Like [jux], but with adjustable stereo width.
 *
 * ```KlangScript
 * "bd sd".juxBy(0.5, x => x.rev()).s()  // half-width stereo, reversed on right
 * ```
 */
@StrudelDsl
fun String.juxBy(amount: Double, transform: PatternMapper): StrudelPattern =
    this._juxBy(listOf(amount, transform).asStrudelDslArgs())

// -- off() ------------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._off by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: we must support control patterns for the first parameter
    val time = args.getOrNull(0)?.value?.asRationalOrNull() ?: Rational.QUARTER

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    p.stack(transform(p).late(time))
}

internal val String._off by dslStringExtension { p, args, callInfo -> p._off(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Layers a time-shifted, transformed copy of the pattern on top of itself.
 *
 * Stacks the original with a delayed copy produced by applying [transform]. Useful for creating rhythmic
 * echoes, counterpoint, or call-and-response effects.
 *
 * @param time Time offset in cycles for the delayed copy. Default is 0.25 (quarter cycle).
 * @param transform Function applied to the delayed copy.
 * @return The original pattern stacked with a time-shifted, transformed copy.
 *
 * ```KlangScript
 * s("bd sd").off(0.125, x => x.gain(0.2))  // quiet echo 1/8 cycle behind
 * ```
 *
 * ```KlangScript
 * note("c e g").off(0.25, x => x.transpose(12))  // octave-up copy a quarter cycle behind
 * ```
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@StrudelDsl
fun StrudelPattern.off(time: Double, transform: PatternMapper): StrudelPattern =
    this._off(listOf(time, transform).asStrudelDslArgs())

/**
 * Layers a time-shifted, transformed copy of this string-parsed pattern on top of itself.
 *
 * ```KlangScript
 * "bd sd".off(0.125, x => x.gain(0.7)).s()  // quiet echo behind the beat
 * ```
 */
@StrudelDsl
fun String.off(time: Double, transform: PatternMapper): StrudelPattern =
    this._off(listOf(time, transform).asStrudelDslArgs())

// -- filter() ---------------------------------------------------------------------------------------------------------

fun applyFilter(source: StrudelPattern, predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern {
    return source.map { events -> events.filter(predicate) }
}

// delegates - still register with KlangScript
internal val _filter by dslPatternFunction { args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((StrudelPatternEvent) -> Boolean)? =
        args.getOrNull(0)?.value as? (StrudelPatternEvent) -> Boolean

    val pat: StrudelPattern = args.getOrNull(1)?.value as? StrudelPattern ?: silence

    if (predicate != null) applyFilter(pat) { predicate(it) } else pat
}

internal val StrudelPattern._filter by dslPatternExtension { p, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((StrudelPatternEvent) -> Boolean)? =
        args.firstOrNull()?.value as? (StrudelPatternEvent) -> Boolean

    if (predicate != null) applyFilter(source = p, predicate = predicate) else p
}

internal val String._filter by dslStringExtension { p, args, callInfo -> p._filter(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Filters events from the pattern using a predicate function.
 *
 * Only events for which [predicate] returns `true` are kept; all others are removed from the pattern.
 *
 * @param predicate Function that receives a [StrudelPatternEvent] and returns `true` to keep it.
 * @return A new pattern containing only the events that satisfy the predicate.
 *
 * ```KlangScript
 * s("bd sd hh cp").filter(x => x.part.begin < 0.5)  // keep first-half events
 * ```
 *
 * ```KlangScript
 * note("c d e f").filter(x => x.isOnset)  // keep only onset events
 * ```
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@StrudelDsl
fun StrudelPattern.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern =
    applyFilter(source = this, predicate = predicate)

/**
 * Filters events from this string-parsed pattern using a predicate function.
 *
 * ```KlangScript
 * "bd sd hh cp".filter { it.part.begin.toDouble() < 0.5 }.s()  // keep first-half events
 * ```
 */
@StrudelDsl
fun String.filter(predicate: (StrudelPatternEvent) -> Boolean): StrudelPattern =
    this._filter(listOf(predicate).asStrudelDslArgs())

@StrudelDsl
fun filter(predicate: (StrudelPatternEvent) -> Boolean) = _filter(predicate)

// -- filterWhen() -----------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val _filterWhen by dslPatternFunction { args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.getOrNull(0)?.value as? (Double) -> Boolean

    val pat: StrudelPattern = args.getOrNull(1)?.value as? StrudelPattern ?: silence

    if (predicate != null) applyFilter(pat) { predicate(it.part.begin.toDouble()) } else pat
}

internal val StrudelPattern._filterWhen by dslPatternExtension { source, args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val predicate: ((Double) -> Boolean)? = args.firstOrNull()?.value as? (Double) -> Boolean

    if (predicate != null) applyFilter(source = source) { predicate(it.part.begin.toDouble()) } else source
}

internal val String._filterWhen by dslStringExtension { source, args, callInfo ->
    source._filterWhen(args, callInfo)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Filters events from the pattern based on their begin time.
 *
 * Only events whose `part.begin` value (as `Double`) satisfies [predicate] are kept.
 *
 * @param predicate Function that receives the begin time as a `Double` and returns `true` to keep the event.
 * @return A new pattern with only the events whose begin time satisfies the predicate.
 *
 * ```KlangScript
 * note("c d e f").filterWhen(t => t < 0.5)  // keep only first-half events
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").filterWhen(t => t % 0.25 == 0)  // keep only events on beat boundaries
 * ```
 * @category structural
 * @tags filter, time, conditional, predicate
 */
@StrudelDsl
fun StrudelPattern.filterWhen(predicate: (Double) -> Boolean): StrudelPattern =
    this._filterWhen(listOf(predicate).asStrudelDslArgs())

/**
 * Filters events from this string-parsed pattern based on their begin time.
 *
 * ```KlangScript
 * "bd sd hh cp".filterWhen(t => t < 0.5).s()  // keep only first-half events
 * ```
 */
@StrudelDsl
fun String.filterWhen(predicate: (Double) -> Boolean): StrudelPattern =
    this._filterWhen(listOf(predicate).asStrudelDslArgs())

@StrudelDsl
fun filterWhen(predicate: (Double) -> Boolean) = _filterWhen(predicate)

// -- superimpose() ----------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._superimpose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transformed = args
        .map { arg -> arg.toPatternMapper() ?: { it } }
        .map { it(p) }

    p.stack(*transformed.toTypedArray())
}

internal val String._superimpose by dslStringExtension { p, args, callInfo -> p._superimpose(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Layers a transformed copy of the pattern on top of itself.
 *
 * Stacks the original pattern together with the result of applying [transform] to it.
 * Unlike [off], the copy is not time-shifted — both layers start at the same position.
 *
 * @param transform Function applied to produce the second layer.
 * @return The original pattern stacked with its transformed copy.
 *
 * ```KlangScript
 * s("bd sd").superimpose(x => x.fast(2))  // double-speed layer on top of original
 * ```
 *
 * ```KlangScript
 * note("c e g").superimpose(x => x.transpose(7), x => x.transpose(12))  // fifth and octave stacked on original
 * ```
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@StrudelDsl
fun StrudelPattern.superimpose(transform: PatternMapper): StrudelPattern =
    this._superimpose(listOf(transform).asStrudelDslArgs())

/**
 * Layers a transformed copy of this string-parsed pattern on top of itself.
 *
 * ```KlangScript
 * "bd sd".superimpose(x => x.fast(2)).s()  // double-speed layer on top
 * ```
 */
@StrudelDsl
fun String.superimpose(transform: PatternMapper): StrudelPattern =
    this._superimpose(listOf(transform).asStrudelDslArgs())

// -- layer() ----------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._layer by dslPatternExtension { p, args, /* callInfo */ _ ->
    val transforms: List<PatternMapper> = args.mapNotNull { it.toPatternMapper() }

    if (transforms.isEmpty()) {
        p // we keep the pattern as is
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

internal val String._layer by dslStringExtension { p, args, callInfo -> p._layer(args, callInfo) }

internal val StrudelPattern._apply by dslPatternExtension { p, args, callInfo -> p._layer(args, callInfo) }

internal val String._apply by dslStringExtension { p, args, callInfo -> p._apply(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies multiple transformation functions to the pattern and stacks the results.
 *
 * Each function in [transforms] is applied to the original pattern independently, and all results are
 * stacked together. Useful for building complex textures from a single source pattern.
 *
 * @param transforms One or more functions to apply; each result is stacked with the others.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript
 * s("bd hh sd oh").layer(x => x.fast(2), x => x.rev())  // two transformed layers stacked
 * ```
 *
 * ```KlangScript
 * note("c e g").layer(x => x.transpose(7), x => x.transpose(12))  // fifth and octave layers stacked
 * ```
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@StrudelDsl
fun StrudelPattern.layer(vararg transforms: PatternMapper): StrudelPattern =
    this._layer(transforms.toList().asStrudelDslArgs())

/**
 * Applies multiple transformation functions to this string-parsed pattern and stacks the results.
 *
 * ```KlangScript
 * "bd sd".layer(x => x.fast(2), x => x.rev()).s()  // two transformed layers stacked
 * ```
 * @alias apply
 */
@StrudelDsl
fun String.layer(vararg transforms: PatternMapper): StrudelPattern =
    this._layer(transforms.toList().asStrudelDslArgs())

/**
 * Alias for [layer] — applies multiple transformation functions and stacks the results.
 *
 * @param transforms One or more functions to apply; results are stacked.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript
 * s("bd hh sd oh").apply(x => x.fast(2), x => x.rev())  // two layers stacked
 * ```
 *
 * ```KlangScript
 * note("c e").apply(x => x.transpose(7))  // fifth layer stacked
 * ```
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@StrudelDsl
fun StrudelPattern.apply(vararg transforms: PatternMapper): StrudelPattern =
    this._apply(transforms.toList().asStrudelDslArgs())

/**
 * Alias for [layer] on a string-parsed pattern.
 *
 * ```KlangScript
 * "bd sd hh hh".apply(x => x.fast(2), x => x.rev()).s()  // two layers stacked
 * ```
 * @alias layer
 */
@StrudelDsl
fun String.apply(vararg transforms: PatternMapper): StrudelPattern =
    this._apply(transforms.toList().asStrudelDslArgs())

// -- zoom() -----------------------------------------------------------------------------------------------------------

fun applyZoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

// delegates - still register with KlangScript
internal val StrudelPattern._zoom by dslPatternExtension { p, args, /* callInfo */ _ -> applyZoom(p, args) }

internal val String._zoom by dslStringExtension { p, args, callInfo -> p._zoom(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Plays a portion of a pattern within a time window, stretching it to fill a full cycle.
 *
 * The window `[start, end]` is zoomed in on — events within that portion are stretched to fill the cycle.
 * Both `start` and `end` can be pattern strings for dynamic zooming (e.g. `"<0 0.25>"`).
 *
 * @param start Start of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @param end End of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @return The zoomed portion of the pattern stretched to one full cycle.
 *
 * ```KlangScript
 * s("bd hh sd hh").zoom(0.0, 0.5)  // plays only first half, stretched to full cycle
 * ```
 *
 * ```KlangScript
 * note("c d e f").zoom(0.25, 0.75)  // plays middle two notes, stretched to full cycle
 * ```
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@StrudelDsl
fun StrudelPattern.zoom(start: PatternLike, end: PatternLike): StrudelPattern =
    this._zoom(listOf(start, end).asStrudelDslArgs())

/**
 * Plays a portion of this string-parsed pattern within a time window, stretched to fill a cycle.
 *
 * ```KlangScript
 * "bd hh sd hh".zoom(0.0, 0.5).s()  // first half stretched to full cycle
 * ```
 */
@StrudelDsl
fun String.zoom(start: PatternLike, end: PatternLike): StrudelPattern =
    this._zoom(listOf(start, end).asStrudelDslArgs())

// -- within() ---------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._within by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns for start and end
    val start = args.getOrNull(0)?.value?.asRationalOrNull() ?: Rational.ZERO
    val end = args.getOrNull(1)?.value?.asRationalOrNull() ?: Rational.ONE
    val transform = args.getOrNull(2).toPatternMapper() ?: { it }

    if (start >= end || start < Rational.ZERO || end > Rational.ONE) {
        p // Return unchanged if invalid window
    } else {
        val isBeginInWindow: (StrudelPatternEvent) -> Boolean = { ev ->
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

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a transformation to the portion of the pattern that falls within a time window.
 *
 * Events inside `[start, end]` are extracted, transformed, then stacked back with the untouched events
 * outside the window. The window must be in `[0, 1]` and `start < end`.
 *
 * @param start Start of the window (0.0 to 1.0).
 * @param end End of the window (0.0 to 1.0).
 * @param transform Function applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript
 * s("bd sd hh cp").within(0.0, 0.5, x => x.fast(2))  // double-speed in first half
 * ```
 *
 * ```KlangScript
 * note("c d e f").within(0.25, 0.75, x => x.transpose(12))  // octave up in the middle
 * ```
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@StrudelDsl
fun StrudelPattern.within(start: Double, end: Double, transform: PatternMapper): StrudelPattern =
    this._within(listOf(start, end, transform).asStrudelDslArgs())

/**
 * Applies a transformation to the portion of this string-parsed pattern that falls within a time window.
 *
 * ```KlangScript
 * "bd sd hh cp".within(0.0, 0.5, x => x.fast(2)).s()  // double-speed in first half
 * ```
 */
@StrudelDsl
fun String.within(start: Double, end: Double, transform: PatternMapper): StrudelPattern =
    this._within(listOf(start, end, transform).asStrudelDslArgs())

// -- chunk() ----------------------------------------------------------------------------------------------------------

fun applyChunk(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
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
        AtomicPattern(StrudelVoiceData.empty.copy(value = it.asVoiceValue()))
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
internal val StrudelPattern._chunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
internal val String._chunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

internal val StrudelPattern._slowchunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
internal val String._slowchunk by dslStringExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }

internal val StrudelPattern._slowChunk by dslPatternExtension { p, args, /* callInfo */ _ -> applyChunk(p, args) }
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
@StrudelDsl
fun StrudelPattern.chunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())

/** Like [chunk] applied to a mini-notation string. */
@StrudelDsl
fun String.chunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = this._chunk(listOf(n, transform, back, fast).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.slowchunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())

/** Alias for [chunk]. */
@StrudelDsl
fun String.slowchunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = this._slowchunk(listOf(n, transform, back, fast).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.slowChunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = applyChunk(this, listOf(n, transform, back, fast).asStrudelDslArgs())

/** Alias for [chunk]. */
@StrudelDsl
fun String.slowChunk(
    n: Int,
    back: Boolean = false,
    fast: Boolean = false,
    transform: PatternMapper,
): StrudelPattern = this._slowChunk(listOf(n, transform, back, fast).asStrudelDslArgs())

// -- chunkBack() / chunkback() ----------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._chunkBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, true, false).asStrudelDslArgs())
}

internal val String._chunkBack by dslStringExtension { p, args, callInfo -> p._chunkBack(args, callInfo) }

internal val StrudelPattern._chunkback by dslPatternExtension { p, args, callInfo ->
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
@StrudelDsl
fun StrudelPattern.chunkBack(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkBack(listOf(n, transform).asStrudelDslArgs())

/**
 * Like [chunk] on a string-parsed pattern, but cycles backward through parts.
 *
 * ```KlangScript
 * "bd sd ht lt".chunkBack(4, x => x.gain(0.1)).s()  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 */
@StrudelDsl
fun String.chunkBack(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkBack(listOf(n, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.chunkback(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkback(listOf(n, transform).asStrudelDslArgs())

/**
 * Alias for [chunkBack] on a string-parsed pattern.
 *
 * ```KlangScript
 * "bd sd ht lt".chunkback(4, x => x.gain(0.8)).s()  // one hit boosted, cycling back
 * ```
 * @alias chunkBack
 */
@StrudelDsl
fun String.chunkback(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkback(listOf(n, transform).asStrudelDslArgs())

// -- fastChunk() / fastchunk() ----------------------------------------------------------------------------------------

//  delegates - still register with KlangScript
internal val StrudelPattern._fastChunk by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, false, true).asStrudelDslArgs())
}

internal val String._fastChunk by dslStringExtension { p, args, callInfo -> p._fastChunk(args, callInfo) }

internal val StrudelPattern._fastchunk by dslPatternExtension { p, args, callInfo ->
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
@StrudelDsl
fun StrudelPattern.fastChunk(n: Int, transform: PatternMapper): StrudelPattern =
    this._fastChunk(listOf(n, transform).asStrudelDslArgs())

/**
 * Like [chunk] on a string-parsed pattern but at natural speed.
 *
 * ```KlangScript
 * "bd sd ht lt".fastChunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastchunk
 */
@StrudelDsl
fun String.fastChunk(n: Int, transform: PatternMapper): StrudelPattern =
    this._fastChunk(listOf(n, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.fastchunk(n: Int, transform: PatternMapper): StrudelPattern =
    this._fastchunk(listOf(n, transform).asStrudelDslArgs())

/**
 * Alias for [fastChunk] on a string-parsed pattern.
 *
 * ```KlangScript
 * "bd sd ht lt".fastchunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastChunk
 */
@StrudelDsl
fun String.fastchunk(n: Int, transform: PatternMapper): StrudelPattern =
    this._fastchunk(listOf(n, transform).asStrudelDslArgs())

// -- chunkInto() ------------------------------------------------------------------------------------------------------

// delegates - still register with KlangScript
internal val StrudelPattern._chunkInto by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }
    applyChunk(p, listOf(nArg, transform, false, true).asStrudelDslArgs())
}
internal val String._chunkInto by dslStringExtension { p, args, callInfo -> p._chunkInto(args, callInfo) }
internal val StrudelPattern._chunkinto by dslPatternExtension { p, args, callInfo -> p._chunkInto(args, callInfo) }
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
@StrudelDsl
fun StrudelPattern.chunkInto(
    n: Int,
    transform: PatternMapper,
): StrudelPattern = applyChunk(this, listOf(n, transform, false, true).asStrudelDslArgs())

/** Like [chunkInto] applied to a mini-notation string. */
@StrudelDsl
fun String.chunkInto(
    n: Int,
    transform: PatternMapper,
): StrudelPattern = this._chunkInto(listOf(n, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.chunkinto(
    n: Int,
    transform: PatternMapper,
): StrudelPattern = chunkInto(n, transform)

/** Alias for [chunkInto]. */
@StrudelDsl
fun String.chunkinto(
    n: Int,
    transform: PatternMapper,
): StrudelPattern = this.chunkInto(n, transform)

// -- chunkBackInto() --------------------------------------------------------------------------------------------------

internal val StrudelPattern._chunkBackInto by dslPatternExtension { p, args, /* callInfo */ _ ->
    // TODO: support control patterns
    val nArg = args.getOrNull(0) ?: StrudelDslArg.of(1)
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    applyChunk(p, listOf(nArg, transform, true, true, 1).asStrudelDslArgs())
}

internal val String._chunkBackInto by dslStringExtension { p, args, callInfo ->
    p._chunkBackInto(args, callInfo)
}

internal val StrudelPattern._chunkbackinto by dslPatternExtension { p, args, callInfo ->
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
@StrudelDsl
fun StrudelPattern.chunkBackInto(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkBackInto(listOf(n, transform).asStrudelDslArgs())

/** Like [chunkBackInto] applied to a mini-notation string. */
@StrudelDsl
fun String.chunkBackInto(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkBackInto(listOf(n, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.chunkbackinto(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkbackinto(listOf(n, transform).asStrudelDslArgs())

/** Alias for [chunkBackInto]. */
@StrudelDsl
fun String.chunkbackinto(n: Int, transform: PatternMapper): StrudelPattern =
    this._chunkbackinto(listOf(n, transform).asStrudelDslArgs())

// -- linger() ---------------------------------------------------------------------------------------------------------

fun applyLinger(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._linger by dslPatternExtension { p, args, /* callInfo */ _ -> applyLinger(p, args) }

internal val String._linger by dslStringExtension { p, args, callInfo -> p._linger(args, callInfo) }

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
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence).
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript
 * s("bd sd ht lt").linger(0.5)  // repeats "bd sd" throughout the cycle
 * ```
 *
 * ```KlangScript
 * s("lt ht mt cp").linger("<1 .5 .25 .125 .0625 .03125>")  // different fraction each cycle
 * ```
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@StrudelDsl
fun StrudelPattern.linger(t: PatternLike): StrudelPattern = this._linger(listOf(t).asStrudelDslArgs())

/** Selects the given fraction of the pattern and repeats that part to fill the remainder of the cycle. */
@StrudelDsl
fun String.linger(t: PatternLike): StrudelPattern = this._linger(listOf(t).asStrudelDslArgs())

// -- echo() / stut() --------------------------------------------------------------------------------------------------

internal val StrudelPattern._echo by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val StrudelPattern._stut by dslPatternExtension { p, args, callInfo -> p._echo(args, callInfo) }

internal val String._stut by dslStringExtension { p, args, callInfo -> p._echo(args, callInfo) }

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
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@StrudelDsl
fun StrudelPattern.echo(times: Int, delay: Double, decay: Double): StrudelPattern =
    this._echo(listOf(times, delay, decay).asStrudelDslArgs())

/** Like [echo] applied to a mini-notation string. */
@StrudelDsl
fun String.echo(times: Int, delay: Double, decay: Double): StrudelPattern =
    this._echo(listOf(times, delay, decay).asStrudelDslArgs())

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
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@StrudelDsl
fun StrudelPattern.stut(times: Int, delay: Double, decay: Double): StrudelPattern =
    this._stut(listOf(times, delay, decay).asStrudelDslArgs())

/** Alias for [echo]. */
@StrudelDsl
fun String.stut(times: Int, delay: Double, decay: Double): StrudelPattern =
    this._stut(listOf(times, delay, decay).asStrudelDslArgs())

// -- echoWith() / stutWith() ------------------------------------------------------------------------------------------

internal val StrudelPattern._echoWith by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val StrudelPattern._stutWith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
internal val String._stutWith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

internal val StrudelPattern._stutwith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
internal val String._stutwith by dslStringExtension { p, args, callInfo -> p._echoWith(args, callInfo) }

internal val StrudelPattern._echowith by dslPatternExtension { p, args, callInfo -> p._echoWith(args, callInfo) }
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
@StrudelDsl
fun StrudelPattern.echoWith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._echoWith(listOf(times, delay, transform).asStrudelDslArgs())

/** Like [echoWith] applied to a mini-notation string. */
@StrudelDsl
fun String.echoWith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._echoWith(listOf(times, delay, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.stutWith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._stutWith(listOf(times, delay, transform).asStrudelDslArgs())

/** Alias for [echoWith]. */
@StrudelDsl
fun String.stutWith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._stutWith(listOf(times, delay, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.stutwith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._stutwith(listOf(times, delay, transform).asStrudelDslArgs())

/** Alias for [echoWith]. */
@StrudelDsl
fun String.stutwith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._stutwith(listOf(times, delay, transform).asStrudelDslArgs())

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
@StrudelDsl
fun StrudelPattern.echowith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._echowith(listOf(times, delay, transform).asStrudelDslArgs())

/** Alias for [echoWith]. */
@StrudelDsl
fun String.echowith(times: Int, delay: Double, transform: PatternMapper): StrudelPattern =
    this._echowith(listOf(times, delay, transform).asStrudelDslArgs())

// -- bite() -----------------------------------------------------------------------------------------------------------

fun applyBite(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._bite by dslPatternExtension { p, args, /* callInfo */ _ -> applyBite(p, args) }

internal val String._bite by dslStringExtension { p, args, callInfo -> p._bite(args, callInfo) }

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
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@StrudelDsl
fun StrudelPattern.bite(n: PatternLike, indices: PatternLike): StrudelPattern =
    this._bite(listOf(n, indices).asStrudelDslArgs())

/** Like [bite] applied to a mini-notation string. */
@StrudelDsl
fun String.bite(n: PatternLike, indices: PatternLike): StrudelPattern =
    this._bite(listOf(n, indices).asStrudelDslArgs())

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

internal val StrudelPattern._segment by dslPatternExtension { p, args, /* callInfo */ _ -> applySegment(p, args) }

internal val String._segment by dslStringExtension { p, args, callInfo -> p._segment(args, callInfo) }

internal val StrudelPattern._seg by dslPatternExtension { p, args, callInfo -> p._segment(args, callInfo) }

internal val String._seg by dslStringExtension { p, args, callInfo -> p._segment(args, callInfo) }

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
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@StrudelDsl
fun StrudelPattern.segment(n: PatternLike): StrudelPattern = this._segment(listOf(n).asStrudelDslArgs())

/** Like [segment] applied to a mini-notation string. */
@StrudelDsl
fun String.segment(n: PatternLike): StrudelPattern = this._segment(listOf(n).asStrudelDslArgs())

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
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@StrudelDsl
fun StrudelPattern.seg(n: PatternLike): StrudelPattern = this._seg(listOf(n).asStrudelDslArgs())

/** Alias for [segment]. */
@StrudelDsl
fun String.seg(n: PatternLike): StrudelPattern = this._seg(listOf(n).asStrudelDslArgs())

// -- euclid() ---------------------------------------------------------------------------------------------------------

fun applyEuclid(source: StrudelPattern, pulses: Int, steps: Int, rotation: Int): StrudelPattern {
    return EuclideanPattern.create(
        inner = source,
        pulses = pulses,
        steps = steps,
        rotation = rotation,
    )
}

internal val _euclid by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(2).toPattern()
    pattern._euclid(args, callInfo)
}

internal val StrudelPattern._euclid by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val String._euclid by dslStringExtension { p, args, callInfo -> p._euclid(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Changes the structure of the pattern to a Euclidean rhythm.
 *
 * Euclidean rhythms distribute `pulses` onsets as evenly as possible across `steps` steps using the
 * greatest common divisor algorithm.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript
 * s("hh").euclid(3, 8)  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * ```KlangScript
 * s("bd").euclid(5, 16)  // 5 beats distributed across 16 steps
 * ```
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@StrudelDsl
fun euclid(pulses: Int, steps: Int, pattern: PatternLike): StrudelPattern =
    _euclid(listOf(pulses, steps, pattern).asStrudelDslArgs())

/** Applies a Euclidean rhythm structure to the pattern. */
@StrudelDsl
fun StrudelPattern.euclid(pulses: Int, steps: Int): StrudelPattern =
    this._euclid(listOf(pulses, steps).asStrudelDslArgs())

/** Applies a Euclidean rhythm structure to the mini-notation string. */
@StrudelDsl
fun String.euclid(pulses: Int, steps: Int): StrudelPattern =
    this._euclid(listOf(pulses, steps).asStrudelDslArgs())

// -- euclidRot() ------------------------------------------------------------------------------------------------------

internal val _euclidRot by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(3).toPattern()
    pattern._euclidRot(args, callInfo)
}

internal val StrudelPattern._euclidRot by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val String._euclidRot by dslStringExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }

internal val _euclidrot by dslPatternFunction { args, callInfo -> _euclidRot(args, callInfo) }
internal val StrudelPattern._euclidrot by dslPatternExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }
internal val String._euclidrot by dslStringExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Like [euclid], but with an additional rotation parameter to offset the rhythm start point.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * s("hh").euclidRot(3, 8, 2)  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * ```KlangScript
 * s("bd").euclidRot(5, 16, 1)  // 5-over-16 shifted by 1 step
 * ```
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@StrudelDsl
fun euclidRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    _euclidRot(listOf(pulses, steps, rotation, pattern).asStrudelDslArgs())

/** Like [euclid] with rotation, applied to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Like [euclid] with rotation, applied to the mini-notation string. */
@StrudelDsl
fun String.euclidRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Alias for [euclidRot] — Euclidean rhythm with rotation.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * s("hh").euclidrot(3, 8, 2)  // lowercase alias
 * ```
 *
 * ```KlangScript
 * s("sd").euclidrot(5, 16, 3)  // 5-over-16, rotated by 3
 * ```
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@StrudelDsl
fun euclidrot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    _euclidrot(listOf(pulses, steps, rotation, pattern).asStrudelDslArgs())

/** Alias for [euclidRot]. */
@StrudelDsl
fun StrudelPattern.euclidrot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Alias for [euclidRot]. */
@StrudelDsl
fun String.euclidrot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

// -- bjork() ----------------------------------------------------------------------------------------------------------

// TODO: check this, this seems to be wrong. First param is the pattern, rest are params.
// We also need a top level function bjork(pattern, ...args)
internal val _bjork by dslPatternFunction { args, callInfo ->
    val params = args.getOrNull(0)?.value as? List<*> ?: return@dslPatternFunction silence

    val pattern = args.getOrNull(1)?.toPattern() ?: return@dslPatternFunction silence

    pattern._bjork(params, callInfo)
}

internal val StrudelPattern._bjork by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val String._bjork by dslStringExtension { p, args, callInfo -> p._bjork(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a Euclidean rhythm specified as individual parameters (pulses, steps, rotation).
 *
 * Alternative to [euclidRot] that bundles parameters together. Named after Björk's use of
 * Euclidean rhythms in music.
 *
 * @param pulses   Number of onsets (beats).
 * @param steps    Total number of steps.
 * @param rotation Number of steps to rotate (default 0).
 * @return A pattern with the Euclidean rhythm applied.
 *
 * ```KlangScript
 * s("hh").bjork(3, 8, 0)  // equivalent to euclid(3, 8)
 * ```
 *
 * ```KlangScript
 * s("bd").bjork(5, 16, 2)  // 5-over-16 with rotation 2
 * ```
 * @category structural
 * @tags bjork, euclid, rhythm, rotation
 */
@StrudelDsl
fun StrudelPattern.bjork(pulses: Int, steps: Int, rotation: Int = 0): StrudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/** Like [bjork] applied to a mini-notation string. */
@StrudelDsl
fun String.bjork(pulses: Int, steps: Int, rotation: Int = 0): StrudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/** Like [bjork] as a top level function, produces events with voice value 1. */
@StrudelDsl
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, pattern: PatternLike): StrudelPattern =
    _bjork(listOf(listOf(pulses, steps, rotation), pattern).asStrudelDslArgs())

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

internal val _euclidLegato by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(2).toPattern()
    pattern._euclidLegato(args, callInfo)
}

internal val StrudelPattern._euclidLegato by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val String._euclidLegato by dslStringExtension { p, args, callInfo -> p._euclidLegato(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Like [euclid], but each pulse is held until the next pulse, so there are no gaps between notes.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A legato Euclidean rhythm pattern (no rests between onsets).
 *
 * ```KlangScript
 * s("hh").euclidLegato(3, 8)  // 3-over-8 legato (held notes)
 * ```
 *
 * ```KlangScript
 * note("c").euclidLegato(5, 8)  // 5 legato notes across 8 steps
 * ```
 * @category structural
 * @tags euclidLegato, euclid, legato, rhythm, structure
 */
@StrudelDsl
fun euclidLegato(pulses: Int, steps: Int, pattern: PatternLike): StrudelPattern =
    _euclidLegato(listOf(pulses, steps, pattern).asStrudelDslArgs())

/** Applies legato Euclidean structure to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidLegato(pulses: Int, steps: Int): StrudelPattern =
    this._euclidLegato(listOf(pulses, steps).asStrudelDslArgs())

/** Applies legato Euclidean structure to the mini-notation string. */
@StrudelDsl
fun String.euclidLegato(pulses: Int, steps: Int): StrudelPattern =
    this._euclidLegato(listOf(pulses, steps).asStrudelDslArgs())

// -- euclidLegatoRot() ------------------------------------------------------------------------------------------------

internal val _euclidLegatoRot by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(3).toPattern()
    pattern._euclidLegatoRot(args, callInfo)
}

internal val StrudelPattern._euclidLegatoRot by dslPatternExtension { p, args, /* callInfo */ _ ->
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

internal val String._euclidLegatoRot by dslStringExtension { p, args, callInfo ->
    p._euclidLegatoRot(args, callInfo)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Like [euclidLegato], but with rotation — each pulse is held until the next, with a step offset.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A legato Euclidean rhythm with rotation applied.
 *
 * ```KlangScript
 * s("hh").euclidLegatoRot(3, 8, 2)  // legato 3-over-8, rotated by 2
 * ```
 *
 * ```KlangScript
 * note("c").euclidLegatoRot(5, 8, 1)  // legato 5-over-8, rotated by 1
 * ```
 * @category structural
 * @tags euclidLegatoRot, euclid, legato, rotation, rhythm
 */
@StrudelDsl
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    _euclidLegatoRot(listOf(pulses, steps, rotation, pattern).asStrudelDslArgs())

/** Applies legato Euclidean structure with rotation to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Applies legato Euclidean structure with rotation to the mini-notation string. */
@StrudelDsl
fun String.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

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

internal val _euclidish by dslPatternFunction { args, /* callInfo */ _ ->
    // euclidish(pulses, steps, groove, pat)
    val pattern = args.drop(3).toPattern()
    applyEuclidish(pattern, args.take(3))
}

internal val StrudelPattern._euclidish by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyEuclidish(p, args)
}

internal val String._euclidish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }

internal val _eish by dslPatternFunction { args, callInfo -> _euclidish(args, callInfo) }
internal val StrudelPattern._eish by dslPatternExtension { p, args, callInfo -> p._euclidish(args, callInfo) }
internal val String._eish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * A `euclid` variant with a `groove` parameter that morphs the rhythm from strict (0) to even (1).
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict Euclidean) to 1 (completely even spacing).
 * @return A pattern with the morphed Euclidean rhythm applied as structure.
 *
 * ```KlangScript
 * s("hh").euclidish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript
 * s("bd").euclidish(5, 16, 0.0)  // same as euclid(5, 16)
 * ```
 * @alias eish
 * @category structural
 * @tags euclidish, euclid, groove, morph, rhythm
 */
@StrudelDsl
fun euclidish(pulses: Int, steps: Int, groove: PatternLike, pattern: PatternLike): StrudelPattern =
    _euclidish(listOf(pulses, steps, groove, pattern).asStrudelDslArgs())

/** Applies morphed Euclidean structure to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asStrudelDslArgs())

/** Applies morphed Euclidean structure to the mini-notation string. */
@StrudelDsl
fun String.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asStrudelDslArgs())

/**
 * Alias for [euclidish] — `euclid` variant with groove morphing.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict) to 1 (even).
 * @return A pattern with the morphed Euclidean rhythm applied.
 *
 * ```KlangScript
 * s("hh").eish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript
 * s("bd").eish(5, 16, 1.0)  // completely even spacing
 * ```
 * @alias euclidish
 * @category structural
 * @tags eish, euclidish, euclid, groove, morph, rhythm
 */
@StrudelDsl
fun eish(pulses: Int, steps: Int, groove: PatternLike, pattern: PatternLike): StrudelPattern =
    _eish(listOf(pulses, steps, groove, pattern).asStrudelDslArgs())

/** Alias for [euclidish]. */
@StrudelDsl
fun StrudelPattern.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._eish(listOf(pulses, steps, groove).asStrudelDslArgs())

/** Alias for [euclidish]. */
@StrudelDsl
fun String.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._eish(listOf(pulses, steps, groove).asStrudelDslArgs())

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
@StrudelDsl
fun run(n: Int): StrudelPattern = _run(listOf(n).asStrudelDslArgs())

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
@StrudelDsl
fun binaryN(n: Int, bits: Int = 16): StrudelPattern = _binaryN(listOf(n, bits).asStrudelDslArgs())

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
@StrudelDsl
fun binary(n: Int): StrudelPattern = _binary(listOf(n).asStrudelDslArgs())

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
@StrudelDsl
fun binaryNL(n: Int, bits: Int = 16): StrudelPattern = _binaryNL(listOf(n, bits).asStrudelDslArgs())

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
@StrudelDsl
fun binaryL(n: Int): StrudelPattern = _binaryL(listOf(n).asStrudelDslArgs())

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

internal val StrudelPattern._ratio by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
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
@StrudelDsl
fun ratio(vararg values: PatternLike): StrudelPattern = _ratio(values.toList())

/** Converts colon-ratio notation in the pattern's values to numbers. */
@StrudelDsl
fun StrudelPattern.ratio(): StrudelPattern = this._ratio()

/** Converts colon-ratio notation in the mini-notation string to numbers. */
@StrudelDsl
fun String.ratio(): StrudelPattern = this._ratio()

// -- pace() / steps() -------------------------------------------------------------------------------------------------

fun applyPace(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asRationalOrNull() ?: Rational.ONE
    val currentSteps = source.numSteps ?: Rational.ONE

    if (targetSteps <= Rational.ZERO || currentSteps <= Rational.ZERO) {
        return source
    }

    // Calculate speed adjustment: fast(targetSteps / currentSteps)
    val speedFactor = targetSteps / currentSteps

    return source.fast(speedFactor)
}

internal val _pace by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyPace(pattern, args.take(1))
}

internal val StrudelPattern._pace by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPace(p, args)
}

internal val String._pace by dslStringExtension { p, args, callInfo -> p._pace(args, callInfo) }

internal val _steps by dslPatternFunction { args, callInfo -> _pace(args, callInfo) }
internal val StrudelPattern._steps by dslPatternExtension { p, args, callInfo -> p._pace(args, callInfo) }
internal val String._steps by dslStringExtension { p, args, callInfo -> p._pace(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Adjusts the pattern speed so it plays exactly `n` steps per cycle.
 *
 * Computes the speed factor relative to the pattern's natural step count and applies [fast].
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript
 * note("c d e f").pace(8)  // 4-step pattern runs at 8 steps/cycle (double speed)
 * ```
 *
 * ```KlangScript
 * note("c d e f g").pace(4)  // 5-step pattern runs at 4 steps/cycle
 * ```
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@StrudelDsl
fun pace(n: PatternLike, pattern: PatternLike): StrudelPattern =
    _pace(listOf(n, pattern).asStrudelDslArgs())

/** Adjusts the pattern to play `n` steps per cycle. */
@StrudelDsl
fun StrudelPattern.pace(n: PatternLike): StrudelPattern = this._pace(listOf(n).asStrudelDslArgs())

/** Adjusts the mini-notation string pattern to play `n` steps per cycle. */
@StrudelDsl
fun String.pace(n: PatternLike): StrudelPattern = this._pace(listOf(n).asStrudelDslArgs())

/**
 * Alias for [pace] — adjusts the pattern speed so it plays exactly `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript
 * note("c d e f").steps(8)  // 4-step pattern runs at 8 steps/cycle
 * ```
 *
 * ```KlangScript
 * note("c d e f g").steps(4)  // 5-step pattern runs at 4 steps/cycle
 * ```
 * @alias pace
 * @category structural
 * @tags steps, pace, tempo, speed, cycle
 */
@StrudelDsl
fun steps(n: PatternLike, pattern: PatternLike): StrudelPattern =
    _steps(listOf(n, pattern).asStrudelDslArgs())

/** Alias for [pace]. */
@StrudelDsl
fun StrudelPattern.steps(n: PatternLike): StrudelPattern = this._steps(listOf(n).asStrudelDslArgs())

/** Alias for [pace]. */
@StrudelDsl
fun String.steps(n: PatternLike): StrudelPattern = this._steps(listOf(n).asStrudelDslArgs())

// -- take() -----------------------------------------------------------------------------------------------------------

fun applyTake(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val takeArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = takeArg.asControlValueProvider(Rational.ONE.asVoiceValue())

    val takePattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(StrudelVoiceData.empty.copy(value = control.value))
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

internal val _take by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyTake(pattern, args.take(1))
}

internal val StrudelPattern._take by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTake(p, args)
}

internal val String._take by dslStringExtension { p, args, callInfo -> p._take(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Keeps only the first `n` steps of the pattern and stretches them to fill the cycle.
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
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@StrudelDsl
fun take(n: PatternLike, pattern: PatternLike): StrudelPattern =
    _take(listOf(n, pattern).asStrudelDslArgs())

/** Keeps the first `n` steps of the pattern. */
@StrudelDsl
fun StrudelPattern.take(n: PatternLike): StrudelPattern = this._take(listOf(n).asStrudelDslArgs())

/** Keeps the first `n` steps of the mini-notation string pattern. */
@StrudelDsl
fun String.take(n: PatternLike): StrudelPattern = this._take(listOf(n).asStrudelDslArgs())

// -- drop() -----------------------------------------------------------------------------------------------------------

fun applyDrop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val dropArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = dropArg.asControlValueProvider(Rational.ZERO.asVoiceValue())

    val dropPattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(StrudelVoiceData.empty.copy(value = control.value))
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

internal val _drop by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyDrop(pattern, args.take(1))
}

internal val StrudelPattern._drop by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDrop(p, args)
}

internal val String._drop by dslStringExtension { p, args, callInfo -> p._drop(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Skips the first `n` steps of the pattern and stretches the remainder to fill the cycle.
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
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@StrudelDsl
fun drop(n: PatternLike, pattern: PatternLike): StrudelPattern =
    _drop(listOf(n, pattern).asStrudelDslArgs())

/** Skips the first `n` steps of the pattern. */
@StrudelDsl
fun StrudelPattern.drop(n: PatternLike): StrudelPattern = this._drop(listOf(n).asStrudelDslArgs())

/** Skips the first `n` steps of the mini-notation string pattern. */
@StrudelDsl
fun String.drop(n: PatternLike): StrudelPattern = this._drop(listOf(n).asStrudelDslArgs())

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

    val staticReps = repsVal?.asRationalOrNull()

    return if (staticReps != null) {
        RepeatCyclesPattern(source, staticReps)
    } else {
        RepeatCyclesPattern.control(source, repsPattern)
    }
}

internal val _repeatCycles by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyRepeatCycles(pattern, args.take(1))
}

internal val StrudelPattern._repeatCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRepeatCycles(p, args)
}

internal val String._repeatCycles by dslStringExtension { p, args, callInfo ->
    p._repeatCycles(args, callInfo)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Repeats each cycle of the pattern `n` times before advancing.
 *
 * Cycle 0 plays `n` times, then cycle 1 plays `n` times, and so on. Supports control patterns.
 *
 * @param n Number of times to repeat each cycle.
 * @return A pattern where each cycle is repeated `n` times.
 *
 * ```KlangScript
 * note("c d e f").repeatCycles(3)  // each cycle repeats 3 times
 * ```
 *
 * ```KlangScript
 * s("bd sd").repeatCycles("<1 2 4>")  // varying repetitions each cycle
 * ```
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@StrudelDsl
fun repeatCycles(n: PatternLike, pattern: PatternLike): StrudelPattern =
    _repeatCycles(listOf(n, pattern).asStrudelDslArgs())

/** Repeats each cycle of the pattern `n` times. */
@StrudelDsl
fun StrudelPattern.repeatCycles(n: PatternLike): StrudelPattern =
    this._repeatCycles(listOf(n).asStrudelDslArgs())

/** Repeats each cycle of the mini-notation string pattern `n` times. */
@StrudelDsl
fun String.repeatCycles(n: PatternLike): StrudelPattern =
    this._repeatCycles(listOf(n).asStrudelDslArgs())

// -- extend() ---------------------------------------------------------------------------------------------------------

internal val _extend by dslPatternFunction { args, callInfo -> _fast(args, callInfo) }

internal val StrudelPattern._extend by dslPatternExtension { p, args, callInfo -> p._fast(args, callInfo) }

internal val String._extend by dslStringExtension { p, args, callInfo -> p._extend(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Speeds up the pattern by the given factor — alias for [fast].
 *
 * `extend(2)` is identical to `fast(2)`: events play twice as fast. Accepts mini-notation strings
 * and control patterns.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @param pattern The pattern to speed up (top-level call only).
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript
 * note("c d e f").extend(2)  // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript
 * s("bd sd hh").extend("<1 2 4>")  // varying speed each cycle
 * ```
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@StrudelDsl
fun extend(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _extend(listOf(factor, pattern).asStrudelDslArgs())

/** Speeds up the pattern by `factor` — alias for [fast]. */
@StrudelDsl
fun StrudelPattern.extend(factor: PatternLike): StrudelPattern =
    this._extend(listOf(factor).asStrudelDslArgs())

/** Speeds up the mini-notation string pattern by `factor` — alias for [fast]. */
@StrudelDsl
fun String.extend(factor: PatternLike): StrudelPattern =
    this._extend(listOf(factor).asStrudelDslArgs())

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

internal val _iter by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyIter(pattern, args.take(1))
}

internal val StrudelPattern._iter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIter(p, args)
}

internal val String._iter by dslStringExtension { p, args, callInfo -> p._iter(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Divides the pattern into `n` slices and shifts the view forward by one slice each cycle.
 *
 * Each cycle `c` starts at offset `(c % n) / n` within the pattern, creating a rotating effect.
 * Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @param pattern The pattern to iterate over (top-level call only).
 * @return A pattern that rotates forward by one slice each cycle.
 *
 * ```KlangScript
 * note("c d e f").iter(4)  // cycle 0: c d e f, cycle 1: d e f c, …
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").iter(4)  // rotating drum pattern every cycle
 * ```
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@StrudelDsl
fun iter(n: Int, pattern: PatternLike): StrudelPattern = _iter(listOf(n, pattern).asStrudelDslArgs())

/** Rotates the pattern forward by one slice each cycle, dividing into `n` slices. */
@StrudelDsl
fun StrudelPattern.iter(n: Int): StrudelPattern = this._iter(listOf(n).asStrudelDslArgs())

/** Rotates the mini-notation string pattern forward by one slice each cycle. */
@StrudelDsl
fun String.iter(n: Int): StrudelPattern = this._iter(listOf(n).asStrudelDslArgs())

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

internal val _iterBack by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.drop(1).toPattern()
    applyIterBack(pattern, args.take(1))
}

internal val StrudelPattern._iterBack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyIterBack(p, args)
}

internal val String._iterBack by dslStringExtension { p, args, callInfo -> p._iterBack(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Divides the pattern into `n` slices and shifts the view backward by one slice each cycle.
 *
 * Like [iter] but in the opposite direction: each cycle `c` starts later within the pattern,
 * rotating it right. Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @param pattern The pattern to iterate over (top-level call only).
 * @return A pattern that rotates backward by one slice each cycle.
 *
 * ```KlangScript
 * note("c d e f").iterBack(4)  // cycle 0: c d e f, cycle 1: f c d e, …
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").iterBack(4)  // backward-rotating drum pattern
 * ```
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@StrudelDsl
fun iterBack(n: Int, pattern: PatternLike): StrudelPattern =
    _iterBack(listOf(n, pattern).asStrudelDslArgs())

/** Rotates the pattern backward by one slice each cycle, dividing into `n` slices. */
@StrudelDsl
fun StrudelPattern.iterBack(n: Int): StrudelPattern = this._iterBack(listOf(n).asStrudelDslArgs())

/** Rotates the mini-notation string pattern backward by one slice each cycle. */
@StrudelDsl
fun String.iterBack(n: Int): StrudelPattern = this._iterBack(listOf(n).asStrudelDslArgs())

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

internal val _invert by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern()
    applyInvert(pattern)
}

internal val _inv by dslPatternFunction { args, callInfo -> _invert(args, callInfo) }

internal val StrudelPattern._invert by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyInvert(p)
}

internal val StrudelPattern._inv by dslPatternExtension { p, args, callInfo -> p._invert(args, callInfo) }

internal val String._invert by dslStringExtension { p, args, callInfo -> p._invert(args, callInfo) }

internal val String._inv by dslStringExtension { p, args, callInfo -> p._inv(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Inverts boolean values in the pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * Useful for flipping structural masks (e.g. a euclidean rhythm) so that the silent beats
 * become active and vice-versa.
 *
 * @param pattern The pattern whose boolean values are inverted (top-level call only).
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript
 * "1 0 1 1".invert()  // produces 0 1 0 0
 * ```
 *
 * ```KlangScript
 * note("c d e f").struct("1 0 1 0").invert()  // swaps active and silent beats
 * ```
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@StrudelDsl
fun invert(pattern: PatternLike): StrudelPattern = _invert(listOf(pattern).asStrudelDslArgs())

/** Inverts boolean values in the pattern. */
@StrudelDsl
fun StrudelPattern.invert(): StrudelPattern = this._invert()

/** Inverts boolean values in the mini-notation string pattern. */
@StrudelDsl
fun String.invert(): StrudelPattern = this._invert()

/**
 * Alias for [invert]. Inverts boolean values in the pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * @param pattern The pattern whose boolean values are inverted (top-level call only).
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript
 * "1 0 1 1".inv()  // produces 0 1 0 0
 * ```
 *
 * ```KlangScript
 * note("c d e f").struct("1 0 1 0").inv()  // swaps active and silent beats
 * ```
 * @alias invert
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@StrudelDsl
fun inv(pattern: PatternLike): StrudelPattern = _inv(listOf(pattern).asStrudelDslArgs())

/** Inverts boolean values in the pattern — alias for [invert]. */
@StrudelDsl
fun StrudelPattern.inv(): StrudelPattern = this._inv()

/** Inverts boolean values in the mini-notation string pattern — alias for [invert]. */
@StrudelDsl
fun String.inv(): StrudelPattern = this._inv()

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

internal val _applyN by dslPatternFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0) ?: return@dslPatternFunction silence
    val func = args.getOrNull(1).toPatternMapper() ?: return@dslPatternFunction silence
    val pattern = args.getOrNull(2)?.toPattern() ?: return@dslPatternFunction silence

    applyApplyN(pattern, listOf(n, StrudelDslArg.of(func)))
}

internal val StrudelPattern._applyN by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyApplyN(p, args)
}

internal val String._applyN by dslStringExtension { p, args, callInfo -> p._applyN(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a mapper function to a pattern `n` times.
 *
 * Supports control patterns for `n`, so the repetition count can vary each cycle.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly to the pattern.
 * @param pattern The pattern to transform (top-level call only).
 * @return A pattern with `transform` applied `n` times.
 *
 * ```KlangScript
 * note("c d e f").applyN(2, x => x.fast(2))  // fast(2) applied twice
 * ```
 *
 * ```KlangScript
 * s("bd sd").applyN(3, x => x.echo(2, 0.25, 0.5))  // echo applied 3 times
 * ```
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@StrudelDsl
fun applyN(n: PatternLike, transform: PatternMapper, pattern: PatternLike): StrudelPattern =
    _applyN(listOf(n, transform, pattern).asStrudelDslArgs())

/** Applies `transform` to the pattern `n` times. */
@StrudelDsl
fun StrudelPattern.applyN(n: PatternLike, transform: PatternMapper): StrudelPattern =
    this._applyN(listOf(n, transform).asStrudelDslArgs())

/** Applies `transform` to the mini-notation string pattern `n` times. */
@StrudelDsl
fun String.applyN(n: PatternLike, transform: PatternMapper): StrudelPattern =
    this._applyN(listOf(n, transform).asStrudelDslArgs())

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
fun applyPressBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asRational ?: return@_innerJoin src

        src._fmap { value ->
            // Create atomic pattern with the value
            val atomicPattern = AtomicPattern.value(value)
            // Compress to [r, 1] - applyCompress handles control patterns internally
            applyCompress(atomicPattern, listOf(StrudelDslArg.of(r), StrudelDslArg.of(1.0)))
        }._squeezeJoin()
    }
}

internal val _pressBy by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternFunction silence
    }
    val pattern = args.drop(1).toPattern()
    applyPressBy(pattern, args.take(1))
}

internal val StrudelPattern._pressBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPressBy(p, args)
}

internal val String._pressBy by dslStringExtension { p, args, callInfo -> p._pressBy(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Syncopates the pattern by compressing each event to start at position `r` within its timespan.
 *
 * - `r = 0`: No compression (normal timing).
 * - `r = 0.5`: Events start halfway through their slot (classic syncopation).
 * - `r = 1`: Events compressed to the very end of their slot.
 *
 * Supports control patterns for `r`.
 *
 * @param r Compression ratio in the range [0, 1].
 * @param pattern The pattern to syncopate (top-level call only).
 * @return A syncopated pattern.
 *
 * ```KlangScript
 * s("bd mt sd ht").pressBy(0.5)  // classic syncopation
 * ```
 *
 * ```KlangScript
 * s("bd mt sd ht").pressBy("<0 0.5 0.25>")  // varying syncopation each cycle
 * ```
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@StrudelDsl
fun pressBy(r: PatternLike, pattern: PatternLike): StrudelPattern =
    _pressBy(listOf(r, pattern).asStrudelDslArgs())

/** Syncopates the pattern by compressing events to start at position `r`. */
@StrudelDsl
fun StrudelPattern.pressBy(r: PatternLike): StrudelPattern =
    this._pressBy(listOf(r).asStrudelDslArgs())

/** Syncopates the mini-notation string pattern by compressing events to start at position `r`. */
@StrudelDsl
fun String.pressBy(r: PatternLike): StrudelPattern =
    this._pressBy(listOf(r).asStrudelDslArgs())

// -- press() ----------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by shifting each event halfway into its timespan.
 * Equivalent to `pressBy(0.5)`.
 *
 * Example: s("bd mt sd ht").every(4, { it.press() })
 */
fun applyPress(pattern: StrudelPattern): StrudelPattern {
    return applyPressBy(pattern, listOf(StrudelDslArg.of(0.5)))
}

internal val _press by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern()
    applyPress(pattern)
}

internal val StrudelPattern._press by dslPatternExtension { p, _, /* callInfo */ _ ->
    applyPress(p)
}

internal val String._press by dslStringExtension { p, args, callInfo -> p._press(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Syncopates the pattern by shifting each event halfway into its timespan.
 *
 * Equivalent to `pressBy(0.5)`. Each event is compressed so it starts at the midpoint of its
 * original slot, creating a classic off-beat feel.
 *
 * @param pattern The pattern to syncopate (top-level call only).
 * @return A syncopated pattern where events start halfway through their slots.
 *
 * ```KlangScript
 * s("bd mt sd ht").press()  // classic off-beat feel
 * ```
 *
 * ```KlangScript
 * note("c d e f").every(4, x => x.press())  // press every 4th cycle
 * ```
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@StrudelDsl
fun press(pattern: PatternLike): StrudelPattern = _press(listOf(pattern).asStrudelDslArgs())

/** Syncopates the pattern by shifting events halfway into their timespan. */
@StrudelDsl
fun StrudelPattern.press(): StrudelPattern = this._press()

/** Syncopates the mini-notation string pattern by shifting events halfway. */
@StrudelDsl
fun String.press(): StrudelPattern = this._press()

// -- ribbon() ---------------------------------------------------------------------------------------------------------

fun applyRibbon(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val offsetArg = args.getOrNull(0) ?: StrudelDslArg.of(0.0)
    val cyclesArg = args.getOrNull(1) ?: StrudelDslArg.of(1.0)

    // pat.early(offset) -> use applyTimeShift with factor -1
    val shifted = applyTimeShift(pattern, listOf(offsetArg), Rational.MINUS_ONE)

    // pure(1).slow(cycles)
    // We use SequencePattern to ensure we get discrete events per cycle (or per 'cycles' duration),
    // which forces _bindRestart to re-trigger the pattern repeatedly (looping it).
    // If we just used AtomicPattern, it might produce a single long event, preventing the loop.
    val one = AtomicPattern(StrudelVoiceData.empty.copy(value = 1.asVoiceValue()))
    val pureOne = SequencePattern(listOf(one))

    val loopStructure = applySlow(pureOne, listOf(cyclesArg))

    // struct.restart(shifted)
    return loopStructure._bindRestart { shifted }
}

internal val StrudelPattern._ribbon by dslPatternExtension { p, args, /* callInfo */ _ -> applyRibbon(p, args) }

internal val String._ribbon by dslStringExtension { p, args, callInfo -> p._ribbon(args, callInfo) }

internal val StrudelPattern._rib by dslPatternExtension { p, args, callInfo -> p._ribbon(args, callInfo) }

internal val String._rib by dslStringExtension { p, args, callInfo -> p._ribbon(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * Imagine the entire timeline as a ribbon: `ribbon` cuts a piece starting at `offset` with length
 * `cycles`, then loops that piece indefinitely.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles.
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript
 * note("<c d e f>").ribbon(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").ribbon(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@StrudelDsl
fun StrudelPattern.ribbon(offset: PatternLike, cycles: PatternLike = 1.0): StrudelPattern =
    this._ribbon(listOf(offset, cycles).asStrudelDslArgs())

/** Loops a segment of the mini-notation string pattern starting at `offset` for `cycles` cycles. */
@StrudelDsl
fun String.ribbon(offset: PatternLike, cycles: PatternLike = 1.0): StrudelPattern =
    this._ribbon(listOf(offset, cycles).asStrudelDslArgs())

/**
 * Alias for [ribbon]. Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles.
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript
 * note("<c d e f>").rib(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").rib(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 * @alias ribbon
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@StrudelDsl
fun StrudelPattern.rib(offset: PatternLike, cycles: PatternLike = 1.0): StrudelPattern =
    this._rib(listOf(offset, cycles).asStrudelDslArgs())

/** Alias for [ribbon]. Loops a segment of the mini-notation string pattern. */
@StrudelDsl
fun String.rib(offset: PatternLike, cycles: PatternLike = 1.0): StrudelPattern =
    this._rib(listOf(offset, cycles).asStrudelDslArgs())
