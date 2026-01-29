package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Speeds up a pattern and also increases the speed parameter by the same factor.
 *
 * This is like fast() but also affects sample playback speed, resulting in pitch changes.
 * For example, hurry(2) will make the pattern play twice as fast AND make samples play
 * at double speed (higher pitch).
 *
 * For static values, applies uniform hurry across the entire pattern.
 * For control patterns, segments the time range and applies different factors to each segment.
 *
 * @param source The source pattern to speed up
 * @param factorProvider Control value provider for the factor
 */
internal class HurryPattern(
    val source: StrudelPattern,
    val factorProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a HurryPattern with a static factor value.
         */
        fun static(source: StrudelPattern, factor: Rational): HurryPattern {
            return HurryPattern(
                source = source,
                factorProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(factor.toDouble()))
            )
        }

        /**
         * Create a HurryPattern with a control pattern for the factor.
         */
        fun control(source: StrudelPattern, factorPattern: StrudelPattern): HurryPattern {
            return HurryPattern(
                source = source,
                factorProvider = ControlValueProvider.Pattern(factorPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational {
        // Use queried value if available, otherwise use 1.0 as estimate
        val factor = (factorProvider.query(from = Rational.ZERO, to = Rational.ONE, ctx = QueryContext.empty)?.asDouble
            ?: 1.0).toRational()

        val sourceDur = source.estimateCycleDuration()
        return sourceDur / factor
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val factorEvents = factorProvider.queryEvents(from, to, ctx)
        if (factorEvents.isEmpty()) return source.queryArcContextual(from, to, ctx)

        val result = createEventList()

        for (factorEvent in factorEvents) {
            val factor = (factorEvent.data.value?.asDouble ?: 1.0).toRational()
            val events = queryWithFactor(factorEvent.begin, factorEvent.end, ctx, factor)
            result.addAll(events)
        }

        return result
    }

    private fun queryWithFactor(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        factor: Rational,
    ): List<StrudelPatternEvent> {
        // If factor is 1.0, just return the source as-is
        if (factor == Rational.ONE) {
            return source.queryArcContextual(from, to, ctx)
        }

        val scale = factor
        val (innerFrom, innerTo) = scaleTimeRange(from, to, scale)

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val (mappedBegin, mappedEnd, mappedDur) = mapEventTimeByScale(ev, scale)

            if (hasOverlap(mappedBegin, mappedEnd, from, to)) {
                // Multiply the speed parameter by the factor
                val currentSpeed = ev.data.speed ?: 1.0
                val newSpeed = currentSpeed * factor.toDouble()

                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                    dur = mappedDur,
                    data = ev.data.copy(speed = newSpeed)
                )
            } else {
                null
            }
        }
    }
}
