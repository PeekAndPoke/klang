package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Plays a list of (duration, pattern) segments sequentially, looping the total duration.
 */
internal class ArrangementPattern(
    val segments: List<Pair<Double, StrudelPattern>>,
) : StrudelPattern.Fixed {

    val totalDuration = segments.sumOf { it.first }

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        if (totalDuration == 0.0) return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()

        // 1. Determine which loops we cover
        // Usually from..to is small, but it might span the loop boundary.
        val loopStart = floor(from / totalDuration).toInt()
        val loopEnd = ceil(to / totalDuration).toInt()

        for (loopIndex in loopStart until loopEnd) {
            val loopOffset = loopIndex * totalDuration

            // Within one loop, iterate segments
            var segmentOffset = 0.0

            for ((dur, pat) in segments) {
                // The absolute time window for this segment in this loop iteration
                val segStart = loopOffset + segmentOffset
                val segEnd = segStart + dur

                // Check overlap
                if (segEnd > from && segStart < to) {
                    // We need to query the inner pattern.
                    // The inner pattern is usually infinite (0..1..2..).
                    // We want it to play starting from 0 relative to the segment start.
                    // So we map global time 't' to inner time 't - segStart'.

                    // Constrain query to the intersection of the request and the segment
                    val qStart = max(from, segStart)
                    val qEnd = min(to, segEnd)

                    val innerEvents = pat.queryArc(qStart - segStart, qEnd - segStart)

                    events.addAll(innerEvents.map { e ->
                        // Shift event back to global time
                        e.copy(
                            begin = e.begin + segStart,
                            end = e.end + segStart
                        )
                    })
                }

                segmentOffset += dur
            }
        }

        return events
    }
}
