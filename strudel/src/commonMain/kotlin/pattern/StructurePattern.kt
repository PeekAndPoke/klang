package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.sampleAt

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

        for (sourceEvent in sourceEvents) {
            val mid = (sourceEvent.part.begin + sourceEvent.part.end) / Rational(2)

            // Sample the other pattern at the midpoint
            val otherEvent = other.sampleAt(mid, ctx)

            val keep = if (filterByTruthiness) {
                // keepif: keep only if overlapping event is truthy
                otherEvent?.data?.isTruthy() == true
            } else {
                // keep: keep if any overlapping event exists
                otherEvent != null
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
            val intersectStart = maxOf(from, maskEvent.part.begin)
            val intersectEnd = minOf(to, maskEvent.part.end)

            if (intersectEnd <= intersectStart) continue

            // Query source pattern within the mask event's duration
            val sourceEvents = source.queryArcContextual(intersectStart, intersectEnd, ctx)

            for (sourceEvent in sourceEvents) {
                // Clip source event to mask event boundaries
                val clippedPart = sourceEvent.part.clipTo(maskEvent.part)

                if (clippedPart != null) {
                    // Create the final event with mask's whole
                    // JS Strudel semantics: struct "rebirths" events within mask boundaries
                    // so whole is set to mask boundaries, not preserved from source
                    val finalEvent = sourceEvent.copy(
                        part = clippedPart,
                        whole = maskEvent.whole  // Set to mask's whole (JS behavior)
                    ).prependLocations(maskEvent.sourceLocations)

                    result.add(finalEvent)
                }
            }
        }

        return result
    }
}
