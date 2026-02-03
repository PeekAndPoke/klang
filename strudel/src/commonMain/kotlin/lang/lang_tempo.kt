@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.stretchBy
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.*

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTempoInit = false

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun applyTimeShift(
    pattern: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    factor: Rational = Rational.ONE,
): StrudelPattern {
    if (args.isEmpty()) return pattern

    val control = args[0].asControlValueProvider(StrudelVoiceValue.Num(0.0))

    return TimeShiftPattern(
        source = pattern,
        offsetProvider = control,
        factor = factor,
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Timing / Order modifiers
// ///

// -- slow() -----------------------------------------------------------------------------------------------------------

fun applySlow(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val factorProvider = args.firstOrNull()
        .asControlValueProvider(StrudelVoiceValue.Num(1.0))

    return TempoModifierPattern(
        source = pattern,
        factorProvider = factorProvider,
        invertPattern = false
    )
}

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val slow by dslFunction { args, /* callInfo */ _ ->
    val factorArg: StrudelDslArg<Any?>
    val sourceParts: List<StrudelDslArg<Any?>>

    // Heuristic: If >1 args, the first one is the factor, the rest is the source.
    // If only 1 arg, it is treated as the source (with factor 1.0).
    if (args.size > 1) {
        factorArg = args[0]
        sourceParts = args.drop(1)
    } else {
        factorArg = StrudelDslArg(1.0, null)
        sourceParts = args
    }

    val source = sourceParts.toPattern(voiceValueModifier)
    applySlow(source, listOf(factorArg))
}

@StrudelDsl
val StrudelPattern.slow by dslPatternExtension { p, args, /* callInfo */ _ -> applySlow(p, args) }

@StrudelDsl
val String.slow by dslStringExtension { p, args, callInfo -> p.slow(args, callInfo) }

// -- fast() -----------------------------------------------------------------------------------------------------------

fun applyFast(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val factorProvider = args.firstOrNull()
        .asControlValueProvider(StrudelVoiceValue.Num(1.0))

    return TempoModifierPattern(
        source = pattern,
        factorProvider = factorProvider,
        invertPattern = true
    )
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val fast by dslFunction { args, /* callInfo */ _ ->
    val factorArg: StrudelDslArg<Any?>
    val sourceParts: List<StrudelDslArg<Any?>>

    if (args.size > 1) {
        factorArg = args[0]
        sourceParts = args.drop(1)
    } else {
        factorArg = StrudelDslArg(1.0, null)
        sourceParts = args
    }

    val source = sourceParts.toPattern(voiceValueModifier)
    applyFast(source, listOf(factorArg))
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslPatternExtension { p, args, /* callInfo */ _ -> applyFast(p, args) }

@StrudelDsl
val String.fast by dslStringExtension { p, args, callInfo -> p.fast(args, callInfo) }

// -- rev() ------------------------------------------------------------------------------------------------------------

fun applyRev(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(StrudelVoiceValue.Num(1.0))

    return ReversePattern(inner = pattern, nProvider = nProvider)
}

/** Reverses the pattern */
@StrudelDsl
val rev by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyRev(pattern, args.take(1))
}

/** Reverses the pattern */
@StrudelDsl
val StrudelPattern.rev by dslPatternExtension { p, args, /* callInfo */ _ -> applyRev(p, args) }

/** Reverses the pattern */
@StrudelDsl
val String.rev by dslStringExtension { p, args, callInfo -> p.rev(args, callInfo) }

// -- revv() -----------------------------------------------------------------------------------------------------------

// TODO: deep checks ... does not seem to work to well for complex patterns
fun applyRevv(pattern: StrudelPattern): StrudelPattern {
    // Negates a time span: [begin, end] → [-end, -begin]
    val negateSpan: (TimeSpan) -> TimeSpan = { span ->
        TimeSpan(
            begin = Rational.ZERO - span.end,
            end = Rational.ZERO - span.begin
        )
    }

    // Transform both query spans and event spans
    return pattern._withQuerySpan(negateSpan)._withHapSpan(negateSpan)
}

/** Reverses the whole pattern across all cycles (not per-cycle) */
@StrudelDsl
val revv by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyRevv(pattern)
}

/** Reverses the whole pattern across all cycles (not per-cycle) */
@StrudelDsl
val StrudelPattern.revv by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyRevv(p)
}

/** Reverses the whole pattern across all cycles (not per-cycle) */
@StrudelDsl
val String.revv by dslStringExtension { p, args, callInfo -> p.revv(args, callInfo) }

// -- palindrome() -----------------------------------------------------------------------------------------------------

fun applyPalindrome(pattern: StrudelPattern): StrudelPattern {
    // Palindrome needs to play the pattern forward, then backward.
    // Critically, it must use ABSOLUTE time (Prime behavior) so that the 'rev' version
    // reverses the content of the *second* cycle, not the first cycle played again.
    // Matches JS: pat.lastOf(2, rev) -> equivalent to slowcatPrime(pat, rev(pat))
    return applySlowcatPrime(listOf(pattern, applyRev(pattern, listOf(StrudelDslArg(1, null)))))
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val palindrome by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyPalindrome(pattern)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val StrudelPattern.palindrome by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyPalindrome(p)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val String.palindrome by dslStringExtension { p, args, callInfo -> p.palindrome(args, callInfo) }

// -- early() ----------------------------------------------------------------------------------------------------------

/** Nudges the pattern to start earlier in time by the given number of cycles */
@StrudelDsl
val early by dslFunction { /* args */ _, /* callInfo */ _ ->
    silence
}

@StrudelDsl
val StrudelPattern.early by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.MINUS_ONE)
}

