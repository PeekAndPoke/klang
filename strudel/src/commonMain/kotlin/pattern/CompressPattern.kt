package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Compresses a pattern into a specific timespan within each cycle.
 *
 * For example, compress(0.25, 0.75) will compress the pattern to play only
 * in the middle half of each cycle, leaving the first and last quarters silent.
 *
 * @param source The source pattern to compress
 * @param start The start of the compressed timespan (0.0 to 1.0)
 * @param end The end of the compressed timespan (0.0 to 1.0)
 */
internal class CompressPattern(
    val source: StrudelPattern,
    val start: Rational,
    val end: Rational,
) : StrudelPattern {

    private val span = end - start

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // Handle edge case where span is zero or negative
        if (span <= Rational.ZERO) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // Determine which cycles we need to query
        val cycleStart = floor(from.toDouble()).toLong()
        val cycleEnd = floor(to.toDouble()).toLong()

        for (cycle in cycleStart..cycleEnd) {
            val cycleRat = cycle.toRational()

            // The compressed region in this cycle
            val compressedStart = cycleRat + start
            val compressedEnd = cycleRat + end

            // Check if our query range intersects with the compressed region
            if (to <= compressedStart || from >= compressedEnd) {
                continue // No intersection
            }

            // Query the source pattern for the full cycle (0 to 1)
            // We need to map the entire cycle content into our compressed region
            val sourceEvents = source.queryArcContextual(cycleRat, cycleRat + Rational.ONE, ctx)

            // Map each event from source cycle [0,1) to compressed region [start,end)
            for (ev in sourceEvents) {
                // Map event times from [cycle, cycle+1) to [compressedStart, compressedEnd)
                val relativeBegin = ev.begin - cycleRat // Position within source cycle [0,1)
                val relativeEnd = ev.end - cycleRat

                val mappedBegin = compressedStart + (relativeBegin * span)
                val mappedEnd = compressedStart + (relativeEnd * span)
                val mappedDur = ev.dur * span

                // Only include if it intersects with our query range
                if (mappedEnd > from && mappedBegin < to) {
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
