package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table based on a selector pattern and flattens with innerJoin.
 *
 * @param selector Pattern providing selection indices/keys
 * @param lookup Map of patterns to select from (keys can be Int for lists or String for maps)
 * @param modulo If true, wrap out-of-bounds indices; if false, clamp them
 * @param extractKey Function to extract the lookup key from a selector event value
 */
internal class PickInnerPattern(
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
        // Query the selector pattern to get selection events
        val selectorEvents = selector.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        // For each selector event, look up the corresponding pattern and query it
        for (selectorEvent in selectorEvents) {
            // Extract the key/index from the selector event's value
            val key: Any? = extractKey(selectorEvent.data.value, modulo, lookup.size)

            // Get the pattern from lookup
            val selectedPattern = if (key != null) lookup[key] else null
            if (selectedPattern == null) continue

            // Determine the intersection of the query arc and the selector event
            val intersectStart = maxOf(from, selectorEvent.begin)
            val intersectEnd = minOf(to, selectorEvent.end)

            if (intersectEnd <= intersectStart) continue

            // Standard innerJoin: query the pattern using the intersection time
            val selectedEvents = selectedPattern.queryArcContextual(
                intersectStart,
                intersectEnd,
                ctx
            )

            // Add all events from the selected pattern, clipping them to the selector event's timeframe
            for (event in selectedEvents) {
                // We must clip the event to the selector's window to match Strudel JS behavior.
                // The 'queryArcContextual' might return events that extend beyond the query window
                // if the pattern doesn't clip them itself.

                val clippedBegin = maxOf(event.begin, selectorEvent.begin)
                val clippedEnd = minOf(event.end, selectorEvent.end)

                if (clippedEnd > clippedBegin) {
                    // Only create a new event if it was actually clipped or needs copying
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
