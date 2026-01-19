// strudel/src/commonMain/kotlin/lang/lang_conditional.kt
@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.ArrangementPattern
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.FirstOfWithControlPattern
import io.peekandpoke.klang.strudel.pattern.LastOfWithControlPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

private fun applyFirstOf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val nArg = args.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1) as? (StrudelPattern) -> StrudelPattern ?: { it }

    // Parse n as a pattern
    val nPattern = when (nArg) {
        is StrudelPattern -> nArg
        null -> parseMiniNotation("1") { text, _ -> AtomicPattern(VoiceData.empty.defaultModifier(text)) }
        else -> parseMiniNotation(nArg.toString()) { text, _ -> AtomicPattern(VoiceData.empty.defaultModifier(text)) }
    }

    val staticN = nArg?.asIntOrNull()

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
        return FirstOfWithControlPattern(source, nPattern, transform)
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

// -- lastOf() ---------------------------------------------------------------------------------------------------------

private fun applyLastOf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    val nArg = args.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1) as? (StrudelPattern) -> StrudelPattern ?: { it }

    // Parse n as a pattern
    val nPattern = when (nArg) {
        is StrudelPattern -> nArg

        null -> parseMiniNotation("1") { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }

        else -> parseMiniNotation(nArg.toString()) { text, _ ->
            AtomicPattern(VoiceData.empty.defaultModifier(text))
        }
    }

    val staticN = nArg?.asIntOrNull()

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
        return LastOfWithControlPattern(source, nPattern, transform)
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
val lastOf by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 1

    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1) as? (StrudelPattern) -> StrudelPattern ?: { it }
    val pat = args.getOrNull(2) as? StrudelPattern ?: silence

    applyLastOf(pat, listOf(n, transform))
}

@StrudelDsl
val StrudelPattern.lastOf by dslPatternExtension { source, args ->
    applyLastOf(source, args)
}

@StrudelDsl
val String.lastOf by dslStringExtension { source, args ->
    applyLastOf(source, args)
}
