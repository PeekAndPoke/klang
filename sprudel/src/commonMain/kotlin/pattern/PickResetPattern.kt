package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * Pattern that selects from a lookup table and resets the selected pattern at the event start.
 * "Reset" means the inner pattern cycle start is aligned with the outer event cycle position.
 */
internal class PickResetPattern(
    private val selector: SprudelPattern,
    private val lookup: Map<Any, SprudelPattern>,
    private val modulo: Boolean,
    private val extractKey: (SprudelVoiceData, Boolean, Int) -> Any?,
) : SprudelPattern {

    override val weight: Double get() = selector.weight
    override val numSteps: Rational? get() = selector.numSteps
    override fun estimateCycleDuration(): Rational = selector.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        val selectorEvents = selector.queryArcContextual(from, to, ctx)
        val result = mutableListOf<SprudelPatternEvent>()

        for (selectorEvent in selectorEvents) {
            val key: Any? = extractKey(selectorEvent.data, modulo, lookup.size)
            val selectedPattern = if (key != null) lookup[key] else null
            if (selectedPattern == null) continue

            val intersectStart = maxOf(from, selectorEvent.part.begin)
            val intersectEnd = minOf(to, selectorEvent.part.end)

            if (intersectEnd <= intersectStart) continue

            // Reset: Map global time to local time relative to selector event cycle pos
            // Global: [intersectStart, intersectEnd]
            // NOTE: Using whole.begin (onset time) for cycle position calculation.
            // This matches musical semantics. See accessor-replacement notes.
            val eventBegin = selectorEvent.whole.begin
            val shift = eventBegin.frac()
            val localStart = intersectStart - shift
            val localEnd = intersectEnd - shift

            val innerEvents = selectedPattern.queryArcContextual(localStart, localEnd, ctx)

            // Shift inner events back to global time
            for (innerEvent in innerEvents) {
                // Shift back to global time
                val shiftedPart = innerEvent.part.shift(shift)
                val shiftedWhole = innerEvent.whole.shift(shift)

                // Clip to selector event
                val clippedPart = shiftedPart.clipTo(selectorEvent.part)
                val clippedWhole = shiftedWhole.clipTo(selectorEvent.whole)

                if (clippedPart != null && clippedWhole != null) {
                    // Intersect whole with selector event's whole (matches JS behavior)
                    result.add(innerEvent.copy(part = clippedPart, whole = clippedWhole))
                }
            }
        }

        return result
    }
}
