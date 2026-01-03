package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

/**
 * Applies a control pattern to a source pattern.
 *
 * @param source The pattern defining the rhythm/structure (e.g. note("..."))
 * @param control The pattern defining the values (e.g. seq(0.5, 1.0))
 * @param combiner Function to merge the source VoiceData with the control event's VoiceData
 */
internal class ControlPattern(
    val source: StrudelPattern,
    val control: StrudelPattern,
    val combiner: (VoiceData, VoiceData) -> VoiceData,
) : StrudelPattern {

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArc(from, to)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            // Strategy: Sample control pattern at the event's ONSET (or midpoint).
            // Strudel/Tidal standard for |> is onset.

            // To ensure we catch the control value starting exactly at event.begin,
            // we query a tiny epsilon interval.
            val queryTime = event.begin

            // We need to find the control event that is "active" at queryTime.
            // queryArc returns events overlapping the interval.
            val controlEvents = control.queryArc(queryTime, queryTime + 0.000001)

            // Pick the best match.
            // Usually the one that started at or before queryTime.
            // If multiple, usually the latest one (layering) or first one.
            val match = controlEvents.firstOrNull()

            if (match != null) {
                val newData = combiner(event.data, match.data)
                result.add(event.copy(data = newData))
            } else {
                // If no control value, keep original (or drop if you strictly mask)
                result.add(event)
            }
        }

        return result
    }
}
