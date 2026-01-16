package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Atomic Pattern: Represents a single event that repeats every cycle (0, 1, 2...).
 * Used for basic primitives like `note("c3")`.
 */
internal class AtomicPattern(val data: VoiceData) : StrudelPattern.FixedWeight {
    companion object {
        /**
         * AtomicPattern that produces events with empty VoiceData.
         */
        val pure = AtomicPattern(VoiceData.empty)

        /**
         * Creates an AtomicPattern the produces events with the given value set as VoiceValue.
         */
        fun value(value: Any?) = AtomicPattern(VoiceData.empty.copy(value = value?.asVoiceValue()))
    }

    override val steps: Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
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
