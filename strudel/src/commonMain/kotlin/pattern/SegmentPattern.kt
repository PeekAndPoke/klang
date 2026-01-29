package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Segments a pattern based on a control pattern that determines the number of segments per timespan.
 *
 * For each event in the control pattern, divides that timespan into n equal slices
 * and samples the source pattern at each slice.
 *
 * For static values, this is only used with control patterns since static segmentation
 * is handled via struct(x.fast(n)) in the lang layer.
 *
 * @param source The pattern to segment
 * @param nProvider Control value provider for the number of segments
 */
internal class SegmentPattern(
    val source: StrudelPattern,
    val nProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a SegmentPattern with a static n value.
         * Note: This is rarely used directly; static segmentation is usually done via struct(x.fast(n)).
         */
        fun static(source: StrudelPattern, n: Int): SegmentPattern {
            return SegmentPattern(
                source = source,
                nProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(n.toDouble()))
            )
        }

        /**
         * Create a SegmentPattern with a control pattern for n.
         */
        fun control(source: StrudelPattern, nPattern: StrudelPattern): SegmentPattern {
            return SegmentPattern(
                source = source,
                nProvider = ControlValueProvider.Pattern(nPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val nEvents = nProvider.queryEvents(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()

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
