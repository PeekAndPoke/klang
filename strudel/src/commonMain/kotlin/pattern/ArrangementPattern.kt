package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Plays a list of (duration, pattern) segments sequentially, looping the total duration.
 */
internal class ArrangementPattern(
    val segments: List<Pair<Double, StrudelPattern>>,
) : StrudelPattern.Fixed {

    private val segmentsRational = segments.map { (dur, pat) -> Rational(dur) to pat }
    private val totalDuration = segmentsRational.fold(Rational.ZERO) { acc, (dur, _) -> acc + dur }

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        if (totalDuration == Rational.ZERO) return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()

        // 1. Determine which loops we cover
        // Usually from..to is small, but it might span the loop boundary.
        val loopStart = (from / totalDuration).floor().toInt()
        val loopEnd = (to / totalDuration).ceil().toInt()

        for (loopIndex in loopStart until loopEnd) {
            val loopOffset = Rational(loopIndex) * totalDuration

            // Within one loop, iterate segments
            var segmentOffset = Rational.ZERO

            for ((dur, pat) in segmentsRational) {
                // The absolute time window for this segment in this loop iteration
                val segStart = loopOffset + segmentOffset
                val segEnd = segStart + dur

                // Check overlap
                if (segEnd >= from && segStart < to) {
                    // We need to query the inner pattern.
                    // The inner pattern is usually infinite (0..1..2..).
                    // We want it to play starting from 0 relative to the segment start.
                    // So we map global time 't' to inner time 't - segStart'.

                    // Constrain query to the intersection of the request and the segment
                    val qStart = maxOf(from, segStart)
                    val qEnd = minOf(to, segEnd)

                    val innerQEnd = minOf(qEnd - segStart, dur)
                    val innerQStart = maxOf(qStart - segStart, Rational.ZERO)

                    if (innerQEnd > innerQStart) {
                        val innerEvents = pat.queryArc(innerQStart, innerQEnd)

                        events.addAll(innerEvents.map { e ->
                            // Shift event back to global time
                            e.copy(
                                begin = e.begin + segStart,
                                end = e.end + segStart
                            )
                        })
                    }
                }

                segmentOffset += dur
            }
        }

        return events
    }
}
