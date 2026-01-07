package io.peekandpoke.klang.strudel.patterns

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

/**
 * Applies a control pattern to a source pattern.
 *
 * @param source The pattern defining the rhythm/structure (e.g. note("..."))
 * @param control The pattern defining the values (e.g. seq(0.5, 1.0))
 * @param mapper additional mapper from VoiceData to VoiceData
 * @param combiner Function to merge the source VoiceData with the control event's VoiceData
 */
internal class ControlPattern(
    val source: StrudelPattern,
    val control: StrudelPattern,
    val mapper: (VoiceData) -> VoiceData,
    val combiner: (VoiceData, VoiceData) -> VoiceData,
) : StrudelPattern {

    // Control patterns wrap a source pattern and should preserve its weight.
    // E.g. (bd@2).gain(0.5) should still have a weight of 2.
    override val weight: Double get() = source.weight

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArc(from, to)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            val queryTime = event.begin
            val controlEvents = control.queryArc(queryTime, queryTime + 0.00001)
            val match = controlEvents.firstOrNull()

            if (match != null) {
                // Apply the mapper to the control data BEFORE combining
                val mappedControlData = mapper(match.data)
                val newData = combiner(event.data, mappedControlData)
                result.add(event.copy(data = newData))
            } else {
                result.add(event)
            }
        }

        return result
    }
}
