package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.math.max

internal class TempoModifierPattern(
    val source: StrudelPattern,
    val factor: Double,
    val invertPattern: Boolean = false,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val scale = if (invertPattern) factor else (1.0 / max(0.001, factor))
        val scaleRat = Rational(scale)

        val innerFrom = from * scaleRat
        val innerTo = to * scaleRat

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val mappedBegin = ev.begin / scaleRat
            val mappedEnd = ev.end / scaleRat
            val mappedDur = ev.dur / scaleRat

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
