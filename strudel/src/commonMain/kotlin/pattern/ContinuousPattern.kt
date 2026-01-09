package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * A pattern that generates a value based on continuous cycle time.
 */
class ContinuousPattern(
    val min: Double = 0.0,
    val max: Double = 1.0,
    val getValue: (Double) -> Double,
) : StrudelPattern.FixedWeight {

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        return listOf(
            StrudelPatternEvent(
                begin = from, end = to, dur = to - from,
                data = VoiceData.empty.copy(value = getValue(from.toDouble()))
            )
        )
    }

    /** Creates a new version of this pattern with a transformed value range */
    internal fun applyRange(min: Double, max: Double): ContinuousPattern {
        return ContinuousPattern(
            min = min,
            max = max,
            getValue = { t ->
                val value = getValue(t)
                // Map current value (from this.min..this.max) to the new range
                val normalized = (value - this.min) / (this.max - this.min)
                (min + (normalized * (max - min)))
            },
        )
    }
}