@StrudelDsl
val String.early by dslStringExtension { p, args, callInfo -> p.early(args, callInfo) }

// -- late() -----------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.late by dslPatternExtension { p, args, _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.ONE)
}

@StrudelDsl
val String.late by dslStringExtension { p, args, callInfo -> p.late(args, callInfo) }


/** Nudges the pattern to start later in time by the given number of cycles */
@StrudelDsl
val late by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

// -- compress() -------------------------------------------------------------------------------------------------------

fun applyCompress(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // We convert both arguments to patterns to support dynamic compress
    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    // Bind start...
    return startCtrl._bind { startEv ->
        val sVal = startEv.data.value?.asDouble ?: return@_bind null

        // ...bind end
        endCtrl._bind { endEv ->
            val eVal = endEv.data.value?.asDouble ?: return@_bind null

            val b = sVal.toRational()
            val e = eVal.toRational()

            // JS check: if (b.gt(e) || b.gt(1) || e.gt(1) || b.lt(0) || e.lt(0)) return silence
            if (b > e || b > Rational.ONE || e > Rational.ONE || b < Rational.ZERO || e < Rational.ZERO) {
                return@_bind null // effectively silence for this event
            }

            val duration = e - b
            if (duration == Rational.ZERO) return@_bind null

            val factor = Rational.ONE / duration

            // pat._fastGap(1 / (e - b))._late(b)
            // Note: using applyTimeShift for 'late' with factor=1.0 (Rational.ONE)
            val fastGapped = pattern._fastGap(factor)

            // _late(b) is implemented via applyTimeShift with factor=1
            // But we can just use the internal logic directly or call the helper
            // Here we use internal logic for efficiency inside the bind loop

            // _late(b) -> shift time +b
            fastGapped._withQueryTime { t -> t - b }.mapEvents { ev ->
                val shiftedPart = ev.part.shift(b)
                val shiftedWhole = ev.whole.shift(b)
                ev.copy(part = shiftedPart, whole = shiftedWhole)
            }
        }
    }
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val compress by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyCompress(pattern, args.take(2))
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val StrudelPattern.compress by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCompress(p, args)
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val String.compress by dslStringExtension { p, args, callInfo -> p.compress(args, callInfo) }

// -- focus() ----------------------------------------------------------------------------------------------------------

fun applyFocus(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) return source

    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    return startCtrl._bind { startEv ->
        val sVal = startEv.data.value?.asDouble ?: return@_bind null

        endCtrl._bind { endEv ->
            val eVal = endEv.data.value?.asDouble ?: return@_bind null

            val s = sVal.toRational()
            val e = eVal.toRational()

            if (s >= e) return@_bind null

            val d = e - s
            val sFloored = s.floor()

            source._withQueryTime { t -> (t - s) / d + sFloored }.mapEvents { ev ->
                val scaledPart = ev.part.shift(-sFloored).scale(d).shift(s)
                val scaledWhole = ev.whole.shift(-sFloored).scale(d).shift(s)
                ev.copy(part = scaledPart, whole = scaledWhole)
            }
        }
    }
}

