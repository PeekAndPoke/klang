package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Provides control values either as static constants or from control patterns.
 *
 * This interface allows unified handling of both static and pattern-based parameters,
 * reducing code duplication and ensuring consistent behavior.
 */
sealed interface ControlValueProvider {

    companion object {
        val DEFAULT_EPSILON = Rational(0.0001)
    }

    /**
     * Static control value that doesn't change over time.
     */
    data class Static(val value: StrudelVoiceValue) : ControlValueProvider {
        override fun query(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): StrudelVoiceValue {
            return value
        }
    }

    /**
     * Pattern-based control value that can change over time.
     */
    data class Pattern(val pattern: StrudelPattern) : ControlValueProvider {
        override fun query(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): StrudelVoiceValue? {
            val events = pattern.queryArcContextual(from, to, ctx)
            // Return the first event's value, or null if no events
            return events.firstOrNull()?.data?.value
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
        from: Rational,
        to: Rational = from + DEFAULT_EPSILON,
        ctx: StrudelPattern.QueryContext,
    ): StrudelVoiceValue?
}
