package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * A pattern that modifies the query context before querying the source pattern.
 */
internal class ContextModifierPattern(
    /** The wrapped pattern. */
    val source: SprudelPattern,
    /** The context modifier function. */
    val modifier: QueryContext.Updater.() -> Unit,
) : SprudelPattern {
    companion object {
        fun SprudelPattern.withContext(modifier: QueryContext.Updater.() -> Unit) =
            ContextModifierPattern(this, modifier)
    }

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val updated = ctx.update(modifier)

        return source.queryArcContextual(from, to, updated)
    }
}
