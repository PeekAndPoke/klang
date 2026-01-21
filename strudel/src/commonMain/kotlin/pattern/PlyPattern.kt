package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Repeats each event n times within its original timespan.
 *
 * For example, ply(3) on pattern "bd sn" produces "[bd bd bd] [sn sn sn]"
 * where each event is subdivided into n repetitions within its original duration.
 *
 * @param source The source pattern whose events will be repeated
 * @param n The number of times to repeat each event (must be >= 1)
 */
internal class PlyPattern(
    val source: StrudelPattern,
    val n: Int,
) : StrudelPattern {

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
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
