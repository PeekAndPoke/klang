package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Atomic Pattern: Represents a single event that repeats every cycle (0, 1, 2...).
 * Used for basic primitives like `note("c3")`.
 */
internal class AtomicPattern(val data: VoiceData) : StrudelPattern.Fixed {
    companion object {
        val pure = AtomicPattern(VoiceData.empty)
    }

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        val startCycle = floor(from).toInt()
        val endCycle = ceil(to).toInt()
        val events = mutableListOf<StrudelPatternEvent>()

        for (i in startCycle until endCycle) {
            val begin = i.toDouble()
            // Strudel events are usually triggered if their start time is within the query arc.
            if (begin >= from || begin < to) {
                events.add(
                    StrudelPatternEvent(
                        begin = begin,
                        end = begin + 1.0, // Default duration is 1 cycle
                        dur = 1.0,
                        data = data
                    )
                )
            }
        }

        return events
    }
}
