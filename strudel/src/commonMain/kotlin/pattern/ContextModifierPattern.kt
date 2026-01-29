package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * A pattern that modifies the query context before querying the source pattern.
 */
internal class ContextModifierPattern(
    /** The wrapped pattern. */
    val source: StrudelPattern,
    /** The context modifier function. */
    val modifier: QueryContext.Updater.() -> Unit,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.withContext(modifier: QueryContext.Updater.() -> Unit) =
            ContextModifierPattern(this, modifier)
    }

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val updated = ctx.update(modifier)

        return source.queryArcContextual(from, to, updated)
    }
}
