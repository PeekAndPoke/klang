package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent

/**
 * A pattern that hurries (speeds up) based on a control pattern.
 *
 * Hurry is like fast but also multiplies the speed parameter by the same factor.
 * For each control event, applies hurry with that factor value.
 *
 * @param source The pattern to hurry
 * @param factorPattern The control pattern providing factor values
 */
internal class HurryPatternWithControl(
    val source: StrudelPattern,
    val factorPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override val steps: io.peekandpoke.klang.strudel.math.Rational? get() = source.steps

    override fun queryArcContextual(
        from: io.peekandpoke.klang.strudel.math.Rational,
        to: io.peekandpoke.klang.strudel.math.Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val controlEvents = factorPattern.queryArcContextual(from, to, ctx)
        if (controlEvents.isEmpty()) return source.queryArcContextual(from, to, ctx)

        val result = mutableListOf<StrudelPatternEvent>()

        for (controlEvent in controlEvents) {
            val factor = controlEvent.data.value?.asDouble ?: 1.0

            // Apply hurry with this factor for this timespan
            val patternToQuery = if (factor <= 0.0 || factor == 1.0) {
                source
            } else {
                HurryPattern(source = source, factor = factor)
            }

            val events = patternToQuery.queryArcContextual(controlEvent.begin, controlEvent.end, ctx)
            result.addAll(events)
        }

        return result
    }
}
