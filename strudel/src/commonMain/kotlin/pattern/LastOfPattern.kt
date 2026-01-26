package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that applies a transformation every n cycles, on the last cycle of each group.
 *
 * For example, with n=4:
 * - Cycles 0-2: original pattern
 * - Cycle 3: transform applied
 * - Cycles 4-6: original pattern
 * - Cycle 7: transform applied again
 * - etc.
 *
 * @param source The original pattern
 * @param nPattern The control pattern determining the cycle group size
 * @param transform The transformation function to apply on last cycles
 */
internal class LastOfPattern(
    val source: StrudelPattern,
    val nPattern: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {
    override val weight = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        // Query the n pattern to get the cycle group size for this timespan
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()

        // For each n value's timespan, determine which cycles should be transformed
        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1
            if (n <= 0) continue

            val eventFrom = maxOf(from, nEvent.begin)
            val eventTo = minOf(to, nEvent.end)

            if (eventFrom >= eventTo) continue

            // Determine which cycles are covered by this timespan
            val startCycle = eventFrom.floor()
            val endCycle = eventTo.ceil()

            for (cycle in startCycle.toLong() until endCycle.toLong()) {
                val cycleRat = Rational(cycle)
                val cycleStart = maxOf(eventFrom, cycleRat)
                val cycleEnd = minOf(eventTo, cycleRat + Rational.ONE)

                if (cycleStart >= cycleEnd) continue

                // Determine if this is the last cycle of an n-cycle group
                val positionInGroup = (cycle % n + n) % n
                val shouldTransform = positionInGroup == (n - 1).toLong()

                val patternToUse = if (shouldTransform) transform(source) else source
                val events = patternToUse.queryArcContextual(cycleStart, cycleEnd, ctx)
                result.addAll(events)
            }
        }

        return result
    }
}
