package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Pattern that selects from a lookup table and resets the selected pattern at the event start.
 * "Reset" means the inner pattern cycle start is aligned with the outer event cycle position.
 */
internal class PickResetPattern(
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

            val intersectStart = maxOf(from, selectorEvent.begin)
            val intersectEnd = minOf(to, selectorEvent.end)

            if (intersectEnd <= intersectStart) continue

            // Reset: Map global time to local time relative to selector event cycle pos
            // Global: [intersectStart, intersectEnd]
            // Shift = selectorEvent.begin.frac()
            // Local:  [intersectStart - shift, intersectEnd - shift]

            val shift = selectorEvent.begin.frac()
            val localStart = intersectStart - shift
            val localEnd = intersectEnd - shift

            val innerEvents = selectedPattern.queryArcContextual(localStart, localEnd, ctx)

            // Shift inner events back to global time
            for (innerEvent in innerEvents) {
                // Shift back
                val globalBegin = innerEvent.begin + shift
                val globalEnd = innerEvent.end + shift

                // Clip to selector event
                val clippedBegin = maxOf(globalBegin, selectorEvent.begin)
                val clippedEnd = minOf(globalEnd, selectorEvent.end)

                if (clippedEnd > clippedBegin) {
                    if (clippedBegin != globalBegin || clippedEnd != globalEnd) {
                        result.add(
                            innerEvent.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedEnd - clippedBegin
                            )
                        )
                    } else {
                        result.add(innerEvent.copy(begin = globalBegin, end = globalEnd))
                    }
                }
            }
        }

        return result
    }
}
