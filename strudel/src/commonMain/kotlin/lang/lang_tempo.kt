@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
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
    factor: Rational = 1.0.toRational(),
): StrudelPattern {
    return when (args.size) {
        0 -> pattern
        1 if (args[0].value is Number) -> applyTimeShift(
            pattern = pattern,
            offset = (args[0].value?.asDoubleOrNull()?.toRational() ?: Rational.ZERO) * factor,
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

private fun applySlow(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val factorArg = args.firstOrNull()

    // Parse the factor argument into a pattern
    val factorPattern: StrudelPattern = when (val factorVal = factorArg?.value) {
        is StrudelPattern -> factorVal

        else -> parseMiniNotation(factorArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for optimization
    val staticFactor = factorArg?.value?.asDoubleOrNull()

    return if (staticFactor != null) {
        // Static path: use the simple TempoModifierPattern
        TempoModifierPattern(source = pattern, factor = staticFactor, invertPattern = false)
    } else {
        // Dynamic path: use pattern-controlled tempo modification
        TempoModifierPatternWithControl(source = pattern, factorPattern = factorPattern, invertPattern = false)
    }
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

    val source = sourceParts.toPattern(defaultModifier)
    applySlow(source, listOf(factorArg))
}

@StrudelDsl
val StrudelPattern.slow by dslPatternExtension { p, args, /* callInfo */ _ -> applySlow(p, args) }

@StrudelDsl
val String.slow by dslStringExtension { p, args, callInfo -> p.slow(args, callInfo) }

// -- fast() -----------------------------------------------------------------------------------------------------------

private fun applyFast(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val factorArg = args.firstOrNull()

    // Parse the factor argument into a pattern
    val factorPattern: StrudelPattern = when (val factorVal = factorArg?.value) {
        is StrudelPattern -> factorVal

        else -> parseMiniNotation(factorArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for optimization
    val staticFactor = factorArg?.value?.asDoubleOrNull()

    return if (staticFactor != null) {
        // Static path: use the simple TempoModifierPattern
        TempoModifierPattern(source = pattern, factor = staticFactor, invertPattern = true)
    } else {
        // Dynamic path: use pattern-controlled tempo modification
        TempoModifierPatternWithControl(source = pattern, factorPattern = factorPattern, invertPattern = true)
    }
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

    val source = sourceParts.toPattern(defaultModifier)
    applyFast(source, listOf(factorArg))
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslPatternExtension { p, args, /* callInfo */ _ -> applyFast(p, args) }

@StrudelDsl
val String.fast by dslStringExtension { p, args, callInfo -> p.fast(args, callInfo) }

// -- rev() ------------------------------------------------------------------------------------------------------------

private fun applyRev(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()

    // Parse the argument to detect if it's a plain number or a pattern
    val nPattern = when (val nVal = nArg?.value) {
        is StrudelPattern -> nVal

        else -> {
            // Parse as mini-notation (handles numbers and strings)
            parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
                AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
            }
        }
    }

    // Try to extract a static integer value for optimization
    val staticN = nArg?.asIntOrNull()

    return if (staticN != null) {
        // Static value optimization
        if (staticN <= 1) {
            ReversePattern(pattern)
        } else {
            // Reverses every n-th cycle by speeding up, reversing, then slowing back down
            pattern.fast(staticN).rev().slow(staticN)
        }
    } else {
        // Use pattern-controlled reversal
        ReversePatternWithControl(pattern, nPattern)
    }
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

// -- palindrome() -----------------------------------------------------------------------------------------------------

private fun applyPalindrome(pattern: StrudelPattern): StrudelPattern {
    return applyCat(listOf(pattern, applyRev(pattern, listOf(StrudelDslArg(1, null)))))
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
    applyTimeShift(p, args, (-1.0).toRational())
}

@StrudelDsl
val String.early by dslStringExtension { p, args, callInfo -> p.early(args, callInfo) }

// -- late() -----------------------------------------------------------------------------------------------------------


/** Nudges the pattern to start later in time by the given number of cycles */
@StrudelDsl
val late by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

@StrudelDsl
val StrudelPattern.late by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTimeShift(p, args, 1.0.toRational())
}

@StrudelDsl
val String.late by dslStringExtension { p, args, callInfo -> p.late(args, callInfo) }

// -- compress() -------------------------------------------------------------------------------------------------------

private fun applyCompress(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val startArg = args[0].value?.asDoubleOrNull() ?: 0.0
    val endArg = args[1].value?.asDoubleOrNull() ?: 1.0

    return CompressPattern(
        source = pattern,
        start = startArg.toRational(),
        end = endArg.toRational()
    )
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val compress by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val startArg = args[0].value?.asDoubleOrNull() ?: 0.0
    val endArg = args[1].value?.asDoubleOrNull() ?: 1.0
    val pattern = args.drop(2).toPattern(defaultModifier)

    CompressPattern(
        source = pattern,
        start = startArg.toRational(),
        end = endArg.toRational()
    )
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val StrudelPattern.compress by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCompress(p, args)
}

/** Compresses pattern into the given timespan, leaving a gap */
@StrudelDsl
val String.compress by dslStringExtension { p, args, callInfo -> p.compress(args, callInfo) }

// -- ply() ------------------------------------------------------------------------------------------------------------

private fun applyPly(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val n = args[0].value?.asIntOrNull() ?: 1
    if (n <= 1) {
        return pattern
    }

    return PlyPattern(source = pattern, n = n)
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val ply by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val n = args[0].value?.asIntOrNull() ?: 1
    val pattern = args.drop(1).toPattern(defaultModifier)

    if (n <= 1) {
        pattern
    } else {
        PlyPattern(source = pattern, n = n)
    }
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val StrudelPattern.ply by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPly(p, args)
}

/** Repeats each event n times within its timespan */
@StrudelDsl
val String.ply by dslStringExtension { p, args, callInfo -> p.ply(args, callInfo) }
