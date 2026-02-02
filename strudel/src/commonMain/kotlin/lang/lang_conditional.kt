// strudel/src/commonMain/kotlin/lang/lang_conditional.kt
@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel._innerJoin
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

fun applyFirstOf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<StrudelPattern>(n)
            patterns.add(transform(src))
            repeat(n - 1) { patterns.add(src) }
            applySlowcatPrime(patterns)
        }
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
    val pat = args.getOrNull(2)?.toPattern() ?: silence

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
    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern =
        args.getOrNull(1)?.value as? (StrudelPattern) -> StrudelPattern ?: { it }

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<StrudelPattern>(n)
            repeat(n - 1) { patterns.add(src) }
            patterns.add(transform(src))
            applySlowcatPrime(patterns)
        }
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
 * @param {condition} Pattern to test for truthiness
 * @param {transform} Function to apply when condition is true
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

    val condition = listOf(arg0).toPattern()

    @Suppress("UNCHECKED_CAST")
    val transform: (StrudelPattern) -> StrudelPattern = arg1.value as? (StrudelPattern) -> StrudelPattern
        ?: return@dslPatternExtension p

    // FLIPPED IMPLEMENTATION: Create a pattern that iterates over source events
    // and checks the condition for each one
    object : StrudelPattern {
        override val weight: Double get() = p.weight
        override val numSteps: io.peekandpoke.klang.strudel.math.Rational? get() = p.numSteps

        private val transformed = transform(p)

        override fun queryArcContextual(
            from: io.peekandpoke.klang.strudel.math.Rational,
            to: io.peekandpoke.klang.strudel.math.Rational,
            ctx: StrudelPattern.QueryContext,
        ): List<StrudelPatternEvent> {
            // Query source pattern
            val sourceEvents = p.queryArcContextual(from, to, ctx)

            val result = mutableListOf<StrudelPatternEvent>()

            for (sourceEvent in sourceEvents) {
                // Query condition at this source event's time (sample at midpoint)
                val conditionEvents = condition.queryArcContextual(
                    from = sourceEvent.part.begin,
                    to = sourceEvent.part.begin + 0.00001.toRational(),
                    ctx = ctx,
                )

                // Check if condition is truthy
                val isTrue = conditionEvents.firstOrNull()?.data?.isTruthy() ?: false

                if (isTrue) {
                    val transformedEvents = transformed
                        .queryArcContextual(from = sourceEvent.part.begin, to = sourceEvent.part.end, ctx = ctx)

                    result.addAll(transformedEvents)
                } else {
                    // Keep source event unchanged
                    result.add(sourceEvent)
                }
            }

            return result
        }

        override fun estimateCycleDuration(): io.peekandpoke.klang.strudel.math.Rational {
            return p.estimateCycleDuration()
        }
    }
}

/** Direct function call support */
@StrudelDsl
fun StrudelPattern.`when`(
    condition: StrudelPattern,
    transform: (StrudelPattern) -> StrudelPattern,
): StrudelPattern {
    return this.`when`(listOf(condition, transform).asStrudelDslArgs())
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
