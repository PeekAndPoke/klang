package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

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
    val source: SprudelPattern,
    val factorProvider: ControlValueProvider,
    val invertPattern: Boolean = false,
) : SprudelPattern {
    companion object {
        /**
         * Create a TempoModifierPattern with a static factor value.
         */
        fun static(
            source: SprudelPattern,
            factor: Rational,
            invertPattern: Boolean = false,
        ): TempoModifierPattern {
            return TempoModifierPattern(
                source = source,
                factorProvider = ControlValueProvider.Static(factor.asVoiceValue()),
                invertPattern = invertPattern
            )
        }

        /**
         * Create a TempoModifierPattern with a control pattern for the factor.
         */
        fun control(
            source: SprudelPattern,
            factorPattern: SprudelPattern,
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

    override val numSteps: Rational? get() = source.numSteps

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val factorEvents = factorProvider.queryEvents(from, to, ctx)
        if (factorEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (factorEvent in factorEvents) {
            // Read as Rational directly — avoids a Rational -> Double -> Rational round-trip
            // that re-runs doubleToFractionBigInt per query (see TimeShiftPattern for details).
            val factor = factorEvent.data.value?.asRational ?: Rational.ONE
            val events = queryWithFactor(factorEvent.part.begin, factorEvent.part.end, ctx, factor)

            val factorLocation = factorEvent.sourceLocations?.innermost
            if (factorLocation != null) {
                result.addAll(events.map { it.prependLocation(factorLocation) })
            } else {
                result.addAll(events)
            }
        }

        return result
    }

    private fun queryWithFactor(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
        factor: Rational,
    ): List<SprudelPatternEvent> {
        // invertPattern=true means fast (use factor as-is)
        // invertPattern=false means slow (use 1/factor)
        val scale = if (invertPattern) {
            factor.toDouble()
        } else {
            1.0 / maxOf(0.001, factor.toDouble())
        }

        val (innerFrom, innerToRaw) = scaleTimeRange(from, to, scale)
        // Preserve a non-empty window if scaling collapsed it (e.g. point-sampling through slow()).
        val innerTo = if (to > from && innerToRaw <= innerFrom) innerFrom + CycleTime(1.0) else innerToRaw

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val (scaledPart, scaledWhole) = mapEventTimeByScale(ev, scale)

            if (hasOverlap(scaledPart.begin, scaledPart.end, from, to)) {
                ev.copy(part = scaledPart, whole = scaledWhole)
            } else {
                null
            }
        }
    }

    override fun estimateCycleDuration(): Rational {
        // Use static value if available, otherwise use 1.0 as estimate
        val factor = if (factorProvider is ControlValueProvider.Static) {
            factorProvider.value.asRational ?: Rational.ONE
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
