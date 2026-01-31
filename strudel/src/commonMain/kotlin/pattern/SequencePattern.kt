package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.sum
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Sequence Pattern: Squashes multiple patterns into a single cycle.
 * Implementation of `seq(a, b)`.
 */
internal class SequencePattern(
    val patterns: List<StrudelPattern>,
) : StrudelPattern.FixedWeight {

    companion object {
        fun create(patterns: List<StrudelPattern>): StrudelPattern = when {
            patterns.isEmpty() -> EmptyPattern
            patterns.size == 1 -> patterns.first()
            else -> SequencePattern(patterns)
        }

        private val MIN_QUERY_LENGTH = 1e-7.toRational()
    }

    override val numSteps: Rational get() = patterns.size.toRational()

    override fun estimateCycleDuration(): Rational = Rational.ONE

    // Calculate proportional offsets based on weights
    private val weights = patterns.map { it.weight.toRational() }
    private val totalWeight = weights.sum()
    private val offsets = mutableListOf(Rational.ZERO)

    init {
        weights.forEach { w ->
            offsets.add(offsets.last() + (w / totalWeight))
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        val events = createEventList()

        // Optimize: Iterate only over the cycles involved in the query
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            patterns.forEachIndexed { index, pattern ->
                val cycleOffset = Rational(cycle)
                val stepStart = cycleOffset + offsets[index]
                val stepEnd = cycleOffset + offsets[index + 1]
                val stepSize = offsets[index + 1] - offsets[index]

                // Calculate the intersection of this step with the query arc
                val intersectStart = maxOf(from, stepStart)
                val intersectEnd = minOf(to, stepEnd)

                // Map the "outer" time to the "inner" pattern time.
                // The inner pattern covers 0..1 logically for this step.
                // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                val innerFrom = (intersectStart - stepStart) / stepSize + cycleOffset
                // Clamp innerTo to the end of the cycle
                val innerTo = minOf((intersectEnd - stepStart) / stepSize + cycleOffset, cycleOffset + Rational.ONE)

                // Condition for when to take the event
                val takeIt = intersectEnd > intersectStart &&
                        // IMPORTANT: Protection against floating point precision issues
                        (innerTo - innerFrom > MIN_QUERY_LENGTH)

                if (takeIt) {

                    val innerEvents = pattern.queryArcContextual(innerFrom, innerTo, ctx)

                    events.addAll(innerEvents.mapNotNull { ev ->
                        // Map back to outer time - scale and shift both part and whole
                        val scaledPart = ev.part.shift(-cycleOffset).scale(stepSize).shift(stepStart)
                        val scaledWhole = ev.whole?.shift(-cycleOffset)?.scale(stepSize)?.shift(stepStart)

                        if (scaledPart.end > from) {
                            ev.copy(part = scaledPart, whole = scaledWhole)
                        } else {
                            null
                        }
                    })
                }
            }
        }

        return events
    }
}
