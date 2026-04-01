package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Repeats each cycle of the source pattern [n] times.
 *
 * Example:
 * source: Cycle 0="a", Cycle 1="b"
 * repeatCycles(2): "a", "a", "b", "b"
 *
 * For static patterns (like note("c")), this acts as identity.
 */
class RepeatCyclesPattern(
    private val source: SprudelPattern,
    private val n: Rational,
) : SprudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        val result = mutableListOf<SprudelPatternEvent>()
        val nDouble = n.toDouble()

        // Iterate through each cycle in the output range
        var currentCycle = floor(from.toDouble()).toInt()
        val lastCycle = ceil(to.toDouble()).toInt()

        while (currentCycle < lastCycle) {
            val cycleStart = currentCycle.toDouble().toRational()
            val cycleEnd = cycleStart + Rational.ONE

            // Calculate which source cycle corresponds to this output cycle
            // Source cycle = floor(current_output_cycle / n)
            val sourceCycleIndex = floor(currentCycle / nDouble).toInt()
            val sourceCycleStart = sourceCycleIndex.toDouble().toRational()

            // Overlap of current output cycle with query range
            val queryStart = maxOf(from, cycleStart)
            val queryEnd = minOf(to, cycleEnd)

            if (queryStart < queryEnd) {
                // Map query times to source times
                // Time within cycle is T - cycleStart
                // Source time = sourceCycleStart + (T - cycleStart)
                val mapToSource = { t: Rational -> sourceCycleStart + (t - cycleStart) }

                val sourceQueryStart = mapToSource(queryStart)
                val sourceQueryEnd = mapToSource(queryEnd)

                val events = source.queryArcContextual(sourceQueryStart, sourceQueryEnd, ctx)

                // Map events back to output time
                // T_out = cycleStart + (T_src - sourceCycleStart)
                events.forEach { event ->
                    val offset = cycleStart - sourceCycleStart
                    val shiftedPart = event.part.shift(offset)
                    val shiftedWhole = event.whole.shift(offset)

                    result.add(event.copy(part = shiftedPart, whole = shiftedWhole))
                }
            }

            currentCycle++
        }

        return result
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration() * n
    }

    override val numSteps: Rational? = source.numSteps

    companion object {
        /**
         * Creates a RepeatCyclesPattern from a control pattern.
         */
        fun control(
            source: SprudelPattern,
            repetitionsPattern: SprudelPattern,
        ): SprudelPattern {
            return object : SprudelPattern.FixedWeight {
                override fun queryArcContextual(
                    from: Rational,
                    to: Rational,
                    ctx: SprudelPattern.QueryContext,
                ): List<SprudelPatternEvent> {
                    val repsEvents = repetitionsPattern.queryArcContextual(from, from + Rational.ONE, ctx)
                    val repsValue = repsEvents.firstOrNull()?.data?.value?.asDouble ?: 1.0
                    val repetitions = repsValue.toRational()

                    return RepeatCyclesPattern(source, repetitions).queryArcContextual(from, to, ctx)
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val numSteps: Rational? = source.numSteps
            }
        }
    }
}
