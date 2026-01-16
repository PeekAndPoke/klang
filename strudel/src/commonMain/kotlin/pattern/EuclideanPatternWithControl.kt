package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Euclidean Pattern with control patterns for pulses, steps, and rotation.
 *
 * For each combination of overlapping control events, applies the Euclidean rhythm
 * with the corresponding parameter values.
 *
 * @param inner The pattern to apply the Euclidean rhythm to
 * @param pulsesPattern Control pattern for number of pulses
 * @param stepsPattern Control pattern for number of steps
 * @param rotationPattern Control pattern for rotation offset
 * @param legato If true, pulses are held until the next pulse (no gaps)
 */
internal class EuclideanPatternWithControl(
    val inner: StrudelPattern,
    val pulsesPattern: StrudelPattern,
    val stepsPattern: StrudelPattern,
    val rotationPattern: StrudelPattern?,
    val legato: Boolean = false,
) : StrudelPattern {
    override val weight: Double get() = inner.weight
    override val steps: Rational? get() = inner.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val pulsesEvents = pulsesPattern.queryArcContextual(from, to, ctx)
        val stepsEvents = stepsPattern.queryArcContextual(from, to, ctx)
        val rotationEvents = rotationPattern?.queryArcContextual(from, to, ctx) ?: listOf(
            StrudelPatternEvent(
                begin = from,
                end = to,
                dur = to - from,
                data = io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(value = 0.asVoiceValue())
            )
        )

        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty() || rotationEvents.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // Find all overlapping combinations of pulses, steps, and rotation events
        for (pulsesEvent in pulsesEvents) {
            for (stepsEvent in stepsEvents) {
                for (rotationEvent in rotationEvents) {
                    // Check if these three events overlap
                    val overlapBegin = maxOf(
                        pulsesEvent.begin,
                        stepsEvent.begin,
                        rotationEvent.begin
                    )
                    val overlapEnd = minOf(
                        pulsesEvent.end,
                        stepsEvent.end,
                        rotationEvent.end
                    )

                    if (overlapEnd <= overlapBegin) continue

                    val pulses = pulsesEvent.data.value?.asInt ?: 0
                    val steps = stepsEvent.data.value?.asInt ?: 0
                    val rotation = rotationEvent.data.value?.asInt ?: 0

                    // Apply the Euclidean pattern for this overlap timespan
                    val euclidPattern = if (legato) {
                        EuclideanPattern.createLegato(inner, pulses, steps, rotation)
                    } else {
                        EuclideanPattern.create(inner, pulses, steps, rotation)
                    }

                    val events = euclidPattern.queryArcContextual(overlapBegin, overlapEnd, ctx)
                    result.addAll(events)
                }
            }
        }

        return result
    }
}
