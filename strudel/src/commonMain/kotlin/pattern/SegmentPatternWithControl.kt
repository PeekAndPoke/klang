package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Segments a pattern based on a control pattern that determines the number of segments per timespan.
 *
 * For each event in the control pattern, divides that timespan into n equal slices
 * and samples the source pattern at each slice.
 *
 * @param source The pattern to segment
 * @param nPattern The control pattern providing the number of segments
 */
internal class SegmentPatternWithControl(
    val source: StrudelPattern,
    val nPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1
            if (n <= 0) continue

            val duration = nEvent.end - nEvent.begin
            val sliceDuration = duration / Rational(n)

            // Create n slices within this timespan
            for (i in 0 until n) {
                val sliceBegin = nEvent.begin + (sliceDuration * Rational(i))
                val sliceEnd = sliceBegin + sliceDuration

                // Query source for this slice
                val sourceEvents = source.queryArcContextual(sliceBegin, sliceEnd, ctx)

                for (sourceEvent in sourceEvents) {
                    // Clip source event to slice boundaries
                    val clippedBegin = maxOf(sliceBegin, sourceEvent.begin)
                    val clippedEnd = minOf(sliceEnd, sourceEvent.end)

                    if (clippedEnd > clippedBegin) {
                        result.add(
                            sourceEvent.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedEnd - clippedBegin
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
