package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

internal class TempoModifierPattern(
    val source: StrudelPattern,
    val factor: Double,
) : StrudelPattern {
    // Time modifiers transform the inner pattern's time but should preserve its structural weight.
    override val weight: Double get() = source.weight

    private val factorRational = Rational(factor)

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // Map outer time to inner time
        // If slow(2), outer 0..2 becomes inner 0..1
        val innerFrom = from / factorRational
        val innerTo = to / factorRational

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.map { ev ->
            // Map inner events back to outer time
            ev.copy(
                begin = ev.begin * factorRational,
                end = ev.end * factorRational,
                dur = ev.dur * factorRational
            )
        }
    }
}
