package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Pattern that shifts events in time based on a control value provider.
 *
 * Unlike using late/early with _bind, this pattern:
 * - Samples the control pattern at query time
 * - Shifts both part AND whole together
 * - Avoids the clipping issues that arise with _bind semantics
 *
 * @param source The source pattern to shift
 * @param offsetProvider Provides the time offset value
 * @param factor Multiplier for the offset (1.0 for late, -1.0 for early)
 */
internal class TimeShiftPattern(
    private val source: StrudelPattern,
    private val offsetProvider: ControlValueProvider,
    private val factor: Rational = Rational.ONE,
) : StrudelPattern {

    companion object {
        private val MIN_OVERLAP = 1e-7.toRational()
    }

    override val weight: Double get() = source.weight
    override val numSteps: Rational? get() = source.numSteps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        // 1. Query control events
        val controlEvents = offsetProvider.queryEvents(from, to, ctx)
        if (controlEvents.isEmpty()) return emptyList()

        // 2. Get min and max shift values
        var minShift = Rational.ZERO
        var maxShift = Rational.ZERO

        for (controlEvent in controlEvents) {
            val offsetDouble = controlEvent.data.value?.asDouble ?: continue
            val offset = offsetDouble.toRational() * factor
            if (offset < minShift) minShift = offset
            if (offset > maxShift) maxShift = offset
        }

        // 3. Query source with expanded range
        val adjustedFrom = from - maxShift
        val adjustedTo = to - minShift
        val sourceEvents = source.queryArcContextual(adjustedFrom, adjustedTo, ctx)

        // 4. Map each source event to the control event where it lands after shifting
        val result = mutableListOf<StrudelPatternEvent>()

        for (sourceEvent in sourceEvents) {
            var matchedEvent: StrudelPatternEvent? = null

            // Try each control event to see where this source event would land
            for (controlEvent in controlEvents) {
                val offsetDouble = controlEvent.data.value?.asDouble ?: continue
                val offset = offsetDouble.toRational() * factor

                // Calculate where this event would land with this offset
                val shiftedBegin = sourceEvent.part.begin + offset

                // Does the shifted event land in this control event's range?
                if (shiftedBegin >= controlEvent.part.begin && shiftedBegin < controlEvent.part.end) {
                    // Yes! Apply this offset
                    val shiftedPart = sourceEvent.part.shift(offset)
                    val shiftedWhole = sourceEvent.whole?.shift(offset) ?: shiftedPart

                    // Only include if shifted event overlaps query range (with epsilon tolerance)
                    val overlaps = hasOverlapWithEpsilon(
                        eventBegin = shiftedPart.begin,
                        eventEnd = shiftedPart.end,
                        queryFrom = from,
                        queryTo = to,
                        epsilon = MIN_OVERLAP,
                    )

                    if (overlaps) {
                        matchedEvent = sourceEvent.copy(
                            part = shiftedPart,
                            whole = shiftedWhole
                        )
                    }
                    break // Found the matching control event
                }
            }

            if (matchedEvent != null) {
                result.add(matchedEvent)
            }
        }

        return result
    }
}