/** Focuses on a portion of each cycle, keeping original timing */
@StrudelDsl
val focus by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyFocus(pattern, args.take(2))
}

/** Focuses on a portion of each cycle, keeping original timing */
@StrudelDsl
val StrudelPattern.focus by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyFocus(p, args)
}

/** Focuses on a portion of each cycle, keeping original timing */
@StrudelDsl
val String.focus by dslStringExtension { p, args, callInfo -> p.focus(args, callInfo) }

// -- ply() ------------------------------------------------------------------------------------------------------------

fun applyPly(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorArg = args[0]

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()?.toRational()
    val newSteps = if (staticFactor != null) pattern.numSteps?.times(staticFactor) else null

    // Convert factor to pattern (supports static values and control patterns)
    val factorPattern = listOf(factorArg).toPattern(voiceValueModifier)

    val result = pattern._bindSqueeze { event ->
        // pure(x) -> infinite pattern of the event's data
        val infiniteAtom = AtomicInfinitePattern(event.data)

        // To support "Patterned Ply" (Tidal style) where the factor pattern is aligned with the cycle,
        // we must project the global factor pattern into the event's local timeframe.
        // factorPattern.zoom(begin, end) takes the slice of factor corresponding to the event
        // and stretches it to 0..1, which matches the squeezed context.
        val localFactor = if (staticFactor == null) {
            factorPattern.zoom(event.part.begin.toDouble(), event.part.end.toDouble())
        } else {
            factorPattern
        }

        // ._fast(factor)
        infiniteAtom.fast(localFactor)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val ply by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyPly(pattern, args.take(1))
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val StrudelPattern.ply by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPly(p, args)
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val String.ply by dslStringExtension { p, args, callInfo -> p.ply(args, callInfo) }

// -- plyWith() --------------------------------------------------------------------------------------------------------

/**
 * Helper function to apply a function n times to a value.
 * Equivalent to JS applyN(n, func, p).
 */
fun applyFunctionNTimes(n: Int, func: StrudelPatternMapper, pattern: StrudelPattern): StrudelPattern {
    var result = pattern
    repeat(n) {
        result = func(result)
    }
    return result
}

fun applyPlyWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]
    val func = funcArg.toPatternMapper() ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()?.toInt()
    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor.toRational())
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asDoubleOrNull()?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Create factor number of patterns, applying func 0, 1, 2, ... (factor-1) times
        // Equivalent to: cat(...listRange(0, factor - 1).map((i) => applyN(i, func, x)))
        val patterns = (0 until factor).map { i ->
            val atomPattern = AtomicInfinitePattern(event.data)
            applyFunctionNTimes(i, func, atomPattern)
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/** Repeats each event n times, applying function cumulatively (0, 1, 2... times) */
@StrudelDsl
val plyWith by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyPlyWith(pattern, args.take(2))
}

/** Repeats each event n times, applying function cumulatively (0, 1, 2... times) */
@StrudelDsl
val StrudelPattern.plyWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyWith(p, args)
}

