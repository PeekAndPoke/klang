package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * A pattern that generates a value based on continuous cycle time.
 */
class ContinuousPattern(
    val getValue: (Double) -> Double,
) : StrudelPattern.FixedWeight {
    companion object {
        val minKey = QueryContext.Key<Double>("rangeMin")
        val maxKey = QueryContext.Key<Double>("rangeMax")
        val granularityKey = QueryContext.Key<Rational>("granularity")

        val minGranularity = Rational(1 / 16.0)
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

        val value = getValue(
            min = ctx.getOrDefault(minKey, 0.0),
            max = ctx.getOrDefault(maxKey, 1.0),
            t = from.toDouble(),
        ).asVoiceValue()

        // Make sure we do not run into an infinite loop
        val granularity = maxOf(
            minGranularity,
            ctx.getOrDefault(granularityKey, (to - from))
        )

        val result = mutableListOf<StrudelPatternEvent>()
        var currentFrom = from

        val isGreater = to > currentFrom

        while (to > currentFrom) {
            val nextFrom = minOf(to, currentFrom + granularity)

            result.add(
                StrudelPatternEvent(
                    begin = currentFrom,
                    end = nextFrom,
                    dur = nextFrom - currentFrom,
                    data = VoiceData.empty.copy(
                        value = value,
                    )
                )
            )

            currentFrom = nextFrom
        }

        return result
    }

    /** Creates a new version of this pattern with a transformed value range */
    internal fun getValue(min: Double, max: Double, t: Double): Double {
        val value = getValue(t)
        // The internal oscillators now produce 0.0 to 1.0.
        // We map this unipolar value to the target min..max range.
        return min + (value * (max - min))
    }
}
