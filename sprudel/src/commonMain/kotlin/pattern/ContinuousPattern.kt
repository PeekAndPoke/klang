package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan

/**
 * A pattern that generates a value based on continuous cycle time.
 */
class ContinuousPattern private constructor(
    val getValue: (from: Double, to: Double, ctx: QueryContext) -> Double,
) : SprudelPattern.FixedWeight {
    companion object {
        val minKey = QueryContext.Key<Double>("rangeMin")
        val maxKey = QueryContext.Key<Double>("rangeMax")

        operator fun invoke(getValue: (from: Double) -> Double) =
            ContinuousPattern { from, _, _ -> getValue(from) }

        operator fun invoke(getValue: (from: Double, to: Double, ctx: QueryContext) -> Double) =
            ContinuousPattern(getValue)
    }

    override val numSteps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {

        val value = getValue(
            min = ctx.getOrDefault(minKey, 0.0),
            max = ctx.getOrDefault(maxKey, 1.0),
            from = from.toDouble(),
            to = to.toDouble(),
            ctx = ctx
        ).asVoiceValue()

        // Make sure we do not run into an infinite loop
        val granularity = 1.0
        val result = createEventList()
        var currentFrom = from

        while (to > currentFrom) {
            val nextFrom = minOf(to, currentFrom + granularity)

            val span = TimeSpan(begin = currentFrom, end = nextFrom)

            val event = SprudelPatternEvent(
                part = span,
                whole = span,
                data = SprudelVoiceData.empty.copy(value = value)
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
