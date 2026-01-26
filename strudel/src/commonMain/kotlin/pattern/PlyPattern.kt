package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Repeats each event n times within its original timespan.
 *
 * For example, ply(3) on pattern "bd sn" produces "[bd bd bd] [sn sn sn]"
 * where each event is subdivided into n repetitions within its original duration.
 *
 * For static values, applies uniform repetition to all events.
 * For control patterns, samples the n value at each event's timespan.
 *
 * @param source The source pattern whose events will be repeated
 * @param nProvider Control value provider for the number of repetitions (must be >= 1)
 */
internal class PlyPattern(
    val source: StrudelPattern,
    val nProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a PlyPattern with a static n value.
         */
        fun static(source: StrudelPattern, n: Int): PlyPattern {
            return PlyPattern(
                source = source,
                nProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(n.toDouble()))
            )
        }

        /**
         * Create a PlyPattern with a control pattern for n.
         */
        fun control(source: StrudelPattern, nPattern: StrudelPattern): PlyPattern {
            return PlyPattern(
                source = source,
                nProvider = ControlValueProvider.Pattern(nPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val nEvents = nProvider.queryEvents(from, to, ctx)
        if (nEvents.isEmpty()) return source.queryArcContextual(from, to, ctx)

        // Fast-path when provider behaves like a static value over the full range
        if (nEvents.size == 1 && nEvents[0].begin == from && nEvents[0].end == to) {
            val n = nEvents[0].data.value?.asInt ?: 1
            return queryWithStaticN(from, to, ctx, n)
        }

        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            // Sample n based on overlapping control events
            val nValue = nEvents
                .firstOrNull { it.begin < event.end && it.end > event.begin }
                ?.data?.value?.asInt ?: 1

            if (nValue <= 1) {
                result.add(event)
                continue
            }

            val nRat = nValue.toRational()
            val eventDuration = event.end - event.begin
            val subDuration = eventDuration / nRat

            // Create n repetitions within the original event's timespan
            for (i in 0 until nValue) {
                val iRat = i.toRational()
                val newBegin = event.begin + (subDuration * iRat)
                val newEnd = newBegin + subDuration

                // Only include if it intersects with our query range
                if (newEnd > from && newBegin < to) {
                    result.add(
                        event.copy(
                            begin = newBegin,
                            end = newEnd,
                            dur = subDuration
                        )
                    )
                }
            }
        }

        return result
    }

    private fun queryWithStaticN(from: Rational, to: Rational, ctx: QueryContext, n: Int): List<StrudelPatternEvent> {
        // If n <= 1, no repetition needed
        if (n <= 1) {
            return source.queryArcContextual(from, to, ctx)
        }

        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        val nRat = n.toRational()

        for (event in sourceEvents) {
            val eventDuration = event.end - event.begin
            val subDuration = eventDuration / nRat

            // Create n repetitions within the original event's timespan
            for (i in 0 until n) {
                val iRat = i.toRational()
                val newBegin = event.begin + (subDuration * iRat)
                val newEnd = newBegin + subDuration

                // Only include if it intersects with our query range
                if (newEnd > from && newBegin < to) {
                    result.add(
                        event.copy(
                            begin = newBegin,
                            end = newEnd,
                            dur = subDuration
                        )
                    )
                }
            }
        }

        return result
    }
}
