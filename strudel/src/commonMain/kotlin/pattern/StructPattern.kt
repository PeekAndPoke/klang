package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Structures the [source] pattern according to the [structure] pattern.
 *
 * For every event in the [structure] that has the note "x", the [source] pattern
 * is queried for that specific time arc.
 */
internal class StructPattern(
    val source: StrudelPattern,
    val structure: StrudelPattern,
) : StrudelPattern {

    // Struct patterns usually preserve the weight of the structure's rhythm
    override val weight: Double get() = structure.weight

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        // 1. Get the rhythmic structure
        val structEvents = structure.queryArc(from, to)
        if (structEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (structEvent in structEvents) {
            // In Strudel, 'x' marks the active steps in a struct
            if (structEvent.data.note == "x") {
                // 2. Query the source for the duration of this specific structural event
                val sourceEvents = source
                    .queryArc(structEvent.begin, structEvent.end)
//                    .take(1)

                for (sourceEvent in sourceEvents) {
                    // 3. Clip the source event to the boundaries of the structural step
//                    val intersectionBegin = maxOf(sourceEvent.begin, structEvent.begin)
//                    val intersectionEnd = minOf(sourceEvent.end, structEvent.end)
//
//                    if (intersectionBegin < intersectionEnd) {
                    result.add(
                        sourceEvent.copy(
                            begin = structEvent.begin,
                            end = structEvent.end,
                            dur = structEvent.dur,
                        )
                    )
//                    }
                }
            }
        }

        return result
    }
}
