package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Superimpose Pattern: Layers a modified version of the pattern on top of itself.
 * Implementation of `superimpose(fn)`.
 */
internal class SuperimposePattern(
    val source: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val originalEvents = source.queryArcContextual(from, to, ctx)

        val superimposedEvents = try {
            transform(source).queryArcContextual(from, to, ctx)
        } catch (e: Exception) {
            println("Failed to superimpose pattern: ${e.stackTraceToString()}")
            emptyList()
        }

        // Combine and return all events
        return (originalEvents + superimposedEvents)
    }
}
