package io.peekandpoke.klang.strudel

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Strudel pattern.
 */
interface StrudelPattern {
    @Serializable
    class Static(
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


    /**
     * Queries events from [from] and [to] cycles.
     */
    fun queryArc(from: Double, to: Double): List<StrudelPatternEvent>
}

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life strudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun StrudelPattern.makeStatic(from: Double, to: Double) = StrudelPattern.Static(
    events = queryArc(from, to)
)
