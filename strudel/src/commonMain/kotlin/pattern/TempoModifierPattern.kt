package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

internal class TempoModifierPattern(
    val source: StrudelPattern,
    val factor: Rational,
    val invertPattern: Boolean = false,
) : StrudelPattern {
    companion object {
        private val epsilon = 1e-7.toRational()
    }

    private val scale = if (invertPattern) {
        factor
    } else {
        (Rational.ONE / maxOf(0.001.toRational(), factor))
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        val sourceDur = source.estimateCycleDuration()
        return sourceDur / scale
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val innerFrom = (from * scale) + epsilon
        val innerTo = (to * scale) - epsilon

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val mappedBegin = ev.begin / scale
            val mappedEnd = ev.end / scale
            val mappedDur = ev.dur / scale

            if (mappedEnd > from && mappedBegin < to) {
                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                    dur = mappedDur
                )
            } else {
                null
            }
        }
    }
}
