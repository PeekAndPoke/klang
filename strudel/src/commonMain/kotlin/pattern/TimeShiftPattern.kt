package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Shifts a pattern in time by a given offset.
 * Positive offset shifts the pattern later (to the right).
 * Negative offset shifts the pattern earlier (to the left).
 *
 * For static values, applies uniform time shift across the entire pattern.
 * For control patterns, samples the offset at each event's begin time for per-event shifting.
 *
 * @param source The source pattern to shift
 * @param offsetProvider Control value provider for the offset
 */
internal class TimeShiftPattern(
    val source: StrudelPattern,
    val offsetProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        private val epsilon = 1e-7.toRational()

        /**
         * Create a TimeShiftPattern with a static offset value.
         */
        fun static(source: StrudelPattern, offset: Rational): TimeShiftPattern {
            return TimeShiftPattern(
                source = source,
                offsetProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(offset.toDouble()))
            )
        }

        /**
         * Create a TimeShiftPattern with a control pattern for the offset.
         */
        fun control(source: StrudelPattern, offsetPattern: StrudelPattern): TimeShiftPattern {
            return TimeShiftPattern(
                source = source,
                offsetProvider = ControlValueProvider.Pattern(offsetPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val offsetEvents = offsetProvider.queryEvents(from, to, ctx)
        if (offsetEvents.isEmpty()) return queryWithStaticOffset(from, to, ctx, Rational.ZERO)

        val result = mutableListOf<StrudelPatternEvent>()

        for (offsetEvent in offsetEvents) {
            val offset = (offsetEvent.data.value?.asDouble ?: 0.0).toRational()
            val events = queryWithStaticOffset(offsetEvent.begin, offsetEvent.end, ctx, offset)
            result.addAll(events)
        }

        return result
    }

    private fun queryWithStaticOffset(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        offset: Rational,
    ): List<StrudelPatternEvent> {
        // To shift the pattern by 'offset', we query the source at (from - offset, to - offset)
        // and then shift all resulting events forward by 'offset'
        val innerFrom = from - offset
        val innerTo = to - offset

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        val fromPlusEps = from + epsilon
        val toMinusEps = to - epsilon

        return innerEvents.mapNotNull { ev ->
            val mappedBegin = ev.begin + offset
            val mappedEnd = ev.end + offset

            // Only include events that overlap with the requested range
            if (mappedEnd > fromPlusEps && mappedBegin <= toMinusEps) {
                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                )
            } else {
                null
            }
        }
    }
}
