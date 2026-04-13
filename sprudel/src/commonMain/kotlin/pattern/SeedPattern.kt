package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
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

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val result = createEventList()

        val firstCycle = from.floor().toInt()
        val lastCycle = (to - QUERY_EPSILON).floor().toInt()

        for (cycleInt in firstCycle..lastCycle) {
            val cycle = cycleInt.toRational()
            val cycleEnd = cycle + Rational.ONE

            // Sample the seed pattern at the start of this cycle
            val seed = seedPattern.sampleAt(cycle, ctx)?.data?.value?.asInt

            // Clip query to this cycle
            val queryFrom = maxOf(from, cycle)
            val queryTo = minOf(to, cycleEnd)

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

    companion object {
        private val QUERY_EPSILON = 1e-7.toRational()
    }
}