package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Sequence Pattern: Squashes multiple patterns into a single cycle.
 * Implementation of `seq(a, b)`.
 */
internal class SequencePattern(
    val patterns: List<SprudelPattern>,
) : SprudelPattern.FixedWeight {

    companion object {
        fun create(patterns: List<SprudelPattern>): SprudelPattern = when {
            patterns.isEmpty() -> EmptyPattern
            patterns.size == 1 -> patterns.first()
            else -> SequencePattern(patterns)
        }
    }

    override val numSteps: Double get() = patterns.size.toDouble()

    override fun estimateCycleDuration(): Double = 1.0

    // Calculate proportional offsets based on weights
    private val weights = patterns.map { it.weight }
    private val totalWeight = weights.sum()
    private val offsets = mutableListOf(0.0)

    init {
        if (totalWeight == 0.0) {
            // All weights are zero — fall back to equal distribution
            val equalStep = 1.0 / patterns.size.toDouble()
            patterns.indices.forEach { i ->
                offsets.add(equalStep * (i + 1).toDouble())
            }
        } else {
            weights.forEach { w ->
                offsets.add(offsets.last() + (w / totalWeight))
            }
        }
    }

    // Precomputed cycle-local step boundaries and sizes — fixed per pattern, so they are snapped to
    // ticks once here instead of calling CycleTime.ofCycles on every query (hot path).
    private val offsetTimes: List<CycleTime> = offsets.map { CycleTime.ofCycles(it) }
    private val stepSizes: List<Double> = (0 until patterns.size).map { offsets[it + 1] - offsets[it] }

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        val events = createEventList()

        // Optimize: Iterate only over the cycles involved in the query
        val startCycle = from.cycleIndex()
        val endCycle = to.ceilToCycle().cycleIndex()

        for (cycle in startCycle until endCycle) {
            val cycleOffset = CycleTime.ofCycleIndex(cycle)
            patterns.forEachIndexed { index, pattern ->
                val stepStart = cycleOffset + offsetTimes[index]
                val stepEnd = cycleOffset + offsetTimes[index + 1]
                val stepSize = stepSizes[index]

                // Calculate the intersection of this step with the query arc
                val intersectStart = from.coerceAtLeast(stepStart)
                val intersectEnd = to.coerceAtMost(stepEnd)

                // Map the "outer" time to the "inner" pattern time.
                // The inner pattern covers 0..1 logically for this step.
                // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                val innerFrom = (intersectStart - stepStart).divBy(stepSize) + cycleOffset
                // Clamp innerTo to the end of the cycle
                val innerTo = ((intersectEnd - stepStart).divBy(stepSize) + cycleOffset)
                    .coerceAtMost(cycleOffset + CycleTime.ONE)

                val innerEvents = pattern.queryArcContextual(innerFrom, innerTo, ctx)

                events.addAll(innerEvents.mapNotNull { ev ->
                    // Map back to outer time - scale and shift both part and whole
                    val scaledPart = ev.part.shift(-cycleOffset).scale(stepSize).shift(stepStart)
                    val scaledWhole = ev.whole.shift(-cycleOffset).scale(stepSize).shift(stepStart)

                    if (scaledPart.end > from) {
                        ev.copy(part = scaledPart, whole = scaledWhole)
                    } else {
                        null
                    }
                })
            }
        }

        return events
    }
}
