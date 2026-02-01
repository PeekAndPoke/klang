package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table based on a selector pattern and flattens with innerJoin.
 *
 * @param selector Pattern providing selection indices/keys
 * @param lookup Map of patterns to select from (keys can be Int for lists or String for maps)
 * @param modulo If true, wrap out-of-bounds indices; if false, clamp them
 * @param extractKey Function to extract the lookup key from a selector event value
 */
internal class PickSqueezePattern(
    private val selector: StrudelPattern,
    private val lookup: Map<Any, StrudelPattern>,
    private val modulo: Boolean,
    private val extractKey: (StrudelVoiceData, Boolean, Int) -> Any?,
) : StrudelPattern {

    override val weight: Double get() = selector.weight
    override val numSteps: Rational? get() = selector.numSteps
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
            val key: Any? = extractKey(selectorEvent.data, modulo, lookup.size)

            // Get the pattern from lookup
            val selectedPattern = if (key != null) lookup[key] else null
            if (selectedPattern == null) continue

            // Determine the intersection of the query arc and the selector event
            val intersectStart = maxOf(from, selectorEvent.part.begin)
            val intersectEnd = minOf(to, selectorEvent.part.end)

            if (intersectEnd <= intersectStart) continue

            // Squeeze the inner pattern into the selector event's duration.
            // We map the outer time 't' to inner time 't'' such that:
            // selectorEvent.part.begin -> 0
            // selectorEvent.part.end -> 1 (assuming 1 cycle for inner pattern)

            val duration = selectorEvent.part.duration
            if (duration == Rational.ZERO) continue

            val innerStart = (intersectStart - selectorEvent.part.begin) / duration
            val innerEnd = (intersectEnd - selectorEvent.part.begin) / duration

            val innerEvents = selectedPattern.queryArcContextual(innerStart, innerEnd, ctx)

            // Map inner events back to outer time
            for (innerEvent in innerEvents) {
                val scaledPart = innerEvent.part.scale(duration).shift(selectorEvent.part.begin)
                val scaledWhole = innerEvent.whole?.scale(duration)?.shift(selectorEvent.part.begin) ?: scaledPart

                result.add(
                    innerEvent.copy(
                        part = scaledPart,
                        whole = scaledWhole
                    )
                )
            }
        }

        return result
    }
}