/** Convenience function that takes lambda directly */
@StrudelDsl
fun StrudelPattern.plyWith(factor: Int, transform: StrudelPatternMapper): StrudelPattern {
    return this.plyWith(listOf(factor, transform).asStrudelDslArgs())
}

/** Repeats each event n times, applying function cumulatively (0, 1, 2... times) */
@StrudelDsl
val String.plyWith by dslStringExtension { p, args, callInfo -> p.plyWith(args, callInfo) }

/** Convenience function that takes lambda directly */
@StrudelDsl
fun String.plyWith(factor: Int, transform: StrudelPatternMapper): StrudelPattern {
    return this.plyWith(listOf(factor, transform).asStrudelDslArgs())
}

/** Alias for plyWith */
@StrudelDsl
val plywith by dslFunction { args, callInfo -> plyWith(args, callInfo) }

/** Alias for plyWith */
@StrudelDsl
val StrudelPattern.plywith by dslPatternExtension { p, args, callInfo -> p.plyWith(args, callInfo) }

/** Alias for plyWith */
@StrudelDsl
fun StrudelPattern.plywith(factor: Int, transform: StrudelPatternMapper): StrudelPattern {
    return this.plyWith(factor, transform)
}

/** Alias for plyWith */
@StrudelDsl
val String.plywith by dslStringExtension { p, args, callInfo -> p.plyWith(args, callInfo) }

/** Alias for plyWith */
@StrudelDsl
fun String.plywith(factor: Int, transform: StrudelPatternMapper): StrudelPattern {
    return this.plyWith(factor, transform)
}

// -- plyForEach() -----------------------------------------------------------------------------------------------------

fun applyPlyForEach(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]

    // Extract the function from the argument
    val func = funcArg.value as? ((StrudelPattern, Int) -> StrudelPattern) ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()?.toInt()
    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor.toRational())
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asDoubleOrNull()?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Start with the original value, then add transformed versions for i = 1 to factor-1
        // Equivalent to: cat(pure(x), ...listRange(1, factor - 1).map((i) => func(pure(x), i)))
        val atomPattern = AtomicInfinitePattern(event.data)

        val patterns = buildList {
            // First pattern is the original
            add(atomPattern)
            // Then add transformed patterns for i = 1 to factor-1
            for (i in 1 until factor) {
                add(func(atomPattern, i))
            }
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/** Repeats each event n times, passing iteration index to function */
@StrudelDsl
val plyForEach by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyPlyForEach(pattern, args.take(2))
}

/** Repeats each event n times, passing iteration index to function */
@StrudelDsl
val StrudelPattern.plyForEach by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyForEach(p, args)
}

/** Convenience function that takes lambda directly */
@StrudelDsl
fun StrudelPattern.plyForEach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern {
    return this.plyForEach(listOf(factor, transform).asStrudelDslArgs())
}

/** Repeats each event n times, passing iteration index to function */
@StrudelDsl
val String.plyForEach by dslStringExtension { p, args, callInfo -> p.plyForEach(args, callInfo) }

/** Convenience function that takes lambda directly */
@StrudelDsl
fun String.plyForEach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern {
    return this.plyForEach(listOf(factor, transform).asStrudelDslArgs())
}

/** Alias for plyForEach */
@StrudelDsl
val plyforeach by dslFunction { args, callInfo -> plyForEach(args, callInfo) }

/** Alias for plyForEach */
@StrudelDsl
val StrudelPattern.plyforeach by dslPatternExtension { p, args, callInfo -> p.plyForEach(args, callInfo) }

/** Alias for plyForEach */
@StrudelDsl
fun StrudelPattern.plyforeach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern {
    return this.plyForEach(factor, transform)
}

/** Alias for plyForEach */
@StrudelDsl
val String.plyforeach by dslStringExtension { p, args, callInfo -> p.plyForEach(args, callInfo) }

/** Alias for plyForEach */
@StrudelDsl
fun String.plyforeach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern {
    return this.plyForEach(factor, transform)
}

// -- hurry() ----------------------------------------------------------------------------------------------------------

