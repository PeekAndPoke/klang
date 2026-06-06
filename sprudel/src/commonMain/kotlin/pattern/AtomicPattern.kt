package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData

/**
 * Atomic Pattern: Represents a single event that repeats every cycle (0, 1, 2...).
 * Used for basic primitives like `note("c3")`.
 *
 * @property data The voice data for this atom
 * @property sourceLocations Optional source location chain for live code highlighting
 */
internal class AtomicPattern(
    val data: SprudelVoiceData,
    val sourceLocations: SourceLocationChain? = null,
) : SprudelPattern.FixedWeight {
    companion object {
        /**
         * AtomicPattern that produces events with empty SprudelVoiceData.
         */
        val pure = AtomicPattern(createSprudelVoiceData())

        /**
         * Creates an AtomicPattern the produces events with the given value set as VoiceValue.
         */
        fun value(value: Any?) = AtomicPattern(createSprudelVoiceData().also { it.value = value?.asVoiceValue() })
    }

    override val numSteps: Double = 1.0

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val startCycle = from.cycleIndex()
        val endCycle = to.ceilToCycle().cycleIndex()
        val events = createEventList()

        for (i in startCycle until endCycle) {
            val begin = CycleTime.ofCycleIndex(i)
            // Emit when the atom's cycle-long span [begin, begin+1) overlaps the query arc.
            // (Note: a `begin >= from && …` form would wrongly drop an atom whose onset precedes
            // `from`, breaking point-sampling — see AtomicInfinitePattern, which uses the same test.)
            if (begin < to && begin + CycleTime.ONE > from) {
                val timeSpan = CycleTimeSpan(begin = begin, end = begin + CycleTime.ONE)

                events.add(
                    SprudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        // clone() so each emitted event owns its data — required for safe in-place
                        // mutation downstream (the stored `data` field must never be handed out shared).
                        data = data.clone(),
                        sourceLocations = sourceLocations
                    )
                )
            }
        }

        return events
    }
}
