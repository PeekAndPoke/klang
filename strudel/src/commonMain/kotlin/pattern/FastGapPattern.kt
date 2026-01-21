package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Speeds up a pattern like fast, but plays it only once per cycle in the compressed time, leaving a gap.
 *
 * For example, fastGap(2) compresses the pattern into the first half of each cycle,
 * leaving the second half silent.
 *
 * This is equivalent to compress(0, 1/factor).
 *
 * @param source The source pattern to compress
 * @param factor The speed/compression factor
 */
internal class FastGapPattern(
    val source: StrudelPattern,
    val factor: Double,
) : StrudelPattern {

    private val factorRat = factor.toRational()
    private val span = Rational.ONE / factorRat

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // Handle edge case where factor <= 0
        if (factor <= 0.0) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // Determine which cycles we need to query
        val cycleStart = floor(from.toDouble()).toLong()
        val cycleEnd = floor(to.toDouble()).toLong()

        for (cycle in cycleStart..cycleEnd) {
            val cycleRat = cycle.toRational()

            // The compressed region in this cycle: [cycle, cycle + 1/factor)
            val compressedStart = cycleRat
            val compressedEnd = cycleRat + span

            // Check if our query range intersects with the compressed region
            if (to <= compressedStart || from >= compressedEnd) {
                continue // No intersection
            }

            // Query the source pattern for the full cycle (0 to 1)
            val sourceEvents = source.queryArcContextual(cycleRat, cycleRat + Rational.ONE, ctx)

            // Map each event from source cycle [0,1) to compressed region [start, start+span)
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
