// strudel/src/commonMain/kotlin/lang/lang_conditional.kt
@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.not
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.ArrangementPattern
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.FirstOfPattern
import io.peekandpoke.klang.strudel.pattern.LastOfPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

private fun applyFirstOf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    val nVal = nArg?.value

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    // Parse n as a pattern
    val nPattern = when (nVal) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticN = nVal?.asIntOrNull()

    if (staticN != null) {
        // Static path
        if (staticN <= 1) return transform(source)

        // Construct a list of patterns: [transform(source), source, source, ... (n-1 times)]
        val patterns = ArrayList<Pair<Double, StrudelPattern>>(staticN)
        patterns.add(1.0 to transform(source))
        repeat(staticN - 1) {
            patterns.add(1.0 to source)
        }

        return ArrangementPattern(patterns)
    } else {
        // Dynamic path: Use FirstOfWithControl
        return FirstOfPattern(source, nPattern, transform)
    }
}

/**
 * Applies the given function every n cycles, starting from the first cycle.
 *
 * It essentially says: "Every n cycles, do this special thing on the first one."
 *
 * If you call:
 *
 * note("a b c d").firstOf(4, { it.rev() })
 *
 * then:
 * - Cycle 1: The pattern plays in reverse.
 * - Cycle 2: The pattern plays normally.
 * - Cycle 3: The pattern plays normally.
 * - Cycle 4: The pattern plays normally.
 * - Cycle 5: The pattern plays in reverse again (loop restarts).
 *
 * @param {n} - the number of cycles to repeat the function
 * @param {transform} - the function to apply to the first cycle
 */
@StrudelDsl
val firstOf by dslFunction { args, _ ->
    val n = args.getOrNull(0) ?: StrudelDslArg.of(1)

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    // TODO: parse mini
    val pat = args.getOrNull(2)?.value as? StrudelPattern ?: silence

    applyFirstOf(pat, listOf(n, transform).asStrudelDslArgs())
}

@StrudelDsl
val StrudelPattern.firstOf by dslPatternExtension { source, args, /* callInfo */ _ -> applyFirstOf(source, args) }

@StrudelDsl
val String.firstOf by dslStringExtension { source, args, /* callInfo */ _ -> applyFirstOf(source, args) }

// -- every() ----------------------------------------------------------------------------------------------------------

/** Alias for [firstOf] */
@StrudelDsl
val every by dslFunction { args, callInfo -> firstOf(args, callInfo) }

/** Alias for [firstOf] */
@StrudelDsl
val StrudelPattern.every by dslPatternExtension { source, args, callInfo -> source.firstOf(args, callInfo) }

/** Alias for [firstOf] */
@StrudelDsl
val String.every by dslStringExtension { source, args, callInfo -> source.firstOf(args, callInfo) }

// -- lastOf() ---------------------------------------------------------------------------------------------------------

private fun applyLastOf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.firstOrNull()
    val nVal = nArg?.value

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    // Parse n as a pattern
    val nPattern = when (nVal) {
        is StrudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: StrudelDslArg.of("1")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val staticN = nVal?.asIntOrNull()

    if (staticN != null) {
        // Static path
        if (staticN <= 1) return transform(source)

        // Construct a list of patterns: [source, source, ... (n-1 times), transform(source)]
        val patterns = ArrayList<Pair<Double, StrudelPattern>>(staticN)
        repeat(staticN - 1) {
            patterns.add(1.0 to source)
        }
        patterns.add(1.0 to transform(source))

        return ArrangementPattern(patterns)
    } else {
        // Dynamic path: Use LastOfWithControl
        return LastOfPattern(source, nPattern, transform)
    }
}

/**
 * Applies the given function every n cycles, starting from the last cycle.
 *
 * It essentially says: "Every n cycles, do this special thing on the last one."
 *
 * If you call:
 *
 * note("a b c d").lastOf(4, { it.rev() })
 *
 * then:
 * - Cycle 1: The pattern plays normally.
 * - Cycle 2: The pattern plays normally.
 * - Cycle 3: The pattern plays normally.
 * - Cycle 4: The pattern plays in reverse again (loop restarts).
 * - Cycle 5: The pattern plays normally.
 *
 * @param {n} - the number of cycles to repeat the function
 * @param {transform} - the function to apply to the first cycle
 */
@StrudelDsl
val lastOf by dslFunction { args, /* callInfo */ _ ->
    val n = args.getOrNull(0) ?: StrudelDslArg.of(1)

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    // TODO: parse mini
    val pat = args.getOrNull(2)?.value as? StrudelPattern ?: silence

    applyLastOf(pat, listOf(n, transform).asStrudelDslArgs())
}

@StrudelDsl
val StrudelPattern.lastOf by dslPatternExtension { source, args, /* callInfo */ _ ->
    applyLastOf(source, args)
}

@StrudelDsl
val String.lastOf by dslStringExtension { source, args, /* callInfo */ _ ->
    applyLastOf(source, args)
}

// -- when() -----------------------------------------------------------------------------------------------------------

/**
 * Conditionally applies a transformation based on a pattern.
 *
 * Samples the condition pattern at each event's midpoint. If truthy, applies the transformation;
 * otherwise, keeps the event unchanged.
 *
 * Equivalent to JavaScript: pat.when(condition, func)
 *
 * @param condition Pattern to test for truthiness
 * @param transform Function to apply when condition is true
 *
 * @example
 * note("c d e f").when(pure(1).struct("t ~ t ~")) { it.add(12) }
 * // Transforms notes on beats 1 and 3 (where struct is truthy)
 *
 * @example
 * s("bd sd").when(pure(1).slowcat(pure(1), pure(0))) { it.fast(2) }
 * // Doubles speed on alternating cycles
 */
@StrudelDsl
val StrudelPattern.`when` by dslPatternExtension { p, args, _ ->
    val arg0 = args.getOrNull(0) ?: return@dslPatternExtension p
    val arg1 = args.getOrNull(1) ?: return@dslPatternExtension p

    val condition = listOf(arg0).toPattern(voiceValueModifier)

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern = arg1.value as? (StrudelPattern) -> StrudelPattern
        ?: return@dslPatternExtension p

    // The "True" path: Apply transform, then keep only where condition is True
    val trueBranch = transform(p).mask(condition)

    // The "False" path: Keep original, but only where condition is False
    val falseBranch = p.mask(condition.not())

    // Combine them
    trueBranch.stack(falseBranch)
}

/** Direct function call support */
@StrudelDsl
fun StrudelPattern.`when`(
    condition: StrudelPattern,
    transform: (StrudelPattern) -> StrudelPattern,
): StrudelPattern {
    val trueBranch = transform(this).mask(condition)
    val falseBranch = this.mask(condition.not())
    return trueBranch.stack(falseBranch)
}

@StrudelDsl
val String.`when` by dslStringExtension { p, args, callInfo -> p.`when`(args, callInfo) }

/** Direct function call support */
@StrudelDsl
fun String.`when`(
    condition: StrudelPattern,
    transform: (StrudelPattern) -> StrudelPattern,
): StrudelPattern {
    return this.`when`(listOf(condition, transform).asStrudelDslArgs())
}
