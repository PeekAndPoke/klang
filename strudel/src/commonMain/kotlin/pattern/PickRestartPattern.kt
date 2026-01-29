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

            val intersectStart = maxOf(from, selectorEvent.begin)
            val intersectEnd = minOf(to, selectorEvent.end)

            if (intersectEnd <= intersectStart) continue

            // Restart: Map global time to local time relative to selector event start
            // Global: [intersectStart, intersectEnd]
            // Local:  [intersectStart - begin, intersectEnd - begin]

            val localStart = intersectStart - selectorEvent.begin
            val localEnd = intersectEnd - selectorEvent.begin

            val innerEvents = selectedPattern.queryArcContextual(localStart, localEnd, ctx)

            // Shift inner events back to global time
            for (innerEvent in innerEvents) {
                // We clip to the selector event duration (like pick/innerJoin)
                // because pickRestart usually implies the pattern lives within the event.
                // However, does pickRestart clip?
                // Strudel documentation doesn't explicitly say, but usually it behaves like 'pick' but with reset phase.

                // Shift back
                val globalBegin = innerEvent.begin + selectorEvent.begin
                val globalEnd = innerEvent.end + selectorEvent.begin

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
