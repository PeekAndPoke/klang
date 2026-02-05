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

    override val weight: Double get() = source.weight
    override val numSteps: Rational? get() = source.numSteps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        // 1. Query control events
        val controlEvents = offsetProvider
            .queryEvents(from - Rational.ONE, to + Rational.ONE, ctx)

        if (controlEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (controlEvent in controlEvents) {
            val offsetDouble = controlEvent.data.value?.asDouble ?: continue
            val offset = offsetDouble.toRational() * factor

            // Determine the time window where this control event (offset) applies
            // We intersect the control event's part with the requested query range
            val outStart = maxOf(from, controlEvent.part.begin)
            val outEnd = minOf(to, controlEvent.part.end)

            if (outStart >= outEnd) continue

            // Determine the corresponding window in the source pattern
            // If output time is t, source time is t - offset
            val srcStart = outStart - offset
            val srcEnd = outEnd - offset

            val shifted = source
                .queryArcContextual(srcStart, srcEnd, ctx)
                .mapNotNull { ev ->
                    val shiftPart = ev.part.shift(offset)
                    val shiftWhole = ev.whole.shift(offset)

                    // Clip the shifted event to the time window where this offset is valid
                    // (i.e. the control event's duration)
                    val clippedPart = shiftPart.clipTo(controlEvent.part) ?: return@mapNotNull null

                    ev.copy(part = clippedPart, whole = shiftWhole)
                }

            result.addAll(shifted)
        }

        return result.filter {
            hasOverlap(it.part.begin, it.part.end, from, to)
        }
    }
}
