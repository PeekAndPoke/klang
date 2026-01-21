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
        // For static values, we can optimize with a uniform shift
        if (offsetProvider is ControlValueProvider.Static) {
            val offset = (offsetProvider.value.asDouble ?: 0.0).toRational()
            return queryWithStaticOffset(from, to, ctx, offset)
        }

        // For control patterns, use per-event sampling
        val controlPattern = (offsetProvider as? ControlValueProvider.Pattern)?.pattern
            ?: return queryWithStaticOffset(from, to, ctx, Rational.ZERO)

        return queryWithControlPattern(from, to, ctx, controlPattern)
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

    private fun queryWithControlPattern(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        offsetPattern: StrudelPattern,
    ): List<StrudelPatternEvent> {
        // We need to query a wider range from the source to account for potential shifts
        // Use a heuristic: query +/- 10 cycles extra to be safe against larger control values
        val margin = Rational(10)
        val extendedFrom = from - margin
        val extendedTo = to + margin

        val sourceEvents = source.queryArcContextual(extendedFrom, extendedTo, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val fromPlusEps = from + epsilon
        val toMinusEps = to - epsilon

        return sourceEvents.mapNotNull { event ->
            val queryTime = event.begin

            // Sample the offset pattern at the event's begin time
            val offsetEvents = offsetPattern.queryArcContextual(queryTime, queryTime + epsilon, ctx)
            val offsetValue = offsetEvents.firstOrNull()?.data?.value?.asDouble ?: 0.0
            val offset = offsetValue.toRational()

            // Shift the event by the sampled offset
            val mappedBegin = event.begin + offset
            val mappedEnd = event.end + offset

            // Only include events that overlap with the requested range
            if (mappedEnd > fromPlusEps && mappedBegin < toMinusEps) {
                event.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                )
            } else {
                null
            }
        }
    }
}
