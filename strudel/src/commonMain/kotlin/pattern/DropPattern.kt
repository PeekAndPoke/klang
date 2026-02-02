package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Skips the first [n] steps from the source pattern structure.
 * This operates on structural steps (mini-notation elements), not temporal cycles.
 *
 * Example: note("c d e f").drop(1) skips first step -> "d e f" stretched to fill cycle
 * The pattern "c d e f" has 4 steps. drop(1) skips step 0, keeps steps 1-3, stretched
 */
class DropPattern(
    private val source: StrudelPattern,
    private val n: Rational,
) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        val sourceSteps = source.numSteps

        // If source has no defined steps, fall back to cycle-based drop
        if (sourceSteps == null || sourceSteps <= Rational.ZERO) {
            return fallbackCycleDrop(from, to, ctx)
        }

        // If dropping all or more steps, return empty
        if (n >= sourceSteps) {
            return emptyList()
        }

        // Calculate the fraction of the cycle to skip
        val skipFraction = n / sourceSteps
        // Remaining fraction after drop
        val keepFraction = Rational.ONE - skipFraction

        val result = createEventList()

        // Process each cycle in the query range
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            val cycleStart = Rational(cycle)
            val cycleEnd = cycleStart + Rational.ONE

            // Define the "drop window" within this cycle (steps to skip)
            val dropWindowEnd = cycleStart + skipFraction
            val keepWindowStart = dropWindowEnd
            val keepWindowEnd = cycleEnd

            // Query source for this cycle
            val cycleQueryStart = maxOf(from, cycleStart)
            val cycleQueryEnd = minOf(to, cycleEnd)

            if (cycleQueryEnd <= cycleQueryStart) continue

            val sourceEvents = source.queryArcContextual(cycleQueryStart, cycleQueryEnd, ctx)

            // Keep only events that start after the drop window
            val keptEvents = sourceEvents.filter { event ->
                event.part.begin >= keepWindowStart
            }

            // Scale events to fill the full cycle
            // Map from [keepWindowStart, keepWindowEnd] to [cycleStart, cycleEnd]
            val scaledEvents = keptEvents.map { event ->
                val scaleFactor = Rational.ONE / keepFraction
                // Shift events from keepWindowStart to origin, scale, then shift to cycleStart
                val scaledPart = event.part.shift(-keepWindowStart).scale(scaleFactor).shift(cycleStart)
                val scaledWhole = event.whole.shift(-keepWindowStart).scale(scaleFactor).shift(cycleStart)

                event.copy(part = scaledPart, whole = scaledWhole)
            }

            // Filter to query range
            result.addAll(scaledEvents.filter { event ->
                event.part.end > from && event.part.begin < to
            })
        }

        return result
    }

    /**
     * Fallback for patterns without defined steps - treat n as cycles
     */
    private fun fallbackCycleDrop(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        val shiftedBegin = from + n
        val shiftedEnd = to + n

        val events = source.queryArcContextual(shiftedBegin, shiftedEnd, ctx)

        return events.map { event ->
            val shiftedPart = event.part.shift(-n)
            val shiftedWhole = event.whole.shift(-n)
            event.copy(part = shiftedPart, whole = shiftedWhole)
        }
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val numSteps: Rational? = source.numSteps?.let { it - n }

    companion object {
        /**
         * Creates a DropPattern from a control pattern.
         * The n value is sampled from the control pattern each cycle.
         */
        fun control(
            source: StrudelPattern,
            nPattern: StrudelPattern,
        ): StrudelPattern {
            return object : StrudelPattern.FixedWeight {
                override fun queryArcContextual(
                    from: Rational,
                    to: Rational,
                    ctx: StrudelPattern.QueryContext,
                ): List<StrudelPatternEvent> {
                    val result = createEventList()

                    // Process each cycle separately
                    val startCycle = from.floor().toInt()
                    val endCycle = to.ceil().toInt()

                    for (cycle in startCycle until endCycle) {
                        val cycleStart = Rational(cycle)
                        val cycleEnd = cycleStart + Rational.ONE

                        // Sample the n value for this specific cycle
                        val epsilon = 1e-5.toRational()
                        val nEvents = nPattern.queryArcContextual(cycleStart, cycleStart + epsilon, ctx)
                        val nValue = nEvents.firstOrNull()?.data?.value?.asDouble ?: 0.0
                        val n = nValue.toRational()

                        // Query this cycle with the sampled n value
                        val cycleQueryStart = maxOf(from, cycleStart)
                        val cycleQueryEnd = minOf(to, cycleEnd)

                        if (cycleQueryEnd > cycleQueryStart) {
                            val cyclePattern = DropPattern(source, n)
                            val cycleEvents = cyclePattern.queryArcContextual(cycleQueryStart, cycleQueryEnd, ctx)
                            result.addAll(cycleEvents)
                        }
                    }

                    return result
                }

                override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
                override val numSteps: Rational? = source.numSteps
            }
        }
    }
}
