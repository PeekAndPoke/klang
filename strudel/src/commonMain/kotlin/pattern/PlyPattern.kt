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
        // For static values, we can optimize
        if (nProvider is ControlValueProvider.Static) {
            val n = nProvider.value.asInt ?: 1
            return queryWithStaticN(from, to, ctx, n)
        }

        // For control patterns, use event-driven approach
        val controlPattern = (nProvider as? ControlValueProvider.Pattern)?.pattern
            ?: return queryWithStaticN(from, to, ctx, 1)

        return queryWithControlPattern(from, to, ctx, controlPattern)
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

    private fun queryWithControlPattern(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        nPattern: StrudelPattern,
    ): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            // Query the control pattern at this event's timespan
            val controlEvents = nPattern.queryArcContextual(event.begin, event.end, ctx)

            if (controlEvents.isEmpty()) {
                // No control value, keep original event
                result.add(event)
                continue
            }

            // Get the n value from the first control event
            val nValue = controlEvents.first().data.value?.asInt ?: 1

            if (nValue <= 1) {
                // No repetition needed
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
                val newDur = subDuration

                // Only include if it intersects with our query range
                if (newEnd > from && newBegin < to) {
                    result.add(
                        event.copy(
                            begin = newBegin,
                            end = newEnd,
                            dur = newDur
                        )
                    )
                }
            }
        }

        return result
    }
}
