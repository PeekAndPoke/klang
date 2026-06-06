package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData

/**
 * Provides control values either as static constants or from control patterns.
 *
 * This interface allows unified handling of both static and pattern-based parameters,
 * reducing code duplication and ensuring consistent behavior.
 */
sealed interface ControlValueProvider {

    companion object {
        val DEFAULT_EPSILON = CycleTime.ofCycles(0.0001)
    }

    /**
     * Static control value that doesn't change over time.
     */
    data class Static(
        val value: SprudelVoiceValue,
        val location: SourceLocation? = null,
    ) : ControlValueProvider {
        companion object {
            val ONE = Static(1.0.asVoiceValue())
        }

        override fun query(
            from: CycleTime,
            to: CycleTime,
            ctx: SprudelPattern.QueryContext,
        ): SprudelVoiceValue {
            return value
        }

        override fun queryEvents(
            from: CycleTime,
            to: CycleTime,
            ctx: SprudelPattern.QueryContext,
        ): List<SprudelPatternEvent> {
            val timeSpan = CycleTimeSpan(begin = from, end = to)

            return listOf(
                SprudelPatternEvent(
                    part = timeSpan,
                    whole = timeSpan,
                    data = createSprudelVoiceData().also { it.value = value },
                    sourceLocations = location?.asChain()
                )
            )
        }
    }

    /**
     * Pattern-based control value that can change over time.
     */
    data class Pattern(val pattern: SprudelPattern) : ControlValueProvider {
        override fun query(
            from: CycleTime,
            to: CycleTime,
            ctx: SprudelPattern.QueryContext,
        ): SprudelVoiceValue? {
            val events = pattern.queryArcContextual(from, to, ctx)
            // Return the first event's value, or null if no events
            return events.firstOrNull()?.data?.value
        }

        override fun queryEvents(
            from: CycleTime,
            to: CycleTime,
            ctx: SprudelPattern.QueryContext,
        ): List<SprudelPatternEvent> {
            return pattern.queryArcContextual(from, to, ctx)
        }
    }

    /**
     * Query the control value for the given time range.
     *
     * @param from Start of time range
     * @param to End of time range
     * @param ctx Query context
     * @return The control value, or null if no value is available (for patterns)
     */
    fun query(
        from: CycleTime,
        to: CycleTime = from + DEFAULT_EPSILON,
        ctx: SprudelPattern.QueryContext,
    ): SprudelVoiceValue?

    /**
     * Query control events for the given time range.
     *
     * Static providers return a single event covering [from, to].
     * Pattern providers return their underlying events.
     */
    fun queryEvents(
        from: CycleTime,
        to: CycleTime = from + DEFAULT_EPSILON,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent>
}
