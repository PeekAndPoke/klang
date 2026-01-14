// strudel/src/commonMain/kotlin/lang/lang_conditional.kt
package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ArrangementPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

private fun applyFirstOf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val n = args.firstOrNull()?.asIntOrNull() ?: 1

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1) as? (StrudelPattern) -> StrudelPattern ?: { it }

    if (n <= 1) return transform(source)

    // Construct a list of patterns: [transform(source), source, source, ... (n-1 times)]
    // We use 1.0 duration for each pattern segment, so they play sequentially, one per cycle.
    val patterns = ArrayList<Pair<Double, StrudelPattern>>(n)
    patterns.add(1.0 to transform(source))
    repeat(n - 1) {
        patterns.add(1.0 to source)
    }

    return ArrangementPattern(patterns)
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
 * @param n - the number of cycles to repeat the function
 * @param transform - the function to apply to the first cycle
 */
@StrudelDsl
val firstOf by dslFunction { _ -> silence }

@StrudelDsl
val StrudelPattern.firstOf by dslPatternExtension { source, args -> applyFirstOf(source, args) }

@StrudelDsl
val String.firstOf by dslStringExtension { source, args -> applyFirstOf(source, args) }

// -- every() ----------------------------------------------------------------------------------------------------------

/** Alias for [firstOf] */
@StrudelDsl
val every by dslFunction { _ -> silence }

/** Alias for [firstOf] */
@StrudelDsl
val StrudelPattern.every by dslPatternExtension { source, args -> applyFirstOf(source, args) }

/** Alias for [firstOf] */
@StrudelDsl
val String.every by dslStringExtension { source, args -> applyFirstOf(source, args) }
