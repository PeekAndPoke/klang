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
class ContinuousPattern private constructor(
    val getValue: (from: Double, to: Double, ctx: QueryContext) -> Double,
) : StrudelPattern.FixedWeight {
    companion object {
        val minKey = QueryContext.Key<Double>("rangeMin")
        val maxKey = QueryContext.Key<Double>("rangeMax")
        val granularityKey = QueryContext.Key<Rational>("granularity")

        val minGranularity = Rational(1 / 16.0)

        operator fun invoke(getValue: (from: Double) -> Double) =
            ContinuousPattern { from, _, _ -> getValue(from) }

        operator fun invoke(getValue: (from: Double, to: Double, ctx: QueryContext) -> Double) =
            ContinuousPattern(getValue)
    }

    override val steps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

        val value = getValue(
            min = ctx.getOrDefault(minKey, 0.0),
            max = ctx.getOrDefault(maxKey, 1.0),
            from = from.toDouble(),
            to = to.toDouble(),
            ctx = ctx
        ).asVoiceValue()

        // Make sure we do not run into an infinite loop
        val granularity = maxOf(
            minGranularity,
            ctx.getOrDefault(granularityKey, (to - from))
        )

        val result = mutableListOf<StrudelPatternEvent>()
        var currentFrom = from

        while (to > currentFrom) {
            val nextFrom = minOf(to, currentFrom + granularity)

            val event = StrudelPatternEvent(
                begin = currentFrom,
                end = nextFrom,
                dur = nextFrom - currentFrom,
                data = VoiceData.empty.copy(value = value)
            )

            result.add(event)

            // go ahead
            currentFrom = nextFrom
        }

        return result
    }

    /** Creates a new version of this pattern with a transformed value range */
    internal fun getValue(min: Double, max: Double, from: Double, to: Double, ctx: QueryContext): Double {
        val value = getValue(from, to, ctx)
        // The internal oscillators now produce 0.0 to 1.0.
        // We map this unipolar value to the target min..max range.
        return min + (value * (max - min))
    }
}
