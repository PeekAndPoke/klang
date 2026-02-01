package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Keeps only the first [n] steps from the source pattern structure.
 * This operates on structural steps (mini-notation elements), not temporal cycles.
 *
 * Example: note("c d e f").take(2) keeps first 2 steps -> "c d" repeating
 * The pattern "c d e f" has 4 steps. take(2) keeps steps 0-1, creating pattern "c d"
 */
class TakePattern(
    private val source: StrudelPattern,
    private val n: Rational,
) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        val sourceSteps = source.numSteps

        // If source has no defined steps, fall back to cycle-based take
        if (sourceSteps == null || sourceSteps <= Rational.ZERO) {
            return fallbackCycleTake(from, to, ctx)
        }

        // Calculate the fraction of the cycle occupied by n steps
        val stepFraction = n / sourceSteps

        // If taking more steps than available, just return source as-is
        if (stepFraction >= Rational.ONE) {
            return source.queryArcContextual(from, to, ctx)
        }

        val result = createEventList()

        // Process each cycle in the query range
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            val cycleStart = Rational(cycle)
            val cycleEnd = cycleStart + Rational.ONE

            // Define the "take window" within this cycle (first n steps)
            val takeWindowStart = cycleStart
            val takeWindowEnd = cycleStart + stepFraction

            // Query source for this cycle
            val cycleQueryStart = maxOf(from, cycleStart)
            val cycleQueryEnd = minOf(to, cycleEnd)

            if (cycleQueryEnd <= cycleQueryStart) continue

            val sourceEvents = source.queryArcContextual(cycleQueryStart, cycleQueryEnd, ctx)

            // Keep only events that start within the take window
            val takenEvents = sourceEvents.filter { event ->
                event.part.begin >= takeWindowStart && event.part.begin < takeWindowEnd
            }.mapNotNull { event ->
                // Clip event end to take window boundary
                if (event.part.end > takeWindowEnd) {
                    val takeWindow = io.peekandpoke.klang.strudel.TimeSpan(takeWindowStart, takeWindowEnd)
                    val clippedPart = event.part.clipTo(takeWindow)
                    clippedPart?.let { event.copy(part = it) }
                } else {
                    event
                }
            }

            // Scale events to fill the full cycle
            // Map from [cycleStart, cycleStart + stepFraction] to [cycleStart, cycleStart + 1]
            val scaledEvents = takenEvents.map { event ->
                val scaleFactor = Rational.ONE / stepFraction
                val scaledPart = event.part.shift(-cycleStart).scale(scaleFactor).shift(cycleStart)
                val scaledWhole = event.whole?.shift(-cycleStart)?.scale(scaleFactor)?.shift(cycleStart)

                event.copy(
                    part = scaledPart,
                    whole = scaledWhole
                )
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
    private fun fallbackCycleTake(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        if (from >= n) return emptyList()

        val clampedEnd = minOf(to, n)
        val events = source.queryArcContextual(from, clampedEnd, ctx)

        return events.filter { it.part.begin < n }.mapNotNull { event ->
            if (event.part.end > n) {
                val boundary = io.peekandpoke.klang.strudel.TimeSpan(Rational.ZERO, n)
                val clippedPart = event.part.clipTo(boundary)
                clippedPart?.let { event.copy(part = it) }
            } else {
                event
            }
        }
    }

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override val numSteps: Rational? = n  // The result has n steps

    companion object {
        /**
         * Creates a TakePattern from a control pattern.
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
                        val nValue = nEvents.firstOrNull()?.data?.value?.asDouble ?: 1.0
                        val n = nValue.toRational()

                        // Query this cycle with the sampled n value
                        val cycleQueryStart = maxOf(from, cycleStart)
                        val cycleQueryEnd = minOf(to, cycleEnd)

                        if (cycleQueryEnd > cycleQueryStart) {
                            val cyclePattern = TakePattern(source, n)
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
