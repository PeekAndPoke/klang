package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan

/**
 * A pattern that generates a value based on continuous cycle time.
 */
class ContinuousPattern private constructor(
    val getValue: (from: Double, to: Double, ctx: QueryContext) -> Double,
) : StrudelPattern.FixedWeight {
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

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

        val fromD = from.toDouble()
        val toD = to.toDouble()

        val value = getValue(
            min = ctx.getOrDefault(minKey, 0.0),
            max = ctx.getOrDefault(maxKey, 1.0),
            from = fromD,
            to = toD,
            ctx = ctx
        ).asVoiceValue()

        // Fast path: single span (covers segment(1) and most typical queries)
        if (toD - fromD <= 1.0) {
            val span = TimeSpan(begin = from, end = to)
            return listOf(
                StrudelPatternEvent(
                    part = span,
                    whole = span,
                    data = StrudelVoiceData.empty.copy(value = value)
                )
            )
        }

        // Multi-cycle path: step by 1 cycle at a time
        val result = createEventList()
        var currentFrom = from

        while (to > currentFrom) {
            val nextFrom = minOf(to, currentFrom + Rational.ONE)

            val span = TimeSpan(begin = currentFrom, end = nextFrom)

            result.add(
                StrudelPatternEvent(
                    part = span,
                    whole = span,
                    data = StrudelVoiceData.empty.copy(value = value)
                )
            )

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
