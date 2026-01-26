package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Modifies tempo (speed) of a pattern using a control value provider.
 *
 * For static values, applies uniform tempo modification across the entire pattern.
 * For control patterns, segments the time range and applies different tempo to each segment.
 *
 * @param source The pattern to modify
 * @param factorProvider Control value provider for the tempo factor
 * @param invertPattern If true, uses fast mode (1/factor); if false, uses slow mode (factor)
 */
internal class TempoModifierPattern(
    val source: StrudelPattern,
    val factorProvider: ControlValueProvider,
    val invertPattern: Boolean = false,
) : StrudelPattern {
    companion object {
        private val epsilon = 1e-7.toRational()

        /**
         * Create a TempoModifierPattern with a static factor value.
         */
        fun static(
            source: StrudelPattern,
            factor: Rational,
            invertPattern: Boolean = false,
        ): TempoModifierPattern {
            return TempoModifierPattern(
                source = source,
                factorProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(factor.toDouble())),
                invertPattern = invertPattern
            )
        }

        /**
         * Create a TempoModifierPattern with a control pattern for the factor.
         */
        fun control(
            source: StrudelPattern,
            factorPattern: StrudelPattern,
            invertPattern: Boolean = false,
        ): TempoModifierPattern {
            return TempoModifierPattern(
                source = source,
                factorProvider = ControlValueProvider.Pattern(factorPattern),
                invertPattern = invertPattern
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val factorEvents = factorProvider.queryEvents(from, to, ctx)
        if (factorEvents.isEmpty()) return emptyList()

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
        // invertPattern=true means fast (use factor as-is)
        // invertPattern=false means slow (use 1/factor)
        val scale = if (invertPattern) {
            factor
        } else {
            Rational.ONE / maxOf(0.001.toRational(), factor)
        }

        val (innerFrom, innerTo) = scaleTimeRangeWithEpsilon(from, to, scale, epsilon)

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val (mappedBegin, mappedEnd, mappedDur) = mapEventTimeByScale(ev, scale)

            if (hasOverlap(mappedBegin, mappedEnd, from, to)) {
                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                    dur = mappedDur
                )
            } else {
                null
            }
        }
    }

    override fun estimateCycleDuration(): Rational {
        // Use static value if available, otherwise use 1.0 as estimate
        val factor = if (factorProvider is ControlValueProvider.Static) {
            (factorProvider.value.asDouble ?: 1.0).toRational()
        } else {
            Rational.ONE
        }

        val scale = if (invertPattern) {
            factor
        } else {
            Rational.ONE / maxOf(0.001.toRational(), factor)
        }

        val sourceDur = source.estimateCycleDuration()
        return sourceDur / scale
    }
}