fun applyHurry(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern
    // 1. fast(factor)
    val spedUp = applyFast(pattern, listOf(factorArg))
    // 2. speed = speed * factor
    return spedUp._liftNumericField(listOf(factorArg)) { factor ->
        val f = factor ?: 1.0
        val currentSpeed = speed ?: 1.0
        copy(speed = currentSpeed * f)
    }
}

/** Speeds up pattern and increases speed parameter by the same factor */
@StrudelDsl
val hurry by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyHurry(pattern, args.take(1))
}

/** Speeds up pattern and increases speed parameter by the same factor */
@StrudelDsl
val StrudelPattern.hurry by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyHurry(p, args)
}

/** Speeds up pattern and increases speed parameter by the same factor */
@StrudelDsl
val String.hurry by dslStringExtension { p, args, callInfo -> p.hurry(args, callInfo) }

// -- fastGap() --------------------------------------------------------------------------------------------------------

fun applyFastGap(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(StrudelVoiceValue.Num(1.0))

    return FastGapPattern(source = pattern, factorProvider = factorProvider)
}

/** Speeds up pattern like fast, but plays once per cycle with gaps (alias: densityGap) */
@StrudelDsl
val fastGap by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val factor = args[0].value?.asDoubleOrNull() ?: 1.0
    val pattern = args.drop(1).toPattern(voiceValueModifier)

    if (factor <= 0.0 || factor == 1.0) {
        pattern
    } else {
        FastGapPattern.static(source = pattern, factor = factor.toRational())
    }
}

/** Speeds up pattern like fast, but plays once per cycle with gaps (alias: densityGap) */
@StrudelDsl
val StrudelPattern.fastGap by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyFastGap(p, args)
}

/** Speeds up pattern like fast, but plays once per cycle with gaps (alias: densityGap) */
@StrudelDsl
val String.fastGap by dslStringExtension { p, args, callInfo -> p.fastGap(args, callInfo) }

/** Alias for fastGap */
@StrudelDsl
val densityGap by dslFunction { args, callInfo -> fastGap(args, callInfo) }

/** Alias for fastGap */
@StrudelDsl
val StrudelPattern.densityGap by dslPatternExtension { p, args, callInfo -> p.fastGap(args, callInfo) }

/** Alias for fastGap */
@StrudelDsl
val String.densityGap by dslStringExtension { p, args, callInfo -> p.fastGap(args, callInfo) }

// -- inside() ---------------------------------------------------------------------------------------------------------

fun applyInside(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) return pattern

    val factorArg = args[0]
    val func = args[1].toPatternMapper() ?: return pattern

    val slowed = pattern.slow(factorArg)
    val transformed = func(slowed)

    return transformed.fast(factorArg)
}

/**
 * Carries out an operation 'inside' a cycle.
 * Slows the pattern by factor, applies the function, then speeds it back up.
 * @example note("0 1 2 3").inside(4) { it.rev() }
 */
@StrudelDsl
val StrudelPattern.inside by dslPatternExtension { p, args, /* callInfo */ _ -> applyInside(p, args) }

// -- outside() --------------------------------------------------------------------------------------------------------

fun applyOutside(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // TODO: support control pattern for "factor"
    val factor = args[0].value?.asDoubleOrNull() ?: 1.0
    val func = args[1].toPatternMapper() ?: return pattern

    val sped = pattern.fast(factor)
    val transformed = func(sped)

    return transformed.slow(factor)
}

/**
 * Carries out an operation 'outside' a cycle.
 * Speeds the pattern by factor, applies the function, then slows it back down.
 * @example note("0 1 2 3").outside(4) { it.rev() }
 */
@StrudelDsl
val StrudelPattern.outside by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyOutside(p, args)
}

// -- swingBy() --------------------------------------------------------------------------------------------------------

