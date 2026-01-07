package io.peekandpoke.klang.strudel.patterns

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

internal class TimeModifierPattern(
    val pattern: StrudelPattern,
    val factor: Double,
) : StrudelPattern {
    override fun queryArc(
        from: Double,
        to: Double,
    ): List<StrudelPatternEvent> {
        // Map outer time to inner time
        // If slow(2), outer 0..2 becomes inner 0..1
        val innerFrom = from / factor
        val innerTo = to / factor

        val innerEvents = pattern.queryArc(innerFrom, innerTo)

        return innerEvents.map { ev ->
            // Map inner events back to outer time
            ev.copy(
                begin = ev.begin * factor,
                end = ev.end * factor,
                dur = ev.dur * factor
            )
        }
    }
}
