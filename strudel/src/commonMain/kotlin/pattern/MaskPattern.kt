package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Filters the [source] pattern using the [mask] pattern.
 *
 * This implementation queries all events from the [source] first.
 * For each source event, it samples the [mask] for that event's duration.
 * If any part of the mask is truthy during that time, the source event is allowed through.
 */
internal class MaskPattern(
    val source: StrudelPattern,
    val mask: StrudelPattern,
) : StrudelPattern {

    // Mask patterns preserve the source's weight as they filter existing events
    override val weight: Double get() = source.weight

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        // 1. Get all events from the source
        val sourceEvents = source.queryArc(from, to)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (sourceEvent in sourceEvents) {
            // 2. Query the mask for the specific duration of this source event
            val maskEvents = mask.queryArc(sourceEvent.begin, sourceEvent.end)

            // 3. Check if any mask event in this duration is truthy
            val isAllowed = maskEvents.any { maskEvent ->
                val data = maskEvent.data
                val note = data.note ?: ""
                val value = data.value ?: 0.0

                (note.isNotEmpty() && note != "~" && note != "0") || (value > 0.0)
            }

            if (isAllowed) {
                result.add(sourceEvent)
            }
        }

        return result
    }
}
