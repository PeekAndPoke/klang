package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
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

    @Suppress("DuplicatedCode")
    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // 1. Get the rhythmic structure
        val structEvents = structure.queryArcContextual(from, to, ctx)
        if (structEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (maskEvent in structEvents) {
            val isTruthy = maskEvent.data.note == "x"

            if (isTruthy) {
                val sourceEvents = source.queryArcContextual(maskEvent.begin, maskEvent.end, ctx)

                for (sourceEvent in sourceEvents) {
                    val intersectionBegin = maxOf(sourceEvent.begin, maskEvent.begin)
                    val intersectionEnd = minOf(sourceEvent.end, maskEvent.end)
                    val intersectionDur = intersectionEnd - intersectionBegin

                    if (intersectionBegin < intersectionEnd) {
                        result.add(
                            sourceEvent.copy(
                                begin = intersectionBegin,
                                end = intersectionEnd,
                                dur = intersectionDur,
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
