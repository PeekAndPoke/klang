@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
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
    val argVal = args.getOrNull(0)?.value

    return when (args.size) {
        0 -> pattern

        1 if (argVal is Rational) -> applyTimeShift(
            pattern = pattern,
            offset = argVal,
        )

        1 if (argVal is Number) -> applyTimeShift(
            pattern = pattern,
            offset = (argVal.toRational()) * factor,
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
    val factorVal = factorArg?.value

    // Parse the factor argument into a pattern
    val factorPattern: StrudelPattern = when (val factorVal = factorArg?.value) {
        is StrudelPattern -> factorVal

        else -> parseMiniNotation(factorArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for optimization
    val staticFactor = when (factorVal) {
        is Rational -> factorVal
        is Number -> factorVal.toRational()
        else -> null
    }

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
    val factorVal = factorArg?.value

    // Parse the factor argument into a pattern
    val factorPattern: StrudelPattern = when (val factorVal = factorArg?.value) {
        is StrudelPattern -> factorVal

        else -> parseMiniNotation(factorArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for optimization
    val staticFactor = when (factorVal) {
        is Rational -> factorVal
        is Number -> factorVal.toRational()
        else -> null
    }

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

    val startArg = args.getOrNull(0)
    val endArg = args.getOrNull(1)

    // Parse start argument into a pattern
    val startPattern: StrudelPattern = when (val startVal = startArg?.value) {
        is StrudelPattern -> startVal
        else -> parseMiniNotation(startArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Parse end argument into a pattern
    val endPattern: StrudelPattern = when (val endVal = endArg?.value) {
        is StrudelPattern -> endVal
        else -> parseMiniNotation(endArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have static values for optimization
    val staticStart = startArg?.value?.asDoubleOrNull()
    val staticEnd = endArg?.value?.asDoubleOrNull()

    return if (staticStart != null && staticEnd != null) {
        // Static path: use the simple CompressPattern
        CompressPattern(
            source = pattern,
            start = staticStart.toRational(),
            end = staticEnd.toRational()
        )
    } else {
        // Dynamic path: use pattern-controlled compress
        CompressPatternWithControl(
            source = pattern,
            startPattern = startPattern,
            endPattern = endPattern
        )
    }
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

// -- focus() ----------------------------------------------------------------------------------------------------------

private fun applyFocus(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val startArg = args.getOrNull(0)
    val endArg = args.getOrNull(1)

    // Parse start argument into a pattern
    val startPattern: StrudelPattern = when (val startVal = startArg?.value) {
        is StrudelPattern -> startVal
        else -> parseMiniNotation(startArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Parse end argument into a pattern
    val endPattern: StrudelPattern = when (val endVal = endArg?.value) {
        is StrudelPattern -> endVal
        else -> parseMiniNotation(endArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have static values for optimization
    val staticStart = startArg?.value?.asDoubleOrNull()
    val staticEnd = endArg?.value?.asDoubleOrNull()

    return if (staticStart != null && staticEnd != null) {
        // Static path: use the simple FocusPattern
        FocusPattern(
            source = pattern,
            start = staticStart.toRational(),
            end = staticEnd.toRational()
        )
    } else {
        // Dynamic path: use pattern-controlled focus
        FocusPatternWithControl(
            source = pattern,
            startPattern = startPattern,
            endPattern = endPattern
        )
    }
}

/** Focuses on a portion of each cycle, keeping original timing */
@StrudelDsl
val focus by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslFunction silence
    }

    val startArg = args[0].value?.asDoubleOrNull() ?: 0.0
    val endArg = args[1].value?.asDoubleOrNull() ?: 1.0
    val pattern = args.drop(2).toPattern(defaultModifier)

    FocusPattern(
        source = pattern,
        start = startArg.toRational(),
        end = endArg.toRational()
    )
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

private fun applyPly(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val nArg = args.firstOrNull()

    // Parse the n argument into a pattern
    val nPattern: StrudelPattern = when (val nVal = nArg?.value) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.defaultModifier(text))
        }
    }

    // Check if we have a static value for optimization
    val staticN = nArg?.value?.asIntOrNull()

    return if (staticN != null) {
        // Static path: use the simple PlyPattern
        if (staticN <= 1) {
            pattern
        } else {
            PlyPattern(source = pattern, n = staticN)
        }
    } else {
        // Dynamic path: use pattern-controlled ply
        PlyPatternWithControl(source = pattern, nPattern = nPattern)
    }
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

// -- hurry() ----------------------------------------------------------------------------------------------------------

private fun applyHurry(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

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
        // Static path: use the simple HurryPattern
        if (staticFactor <= 0.0 || staticFactor == 1.0) {
            pattern
        } else {
            HurryPattern(source = pattern, factor = staticFactor)
        }
    } else {
        // Dynamic path: use pattern-controlled hurry
        HurryPatternWithControl(source = pattern, factorPattern = factorPattern)
    }
}

/** Speeds up pattern and increases speed parameter by the same factor */
@StrudelDsl
val hurry by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val factor = args[0].value?.asDoubleOrNull() ?: 1.0
    val pattern = args.drop(1).toPattern(defaultModifier)

    if (factor <= 0.0 || factor == 1.0) {
        pattern
    } else {
        HurryPattern(source = pattern, factor = factor)
    }
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

private fun applyFastGap(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

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
        // Static path: use the simple FastGapPattern
        if (staticFactor <= 0.0 || staticFactor == 1.0) {
            pattern
        } else {
            FastGapPattern(source = pattern, factor = staticFactor)
        }
    } else {
        // Dynamic path: use pattern-controlled fastGap
        FastGapPatternWithControl(source = pattern, factorPattern = factorPattern)
    }
}

/** Speeds up pattern like fast, but plays once per cycle with gaps (alias: densityGap) */
@StrudelDsl
val fastGap by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslFunction silence
    }

    val factor = args[0].value?.asDoubleOrNull() ?: 1.0
    val pattern = args.drop(1).toPattern(defaultModifier)

    if (factor <= 0.0 || factor == 1.0) {
        pattern
    } else {
        FastGapPattern(source = pattern, factor = factor)
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

/**
 * Carries out an operation 'inside' a cycle.
 * Slows the pattern by factor, applies the function, then speeds it back up.
 * @example note("0 1 2 3").inside(4) { it.rev() }
 */
@StrudelDsl
val StrudelPattern.inside by dslPatternExtension { p, args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternExtension p
    }

    val factor = args[0].value?.asDoubleOrNull() ?: 1.0

    @Suppress("UNCHECKED_CAST")
    val func: (StrudelPattern) -> StrudelPattern =
        args[1].value as? (StrudelPattern) -> StrudelPattern ?: return@dslPatternExtension p

    val slowed = p.slow(factor)
    val transformed = func(slowed)

    transformed.fast(factor)
}

// -- outside() --------------------------------------------------------------------------------------------------------

/**
 * Carries out an operation 'outside' a cycle.
 * Speeds the pattern by factor, applies the function, then slows it back down.
 * @example note("0 1 2 3").outside(4) { it.rev() }
 */
@StrudelDsl
val StrudelPattern.outside by dslPatternExtension { p, args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternExtension p
    }

    val factor = args[0].value?.asDoubleOrNull() ?: 1.0

    @Suppress("UNCHECKED_CAST")
    val func: (StrudelPattern) -> StrudelPattern =
        args[1].value as? (StrudelPattern) -> StrudelPattern ?: return@dslPatternExtension p

    val sped = p.fast(factor)
    val transformed = func(sped)

    transformed.slow(factor)
}

// -- swingBy() --------------------------------------------------------------------------------------------------------

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
    if (args.size < 2) {
        return@dslPatternExtension p
    }

    val swing = args.getOrNull(0)
        .asControlValueProvider(StrudelVoiceValue.Num(0.0))

    val n = args.getOrNull(1)
        .asControlValueProvider(StrudelVoiceValue.Num(1.0))

    SwingPattern(source = p, swingProvider = swing, nProvider = n)
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
