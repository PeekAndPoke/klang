package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Speeds up a pattern like fast, but plays it only once per cycle in the compressed time, leaving a gap.
 *
 * For example, fastGap(2) compresses the pattern into the first half of each cycle,
 * leaving the second half silent.
 *
 * This is equivalent to compress(0, 1/factor).
 *
 * For static values, applies uniform fastGap across the entire pattern.
 * For control patterns, segments the time range and applies different factors to each segment.
 *
 * @param source The source pattern to compress
 * @param factorProvider Control value provider for the factor
 */
internal class FastGapPattern(
    val source: StrudelPattern,
    val factorProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a FastGapPattern with a static factor value.
         */
        fun static(source: StrudelPattern, factor: Rational): FastGapPattern {
            return FastGapPattern(
                source = source,
                factorProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(factor.toDouble()))
            )
        }

    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
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
        // Handle edge case where factor <= 0
        if (factor.toDouble() <= 0.0) {
            return emptyList()
        }

        // If factor is 1.0, just return the source as-is
        if (factor == Rational.ONE) {
            return source.queryArcContextual(from, to, ctx)
        }

        val span = Rational.ONE / factor
        val result = createEventList()

        // Determine which cycles we need to query
        for (cycle in calculateCycleBounds(from, to)) {
            val cycleRat = cycle.toRational()

            // The compressed region in this cycle: [cycle, cycle + 1/factor)
            val compressedStart = cycleRat
            val compressedEnd = cycleRat + span

            // Check if our query range intersects with the compressed region
            if (!cycleRegionIntersects(from, to, compressedStart, compressedEnd)) {
                continue // No intersection
            }

            // Query the source pattern for the full cycle (0 to 1)
            val sourceEvents = source.queryArcContextual(cycleRat, cycleRat + Rational.ONE, ctx)

            // Map each event from source cycle [0,1) to compressed region [start, start+span)
            for (ev in sourceEvents) {
                val (mappedBegin, mappedEnd, mappedDur) = mapEventTimeBySpan(ev, cycleRat, compressedStart, span)

                // Only include if it intersects with our query range
                if (hasOverlap(mappedBegin, mappedEnd, from, to)) {
                    result.add(
                        ev.copy(
                            begin = mappedBegin,
                            end = mappedEnd,
                            dur = mappedDur
                        )
                    )
                }
            }
        }

        return result
    }
}
