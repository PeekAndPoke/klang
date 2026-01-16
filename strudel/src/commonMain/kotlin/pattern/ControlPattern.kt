package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

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

    companion object {
        private val EPS = 1e-5.toRational()
    }

    // Control patterns wrap a source pattern and should preserve its weight.
    // E.g. (bd@2).gain(0.5) should still have a weight of 2.
    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            val controlEvents: List<StrudelPatternEvent> =
                control.queryArcContextual(from = event.begin, to = event.begin + EPS, ctx = ctx)

            val match: StrudelPatternEvent? = controlEvents.firstOrNull()

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
