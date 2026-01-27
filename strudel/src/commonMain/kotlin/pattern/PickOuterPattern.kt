package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table based on a selector pattern.
 * NOTE: Currently behaves like PickInnerPattern (clips events) to match JS behavior observed in tests.
 * TODO: Verify if/how pickOut differs from pick (outerJoin vs innerJoin)
 */
internal class PickOuterPattern(
    private val selector: StrudelPattern,
    private val lookup: Map<Any, StrudelPattern>,
    private val modulo: Boolean,
    private val extractKey: (Any?, Boolean, Int) -> Any?,
) : StrudelPattern {

    override val weight: Double get() = selector.weight
    override val steps: Rational? get() = selector.steps
    override fun estimateCycleDuration(): Rational = selector.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        val selectorEvents = selector.queryArcContextual(from, to, ctx)
        val result = createEventList()

        for (selectorEvent in selectorEvents) {
            val key: Any? = extractKey(selectorEvent.data.value, modulo, lookup.size)
            val selectedPattern = if (key != null) lookup[key] else null

            if (selectedPattern == null) continue

            val intersectStart = maxOf(from, selectorEvent.begin)
            val intersectEnd = minOf(to, selectorEvent.end)

            if (intersectEnd <= intersectStart) continue

            // Query the pattern using the intersection time
            val selectedEvents = selectedPattern.queryArcContextual(
                intersectStart,
                intersectEnd,
                ctx
            )

            // Add all events from the selected pattern, clipping them to the selector event's timeframe
            // This matches observed JS behavior where pickOut results are clipped to selector duration
            for (event in selectedEvents) {
                val clippedBegin = maxOf(event.begin, selectorEvent.begin)
                val clippedEnd = minOf(event.end, selectorEvent.end)

                if (clippedEnd > clippedBegin) {
                    if (clippedBegin != event.begin || clippedEnd != event.end) {
                        result.add(
                            event.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedEnd - clippedBegin,
                            )
                        )
                    } else {
                        result.add(event)
                    }
                }
            }
        }

        return result
    }
}
