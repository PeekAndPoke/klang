package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.sampleAt

/**
 * Shuffles the source pattern by dividing it into `n` equal slices per cycle
 * and playing them in a random order. Each slice appears exactly once per cycle.
 *
 * [nPattern] is sampled once per cycle to determine the number of slices.
 * The source pattern's timing is preserved within each slice — only the slice
 * order is randomised. The shuffle permutation changes every cycle.
 */
internal class ShufflePattern(
    val source: SprudelPattern,
    val nPattern: SprudelPattern,
) : SprudelPattern {

    override val weight: Double = source.weight

    override val numSteps: Rational? = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val result = createEventList()
        val updatedCtx = ctx.update { setIfAbsent(QueryContext.randomSeedKey, 0) }

        val firstCycle = from.floor().toInt()
        val lastCycle = (to - QUERY_EPSILON).floor().toInt()

        for (cycleInt in firstCycle..lastCycle) {
            val cycle = cycleInt.toRational()

            // Sample n from the control pattern at the start of this cycle
            val n = nPattern.sampleAt(cycle, updatedCtx)?.data?.value?.asInt ?: 1
            if (n < 1) continue

            val nRat = n.toRational()

            // Same permutation every cycle (seeded by n, not by cycle)
            val random = updatedCtx.getSeededRandom(n, "shuffle")
            val permutation = (0 until n).toMutableList()
            permutation.shuffle(random)

            for (i in 0 until n) {
                val slotStart = cycle + i.toRational() / nRat
                val slotEnd = cycle + (i + 1).toRational() / nRat

                // Skip slots outside the query range
                if (slotEnd <= from || slotStart >= to) continue

                val sourceSliceIdx = permutation[i]
                // Time shift to move source slice events into the output slot
                val timeShift = (i - sourceSliceIdx).toRational() / nRat

                // Map query range into source time
                val sourceFrom = maxOf(from, slotStart) - timeShift
                val sourceTo = minOf(to, slotEnd) - timeShift

                val sourceEvents = source.queryArcContextual(sourceFrom, sourceTo, updatedCtx)

                for (event in sourceEvents) {
                    val shiftedPart = event.part.shift(timeShift)
                    val shiftedWhole = event.whole.shift(timeShift)

                    // Clip to slot boundaries
                    val clippedPart = shiftedPart.clipTo(slotStart, slotEnd) ?: continue

                    result.add(event.copy(part = clippedPart, whole = shiftedWhole))
                }
            }
        }

        return result
    }

    companion object {
        private val QUERY_EPSILON = 1e-7.toRational()
    }
}
