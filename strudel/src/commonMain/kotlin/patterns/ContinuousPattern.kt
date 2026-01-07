package io.peekandpoke.klang.strudel.patterns

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

/**
 * A pattern that generates a value based on continuous cycle time.
 */
open class ContinuousPattern(
    val getValue: (Double) -> Double,
) : StrudelPattern {
    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        return listOf(
            StrudelPatternEvent(
                begin = from, end = to, dur = to - from,
                data = VoiceData.empty.copy(value = getValue(from))
            )
        )
    }

    /** Creates a new version of this pattern with a transformed value range */
    fun range(min: Double, max: Double): ContinuousPattern {
        return ContinuousPattern(
            getValue = { t ->
                val value = getValue(t)
                // Normalize bipolar (-1..1) to unipolar (0..1)
                val normalized = (value + 1.0) / 2.0

                (min + (normalized * (max - min)))
                // .also {
                // println("$t | $value | $min | $max -> $it")
                // }
            },
        )
    }
}
