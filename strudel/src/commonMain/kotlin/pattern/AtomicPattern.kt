package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Atomic Pattern: Represents a single event that repeats every cycle (0, 1, 2...).
 * Used for basic primitives like `note("c3")`.
 */
internal class AtomicPattern(val data: VoiceData) : StrudelPattern.FixedWeight {
    companion object {
        val pure = AtomicPattern(VoiceData.empty)
    }

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()
        val events = mutableListOf<StrudelPatternEvent>()

        for (i in startCycle until endCycle) {
            val begin = Rational(i)
            // Strudel events are usually triggered if their start time is within the query arc.
            if (begin >= from || begin < to) {
                events.add(
                    StrudelPatternEvent(
                        begin = begin,
                        end = begin + Rational.ONE, // Default duration is 1 cycle
                        dur = Rational.ONE,
                        data = data
                    )
                )
            }
        }

        return events
    }
}
