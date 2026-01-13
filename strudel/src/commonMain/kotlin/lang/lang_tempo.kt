@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.ReversePattern
import io.peekandpoke.klang.strudel.pattern.TempoModifierPattern
import io.peekandpoke.klang.strudel.pattern.TimeShiftPattern
import io.peekandpoke.klang.strudel.pattern.TimeShiftPatternWithControl

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTempoInit = false

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun applyTimeShift(
    pattern: StrudelPattern,
    args: List<Any?>,
    factor: Rational = 1.0.toRational(),
): StrudelPattern {
    return when (args.size) {
        0 -> pattern
        1 if (args[0] is Number) -> applyTimeShift(
            pattern = pattern,
            offset = (args[0]?.asDoubleOrNull()?.toRational() ?: Rational.ZERO) * factor,
        )

        else -> applyTimeShift(
            pattern = pattern,
            control = args.toPattern(defaultModifier).mul(factor),
        )
    }
}

private fun applyTimeShift(pattern: StrudelPattern, offset: Rational): StrudelPattern {
    return TimeShiftPattern(pattern, offset)
}

private fun applyTimeShift(pattern: StrudelPattern, control: StrudelPattern): StrudelPattern {
    return TimeShiftPatternWithControl(pattern, control)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Timing / Order modifiers
// ///

// -- slow() -----------------------------------------------------------------------------------------------------------

private fun applySlow(pattern: StrudelPattern, factorArg: Any?): StrudelPattern {
    val factor = factorArg?.asDoubleOrNull() ?: 1.0
    return TempoModifierPattern(pattern, factor = factor, invertPattern = false)
}

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val slow by dslFunction { args ->
    val factor: Any?
    val sourceParts: List<Any?>

    // Heuristic: If >1 args, the first one is the factor, the rest is the source.
    // If only 1 arg, it is treated as the source (with factor 1.0).
    if (args.size > 1) {
        factor = args[0]
        sourceParts = args.drop(1)
    } else {
        factor = 1.0
        sourceParts = args
    }

    val source = sourceParts.toPattern(defaultModifier)
    applySlow(source, factor)
}

@StrudelDsl
val StrudelPattern.slow by dslPatternExtension { p, args ->
    applySlow(p, args.firstOrNull())
}

@StrudelDsl
val String.slow by dslStringExtension { p, args ->
    applySlow(p, args.firstOrNull())
}

// -- fast() -----------------------------------------------------------------------------------------------------------

private fun applyFast(pattern: StrudelPattern, factorArg: Any?): StrudelPattern {
    val factor = factorArg?.asDoubleOrNull() ?: 1.0
    return TempoModifierPattern(pattern, factor = factor, invertPattern = true)
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val fast by dslFunction { args ->
    val factor: Any?
    val sourceParts: List<Any?>

    if (args.size > 1) {
        factor = args[0]
        sourceParts = args.drop(1)
    } else {
        factor = 1.0
        sourceParts = args
    }

    val source = sourceParts.toPattern(defaultModifier)
    applyFast(source, factor)
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslPatternExtension { p, args ->
    applyFast(p, args.firstOrNull())
}

@StrudelDsl
val String.fast by dslStringExtension { p, args ->
    applyFast(p, args.firstOrNull())
}

// -- rev() ------------------------------------------------------------------------------------------------------------

private fun applyRev(pattern: StrudelPattern, n: Int): StrudelPattern {
    // TODO: Make this parameter available through a pattern as well (e.g. sine)
    return if (n <= 1) {
        ReversePattern(pattern)
    } else {
        // Reverses every n-th cycle by speeding up, reversing, then slowing back down
        pattern.fast(n).rev().slow(n)
    }
}

/** Reverses the pattern */
@StrudelDsl
val rev by dslFunction { args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyRev(pattern, n)
}

/** Reverses the pattern */
@StrudelDsl
val StrudelPattern.rev by dslPatternExtension { p, args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    applyRev(p, n)
}

/** Reverses the pattern */
@StrudelDsl
val String.rev by dslStringExtension { p, args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    applyRev(p, n)
}

// -- palindrome() -----------------------------------------------------------------------------------------------------

private fun applyPalindrome(pattern: StrudelPattern): StrudelPattern {
    return applyCat(listOf(pattern, applyRev(pattern, 1)))
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val palindrome by dslFunction { args ->
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyPalindrome(pattern)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val StrudelPattern.palindrome by dslPatternExtension { p, _ ->
    applyPalindrome(p)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val String.palindrome by dslStringExtension { p, _ ->
    applyPalindrome(p)
}

// -- early() ----------------------------------------------------------------------------------------------------------

/** Nudges the pattern to start earlier in time by the given number of cycles */
@StrudelDsl
val early by dslFunction {
    silence
}

@StrudelDsl
val StrudelPattern.early by dslPatternExtension { p, args ->
    applyTimeShift(p, args, (-1.0).toRational())
}

@StrudelDsl
val String.early by dslStringExtension { p, args ->
    applyTimeShift(p, args, (-1.0).toRational())
}

// -- late() -----------------------------------------------------------------------------------------------------------


/** Nudges the pattern to start later in time by the given number of cycles */
@StrudelDsl
val late by dslFunction {
    silence
}

@StrudelDsl
val StrudelPattern.late by dslPatternExtension { p, args ->
    applyTimeShift(p, args, 1.0.toRational())
}

@StrudelDsl
val String.late by dslStringExtension { p, args ->
    applyTimeShift(p, args, 1.0.toRational())
}
