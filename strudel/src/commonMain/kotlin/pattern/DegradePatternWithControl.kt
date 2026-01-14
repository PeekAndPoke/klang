package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

internal class DegradePatternWithControl(
    val source: StrudelPattern,
    val probabilityPattern: StrudelPattern,
    val inverted: Boolean = false,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val epsilon = 1e-5.toRational()

        return source.queryArcContextual(from, to, ctx).filter { event ->
            // Sample probability at the start of the event
            val probEvents = probabilityPattern.queryArcContextual(event.begin, event.begin + epsilon, ctx)
            val probability = probEvents.firstOrNull()?.data?.value?.asDouble ?: 0.5

            val threshold = if (inverted) 1.0 - probability else probability
            val random = ctx.getSeededRandom(event.begin, event.end, "DegradePatternWithControl")

            if (inverted) {
                random.nextDouble() <= threshold
            } else {
                random.nextDouble() > threshold
            }
        }
    }
}
