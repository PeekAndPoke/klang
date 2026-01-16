package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * A pattern that modifies tempo (speed) based on a control pattern.
 *
 * For each event in the control pattern, applies tempo modification with the corresponding factor value.
 * The control pattern determines how fast or slow the inner pattern should be played.
 *
 * @param inner The pattern to modify
 * @param factorPattern The control pattern providing factor values
 * @param invertPattern If true, speeds up (fast); if false, slows down (slow)
 */
internal class TempoModifierPatternWithControl(
    val inner: StrudelPattern,
    val factorPattern: StrudelPattern,
    val invertPattern: Boolean,
) : StrudelPattern {
    override val weight: Double get() = inner.weight

    override val steps: Rational? get() = inner.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val factorEvents = factorPattern.queryArcContextual(from, to, ctx)
        if (factorEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (factorEvent in factorEvents) {
            val factor = factorEvent.data.value?.asDouble ?: 1.0

            // Apply tempo modification for this timespan
            val patternToQuery = TempoModifierPattern(inner, factor = factor, invertPattern = invertPattern)
            val events = patternToQuery.queryArcContextual(factorEvent.begin, factorEvent.end, ctx)
            result.addAll(events)
        }

        return result
    }
}
