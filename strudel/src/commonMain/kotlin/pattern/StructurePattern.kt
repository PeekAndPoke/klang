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

    override val numSteps: Rational?
        get() = when (mode) {
            Mode.In -> source.numSteps
            Mode.Out -> other.numSteps
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
        // Optimization: Inline bind logic to avoid lambda allocation and ReinterpretPattern overhead
        val maskEvents = other.queryArcContextual(from, to, ctx)
        val result = createEventList()

        for (maskEvent in maskEvents) {
            // If filtering by truthiness, skip falsy mask events
            if (filterByTruthiness && !maskEvent.data.isTruthy()) {
                continue
            }

            // Intersect query arc with mask event
            val intersectStart = maxOf(from, maskEvent.begin)
            val intersectEnd = minOf(to, maskEvent.end)

            if (intersectEnd <= intersectStart) continue

            // Query source pattern within the mask event's duration
            val sourceEvents = source.queryArcContextual(intersectStart, intersectEnd, ctx)

            for (sourceEvent in sourceEvents) {
                // Clip source event to mask event boundaries
                val clippedBegin = maxOf(sourceEvent.begin, maskEvent.begin)
                val clippedEnd = minOf(sourceEvent.end, maskEvent.end)

                if (clippedEnd > clippedBegin) {
                    // Create the final event, merging source data with mask location
                    // We only copy if boundaries changed or we need to add location
                    val finalEvent =
                        if (clippedBegin != sourceEvent.begin || clippedEnd != sourceEvent.end || maskEvent.sourceLocations != null) {
                            sourceEvent.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedEnd - clippedBegin
                            ).prependLocations(maskEvent.sourceLocations)
                        } else {
                            sourceEvent
                        }

                    result.add(finalEvent)
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
