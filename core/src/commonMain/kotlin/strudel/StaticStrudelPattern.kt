package io.peekandpoke.klang.strudel

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
class StaticStrudelPattern(
    val events: List<StrudelPatternEvent>,
) : StrudelPattern {

    private val maxEnd = max(1.0, events.maxOfOrNull { it.end } ?: 0.0)

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        if (events.isEmpty()) return emptyList()

        return events.filter { evt ->
            val evtBegin = evt.begin % maxEnd
            val evtEnd = evt.end % maxEnd

            evtBegin >= from && evtBegin < to || evtEnd > from && evtEnd <= to
        }
    }
}
