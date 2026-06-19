package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Pattern that shifts events in time based on a control value provider.
 *
 * Unlike using late/early with _bind, this pattern:
 * - Samples the control pattern at query time
 * - Shifts both part AND whole together
 * - Avoids the clipping issues that arise with _bind semantics
 *
 * @param source The source pattern to shift
 * @param offsetProvider Provides the time offset value
 * @param factor Multiplier for the offset (1.0 for late, -1.0 for early)
 */
internal class TimeShiftPattern(
    private val source: SprudelPattern,
    private val offsetProvider: ControlValueProvider,
    private val factor: Double = 1.0,
) : SprudelPattern {

    override val weight: Double get() = source.weight
    override val numSteps: Double? get() = source.numSteps
    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        // 1. Query control events
        val controlEvents = offsetProvider
            .queryEvents(from - CycleTime.ONE, to + CycleTime.ONE, ctx)

        if (controlEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (controlEvent in controlEvents) {
            // Read the offset as a Double (in cycles), then snap it onto the tick grid as a CycleTime.
            val offsetCycles = (controlEvent.data.value?.asDouble ?: continue) * factor
            val offset = CycleTime.ofCycles(offsetCycles)

            // Determine the time window where this control event (offset) applies
            // We intersect the control event's part with the requested query range
            val outStart = from.coerceAtLeast(controlEvent.part.begin)
            val outEnd = to.coerceAtMost(controlEvent.part.end)

            if (outStart >= outEnd) continue

            // Determine the corresponding window in the source pattern
            // If output time is t, source time is t - offset
            val srcStart = outStart - offset
            val srcEnd = outEnd - offset

            val shifted = source
                .queryArcContextual(srcStart, srcEnd, ctx)
                .mapNotNull { ev ->
                    val shiftPart = ev.part.shift(offset)
                    val shiftWhole = ev.whole.shift(offset)

                    // Clip the shifted part to THIS query's output window [outStart, outEnd)
                    // (= [from, to) ∩ controlEvent.part). Source events carry their full step-span
                    // part (the codebase convention), so a step straddling a query boundary would
                    // otherwise be returned as a full onset by both adjacent windows — duplicating
                    // the hit when the engine queries cycle-by-cycle. Clipping here makes part.begin
                    // exceed whole.begin for any slice whose onset is outside this window, so each
                    // onset is emitted by exactly one window (and `whole` is left intact for timing).
                    val clippedPart = shiftPart.clipTo(outStart, outEnd) ?: return@mapNotNull null

                    ev.copy(part = clippedPart, whole = shiftWhole)
                }

            result.addAll(shifted)
        }

        return result.filter {
            hasOverlap(it.part.begin, it.part.end, from, to)
        }
    }
}
