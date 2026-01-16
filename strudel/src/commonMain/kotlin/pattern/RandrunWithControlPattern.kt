package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that generates random sequences with varying length based on a control pattern.
 *
 * This pattern queries the control pattern [nPattern] to determine the sequence length,
 * then generates a shuffled sequence of integers from 0 to n-1 for each control value.
 * The shuffle changes every cycle.
 *
 * @param nPattern The pattern controlling the sequence length
 */
internal class RandrunWithControlPattern(
    val nPattern: StrudelPattern,
) : StrudelPattern {
    override val weight = 1.0
    override val steps: Rational? = null

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 0
            if (n < 1) continue

            // Generate shuffled permutation for this cycle
            val updatedCtx = ctx.update {
                setIfAbsent(QueryContext.randomSeed, 0)
            }
            val cycle = nEvent.begin.floor()
            val random = updatedCtx.getSeededRandom(cycle, "randrun")
            val permutation = (0 until n).toMutableList()
            permutation.shuffle(random)

            // Create n evenly-spaced events in the control event's timespan
            val duration = nEvent.end - nEvent.begin
            val stepSize = duration / Rational(n)

            for (index in 0 until n) {
                val eventBegin = nEvent.begin + (stepSize * Rational(index))
                val eventEnd = eventBegin + stepSize
                val value = permutation[index].asVoiceValue()

                result.add(
                    StrudelPatternEvent(
                        begin = eventBegin,
                        end = eventEnd,
                        dur = stepSize,
                        data = VoiceData.empty.copy(value = value)
                    )
                )
            }
        }

        return result
    }
}
