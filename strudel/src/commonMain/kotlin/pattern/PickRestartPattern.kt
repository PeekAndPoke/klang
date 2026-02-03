package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table and restarts the selected pattern at the event start.
 */
internal class PickRestartPattern(
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
        val selectorEvents = selector.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        for (selectorEvent in selectorEvents) {
            val key: Any? = extractKey(selectorEvent.data, modulo, lookup.size)
            val selectedPattern = if (key != null) lookup[key] else null
            if (selectedPattern == null) continue

            val intersectStart = maxOf(from, selectorEvent.part.begin)
            val intersectEnd = minOf(to, selectorEvent.part.end)

            if (intersectEnd <= intersectStart) continue

            // Restart: Map global time to local time relative to selector event start
            // Global: [intersectStart, intersectEnd]
            // Local:  [intersectStart - part.begin, intersectEnd - part.begin]

            val localStart = intersectStart - selectorEvent.part.begin
            val localEnd = intersectEnd - selectorEvent.part.begin

            val innerEvents = selectedPattern.queryArcContextual(localStart, localEnd, ctx)

            // Shift inner events back to global time
            for (innerEvent in innerEvents) {
                // Shift back to global time
                val shiftedPart = innerEvent.part.shift(selectorEvent.part.begin)
                val shiftedWhole = innerEvent.whole.shift(selectorEvent.part.begin)

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
