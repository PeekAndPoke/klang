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
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

        val value = getValue(
            min = ctx.getOrDefault(minKey, 0.0),
            max = ctx.getOrDefault(maxKey, 1.0),
            t = from.toDouble(),
        ).asVoiceValue()

        return listOf(
            StrudelPatternEvent(
                begin = from, end = to, dur = to - from,
                data = VoiceData.empty.copy(value = value)
            )
        )
    }

    /** Creates a new version of this pattern with a transformed value range */
    internal fun getValue(min: Double, max: Double, t: Double): Double {
        val value = getValue(t)
        // The internal oscillators now produce 0.0 to 1.0.
        // We map this unipolar value to the target min..max range.
        return min + (value * (max - min))
    }
}
