package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

/**
 * Pattern that generates random sequences with varying length based on a control pattern.
 *
 * This pattern queries the control pattern [nPattern] to determine the sequence length,
 * then generates a shuffled sequence of integers from 0 to n-1 for each control value.
 * The shuffle changes every cycle.
 *
 * @param nPattern The pattern controlling the sequence length
 */
internal class RandrunPattern(
    val nPattern: SprudelPattern,
) : SprudelPattern {
    override val weight = 1.0

    override val numSteps: Double? = null

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 0
            if (n < 1) continue

            // Generate shuffled permutation for this cycle
            val updatedCtx = ctx.update {
                setIfAbsent(QueryContext.randomSeedKey, 0)
            }
            val cycle = nEvent.part.begin.cycleIndex()
            val random = updatedCtx.getSeededRandom(cycle, "randrun")
            val permutation = (0 until n).toMutableList()
            permutation.shuffle(random)

            // Create n evenly-spaced events in the control event's timespan, using absolute
            // boundaries so the last event ends exactly at base+duration (no rounding gap).
            val duration = nEvent.part.duration
            val base = nEvent.part.begin

            for (index in 0 until n) {
                val eventBegin = base + duration.scaleBy(index.toDouble() / n)
                val eventEnd = base + duration.scaleBy((index + 1).toDouble() / n)
                val value = permutation[index].asVoiceValue()

                val timeSpan = CycleTimeSpan(begin = eventBegin, end = eventEnd)

                result.add(
                    SprudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = SprudelVoiceData(value = value)
                    )
                )
            }
        }

        return result
    }
}
