package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan

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
        val pure = AtomicPattern(SprudelVoiceData.empty)

        /**
         * Creates an AtomicPattern the produces events with the given value set as VoiceValue.
         */
        fun value(value: Any?) = AtomicPattern(SprudelVoiceData.empty.copy(value = value?.asVoiceValue()))
    }

    override val numSteps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()
        val events = createEventList()

        for (i in startCycle until endCycle) {
            val begin = Rational(i)
            // Sprudel events are usually triggered if their start time is within the query arc.
            if (begin >= from || begin < to) {
                val timeSpan = TimeSpan(begin = begin, end = begin + Rational.ONE)

                events.add(
                    SprudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = data,
                        sourceLocations = sourceLocations
                    )
                )
            }
        }

        return events
    }
}
