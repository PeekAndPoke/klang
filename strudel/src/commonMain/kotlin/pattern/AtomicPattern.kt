package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Atomic Pattern: Represents a single event that repeats every cycle (0, 1, 2...).
 * Used for basic primitives like `note("c3")`.
 *
 * @property data The voice data for this atom
 * @property sourceLocations Optional source location chain for live code highlighting
 */
internal class AtomicPattern(
    val data: StrudelVoiceData,
    val sourceLocations: SourceLocationChain? = null,
) : StrudelPattern.FixedWeight {
    companion object {
        /**
         * AtomicPattern that produces events with empty StrudelVoiceData.
         */
        val pure = AtomicPattern(StrudelVoiceData.empty)

        /**
         * Creates an AtomicPattern the produces events with the given value set as VoiceValue.
         */
        fun value(value: Any?) = AtomicPattern(StrudelVoiceData.empty.copy(value = value?.asVoiceValue()))
    }

    override val numSteps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()
        val events = createEventList()

        for (i in startCycle until endCycle) {
            val begin = Rational(i)
            // Strudel events are usually triggered if their start time is within the query arc.
            if (begin >= from || begin < to) {
                val timeSpan = TimeSpan(begin = begin, end = begin + Rational.ONE)

                events.add(
                    StrudelPatternEvent(
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