fun applySwingBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return pattern._innerJoin(args) { source, swing, n ->
        val swingValue = swing?.asDouble ?: return@_innerJoin silence
        val nVal = n?.asDouble ?: 1.0

        val timing = seq(0, swingValue / 2)
        val stretch = seq(1 + swingValue, 1 - swingValue)

        val transform: StrudelPatternMapper = { innerPat ->
            if (swingValue >= 0) {
                innerPat.late(timing).stretchBy(stretch)
            } else {
                innerPat.stretchBy(stretch).late(timing)
            }
        }

        source.inside(nVal, transform)
    }
}

/**
 * Creates a swing or shuffle rhythm by adjusting event timing and duration within subdivisions.
 *
 * Divides each cycle into `n` subdivisions. Within each subdivision, events are split into two groups:
 * - First half: gets (1 + swing) / 2 of the subdivision duration
 * - Second half: gets (1 - swing) / 2 of the subdivision duration
 *
 * This creates natural rhythmic patterns without overlapping events:
 * - Positive swing (e.g., 1/3): "long-short" pattern (classic swing feel)
 * - Negative swing (e.g., -1/3): "short-long" pattern (reverse swing)
 * - Zero swing: Equal durations (no swing)
 *
 * @param swing Swing amount (-1 to 1). Controls timing offset and duration ratio
 * @param n Number of subdivisions per cycle
 * @example sound("hh*8").swingBy(1/3, 4)  // Classic swing on hi-hats
 * @example note("c d e f").swingBy(-0.25, 2)  // Reverse swing on notes
 */
@StrudelDsl
val StrudelPattern.swingBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySwingBy(p, args)
}

@StrudelDsl
val String.swingBy by dslStringExtension { p, args, callInfo -> p.swingBy(args, callInfo) }

// -- swing() ----------------------------------------------------------------------------------------------------------

/**
 * Creates a swing rhythm with default 1/3 swing amount.
 *
 * This is a shorthand for swingBy(1/3, n), creating the classic swing/shuffle feel
 * where events are played in a "long-short" pattern.
 *
 * @param n Number of subdivisions per cycle
 * @example sound("hh*8").swing(4)  // Classic swing on hi-hats
 * @example note("c d e f g a b c").swing(2)  // Swing on melody
 */
@StrudelDsl
val StrudelPattern.swing by dslPatternExtension { p, args, /* callInfo */ _ ->
    if (args.isEmpty()) {
        return@dslPatternExtension p
    }

    val nArg = args.getOrNull(0)

    // swing(n) = swingBy(1/3, n)
    p.swingBy(StrudelDslArg.of(1.0 / 3.0), nArg ?: StrudelDslArg.of(1.0))
}

@StrudelDsl
val String.swing by dslStringExtension { p, args, callInfo -> p.swing(args, callInfo) }

// -- brak() -----------------------------------------------------------------------------------------------------------

/**
 * Makes every other cycle syncopated (breakbeat-style).
 *
 * JavaScript: `pat.when(slowcat(false, true), (x) => fastcat(x, silence)._late(0.25))`
 *
 * Effect:
 * - Cycle 0: plays normally
 * - Cycle 1: plays first half then silence, delayed by 0.25 cycles (syncopation)
 * - Cycle 2: plays normally
 * - Cycle 3: syncopated
 * - etc.
 */
fun applyBrak(pattern: StrudelPattern): StrudelPattern {
    // when(slowcat(false, true), x => fastcat(x, silence).late(0.25))
    val condition = applySlowcat(
        listOf(
            pure(false),
            pure(true)
        )
    )

    return pattern.`when`(condition) { x ->
        fastcat(x, silence).late(0.25.toRational())
    }
}

/** Makes every other cycle syncopated */
@StrudelDsl
val brak by dslFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern(voiceValueModifier)
    applyBrak(pattern)
}

/** Makes every other cycle syncopated */
@StrudelDsl
val StrudelPattern.brak by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyBrak(p)
}

/** Makes every other cycle syncopated */
@StrudelDsl
val String.brak by dslStringExtension { p, args, callInfo -> p.brak(args, callInfo) }
