package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Handles structure combination logic (In/Out) with optional filtering.
 *
 * - [mode] determines which pattern provides the structure (In = Source, Out = Other).
 * - [filterByTruthiness] determines if we check the value of the 'other' pattern (true = keepif, false = keep).
 */
internal class StructurePattern(
    val source: StrudelPattern,
    val other: StrudelPattern,
    val mode: Mode,
    val filterByTruthiness: Boolean,
) : StrudelPattern {

    enum class Mode {
        In,  // Source provides structure (Mask behavior)
        Out  // Other provides structure (Struct behavior)
    }

    override val weight: Double = if (mode == Mode.In) source.weight else other.weight

    override val steps: Rational?
        get() = when (mode) {
            Mode.In -> source.steps
            Mode.Out -> other.steps
        }

    override fun estimateCycleDuration(): Rational {
        return when (mode) {
            Mode.In -> source.estimateCycleDuration()
            Mode.Out -> other.estimateCycleDuration()
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return when (mode) {
            Mode.In -> queryIn(from, to, ctx)
            Mode.Out -> queryOut(from, to, ctx)
        }
    }

    /**
     * In Mode (mask/maskAll): The source provides the structure.
     * We iterate over source events and sample the 'other' pattern at the midpoint to decide inclusion.
     */
    private fun queryIn(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = createEventList()

        // Use a tiny epsilon for point queries to ensure we catch events in discrete patterns
        val epsilon = 1e-6.toRational()

        for (sourceEvent in sourceEvents) {
            val mid = (sourceEvent.begin + sourceEvent.end) / Rational(2)

            // Sample the other pattern at the midpoint.
            val otherEvents = other.queryArcContextual(mid, mid + epsilon, ctx)

            val keep = if (filterByTruthiness) {
                // keepif: keep only if overlapping event is truthy
                otherEvents.any { it.data.isTruthy() }
            } else {
                // keep: keep if any overlapping event exists
                otherEvents.isNotEmpty()
            }

            if (keep) {
                result.add(sourceEvent)
            }
        }
        return result
    }

    /**
     * Out Mode (struct/structAll): The 'other' pattern provides the structure.
     * We behave like an intersection: clipping source events to the mask's duration.
     */
    private fun queryOut(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val otherEvents = other.queryArcContextual(from, to, ctx)
        if (otherEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (maskEvent in otherEvents) {
            // If filtering by truthiness, skip falsy mask events
            if (filterByTruthiness && !maskEvent.data.isTruthy()) continue

            // Query the source for the duration of this specific mask event
            val sourceEvents = source.queryArcContextual(maskEvent.begin, maskEvent.end, ctx)

            for (sourceEvent in sourceEvents) {
                // Intersection logic: clip source event to mask event
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

    private fun StrudelVoiceData.isTruthy(): Boolean {
        val noteStr = note ?: ""
        // Use the VoiceValue truthiness logic (which now correctly handles 0.0 vs 1.0)
        val valueTruthy = value?.isTruthy() ?: false
        val noteTruthy = noteStr.isNotEmpty() && noteStr != "~" && noteStr != "0" && noteStr != "false"
        return valueTruthy || noteTruthy
    }
}
