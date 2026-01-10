package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

internal class KeepIfPattern(
    val source: StrudelPattern,
    val other: StrudelPattern,
    val structure: StructureMode,
) : StrudelPattern {

    enum class StructureMode {
        In,  // Source provides structure (Mask behavior)
        Out  // Other provides structure (Struct behavior)
    }

    override val weight: Double = if (structure == StructureMode.In) source.weight else other.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return when (structure) {
            StructureMode.In -> queryIn(from, to, ctx)
            StructureMode.Out -> queryOut(from, to, ctx)
        }
    }

    /**
     * mask() logic: The source provides the structure.
     * We iterate over source events and sample the mask (other) at the midpoint to decide inclusion.
     */
    private fun queryIn(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        // Use a tiny epsilon for point queries to satisfy discrete patterns that ignore zero-width arcs
        val epsilon = 1e-7.toRational()

        for (sourceEvent in sourceEvents) {
            val mid = (sourceEvent.begin + sourceEvent.end) / Rational(2)

            // Sample the mask at the midpoint.
            // We use a tiny interval [mid, mid + epsilon] to ensure discrete patterns (SequencePattern)
            // detect the overlap.
            val otherEvents = other.queryArcContextual(mid, mid + epsilon, ctx)

            if (otherEvents.any { it.data.isTruthy() }) {
                result.add(sourceEvent)
            }
        }
        return result
    }

    /**
     * struct() logic: The mask (other) provides the outer structure window.
     * We behave like an intersection: we find source events that overlap with truthy mask events
     * and clip them to the mask's duration.
     */
    private fun queryOut(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // 1. Get the rhythmic structure from the mask
        val otherEvents = other.queryArcContextual(from, to, ctx)
        if (otherEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (maskEvent in otherEvents) {
            // Only consider truthy mask events
            if (!maskEvent.data.isTruthy()) continue

            // 2. Query the source for the duration of this specific mask event
            val sourceEvents = source.queryArcContextual(maskEvent.begin, maskEvent.end, ctx)

            for (sourceEvent in sourceEvents) {
                // Intersection: The resulting event only exists where BOTH overlap.
                val newBegin = maxOf(maskEvent.begin, sourceEvent.begin)
                val newEnd = minOf(maskEvent.end, sourceEvent.end)

                if (newEnd > newBegin) {
                    result.add(
                        sourceEvent.copy(
                            begin = newBegin,
                            end = newEnd,
                            dur = newEnd - newBegin
                        )
                    )
                }
            }
        }
        return result
    }

    /** Helper extension for VoiceData truthiness */
    private fun VoiceData.isTruthy(): Boolean {
        val noteStr = note ?: ""
        val valueTruthy = value?.isTruthy() ?: false
        // In Strudel/Tidal: "0", "~", "false" are falsy.
        val noteTruthy = noteStr.isNotEmpty() && noteStr != "~" && noteStr != "0" && noteStr != "false"
        return valueTruthy || noteTruthy
    }
}
