package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that applies a transformation every n cycles, on the first cycle of each group.
 *
 * For example, with n=4:
 * - Cycle 0: transform applied
 * - Cycles 1-3: original pattern
 * - Cycle 4: transform applied again
 * - Cycles 5-7: original pattern
 * - etc.
 *
 * @param source The original pattern
 * @param nPattern The control pattern determining the cycle group size
 * @param transform The transformation function to apply on first cycles
 */
internal class FirstOfPattern(
    val source: StrudelPattern,
    val nPattern: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {
    override val weight = source.weight

    override val numSteps: Rational? get() = source.numSteps

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

                // Determine if this is the first cycle of an n-cycle group
                val positionInGroup = (cycle % n + n) % n
                val shouldTransform = positionInGroup == 0L

                val patternToUse = if (shouldTransform) transform(source) else source
                val events = patternToUse.queryArcContextual(cycleStart, cycleEnd, ctx)

                // Clip events to the valid window for this 'n' and this cycle
                for (event in events) {
                    val clippedBegin = maxOf(event.begin, cycleStart)
                    val clippedEnd = minOf(event.end, cycleEnd)

                    if (clippedEnd > clippedBegin) {
                        result.add(
                            event.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedEnd - clippedBegin
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
