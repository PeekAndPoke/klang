package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.sampleAt

/**
 * Sets the random seed from a control pattern, sampled once per cycle.
 *
 * For each cycle, the [seedPattern] is sampled at the cycle start, and the resulting
 * integer value is set as the random seed in the query context before querying the source.
 * If the sample is null or non-integer, the seed is removed from the context.
 */
internal class SeedPattern(
    val source: SprudelPattern,
    val seedPattern: SprudelPattern,
) : SprudelPattern {

    override val weight: Double = source.weight

    override val numSteps: Double? get() = source.numSteps

    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val result = createEventList()

        val firstCycle = from.cycleIndex()
        val lastCycle = (to - SprudelPattern.QUERY_EPSILON).cycleIndex()

        for (cycleInt in firstCycle..lastCycle) {
            val cycle = CycleTime.ofCycleIndex(cycleInt)
            val cycleEnd = cycle + CycleTime.ONE

            // Sample the seed pattern at the start of this cycle
            val seed = seedPattern.sampleAt(cycle, ctx)?.data?.value?.asInt

            // Clip query to this cycle
            val queryFrom = from.coerceAtLeast(cycle)
            val queryTo = to.coerceAtMost(cycleEnd)

            // Update context with the sampled seed
            val updatedCtx = ctx.update {
                if (seed != null) {
                    set(QueryContext.randomSeedKey, seed)
                } else {
                    remove(QueryContext.randomSeedKey)
                }
            }

            result.addAll(source.queryArcContextual(queryFrom, queryTo, updatedCtx))
        }

        return result
    }
}
