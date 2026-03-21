package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan

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

    override val numSteps: Rational? = null

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
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
            val cycle = nEvent.part.begin.floor()
            val random = updatedCtx.getSeededRandom(cycle, "randrun")
            val permutation = (0 until n).toMutableList()
            permutation.shuffle(random)

            // Create n evenly-spaced events in the control event's timespan
            val duration = nEvent.part.duration
            val stepSize = duration / Rational(n)

            for (index in 0 until n) {
                val eventBegin = nEvent.part.begin + (stepSize * Rational(index))
                val eventEnd = eventBegin + stepSize
                val value = permutation[index].asVoiceValue()

                val timeSpan = TimeSpan(begin = eventBegin, end = eventEnd)

                result.add(
                    SprudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = SprudelVoiceData.empty.copy(value = value)
                    )
                )
            }
        }

        return result
    }
}
