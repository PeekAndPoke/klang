package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan
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
                nProvider = ControlValueProvider.Static(Rational(n).asVoiceValue())
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

            val duration = nEvent.part.duration
            val sliceDuration = duration / Rational(n)

            // Create n slices within this timespan
            for (i in 0 until n) {
                val sliceBegin = nEvent.part.begin + (sliceDuration * Rational(i))
                val sliceEnd = sliceBegin + sliceDuration

                // Query source for this slice
                val sourceEvents = source.queryArcContextual(sliceBegin, sliceEnd, ctx)

                for (sourceEvent in sourceEvents) {
                    // Clip source event to slice boundaries
                    val sliceSpan = TimeSpan(sliceBegin, sliceEnd)
                    val clippedPart = sourceEvent.part.clipTo(sliceSpan)

                    if (clippedPart != null) {
                        result.add(
                            sourceEvent.copy(
                                part = clippedPart
                                // Preserve whole unchanged
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
