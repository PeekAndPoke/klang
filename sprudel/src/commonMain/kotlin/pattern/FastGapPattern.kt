/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

/**
 * Speeds up a pattern like fast, but plays it only once per cycle in the compressed time, leaving a gap.
 *
 * For example, fastGap(2) compresses the pattern into the first half of each cycle,
 * leaving the second half silent.
 *
 * This is equivalent to compress(0, 1/factor).
 *
 * For static values, applies uniform fastGap across the entire pattern.
 * For control patterns, segments the time range and applies different factors to each segment.
 *
 * @param source The source pattern to compress
 * @param factorProvider Control value provider for the factor
 */
internal class FastGapPattern(
    val source: SprudelPattern,
    val factorProvider: ControlValueProvider,
) : SprudelPattern {
    companion object {
        /**
         * Create a FastGapPattern with a static factor value.
         */
        fun static(source: SprudelPattern, factor: Double): FastGapPattern {
            return FastGapPattern(
                source = source,
                factorProvider = ControlValueProvider.Static(factor.asVoiceValue())
            )
        }
    }

    override val weight: Double get() = source.weight

    override val numSteps: Double? get() = source.numSteps

    override fun estimateCycleDuration(): Double {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val factorEvents = factorProvider.queryEvents(from, to, ctx)
        if (factorEvents.isEmpty()) return source.queryArcContextual(from, to, ctx)

        val result = createEventList()

        for (factorEvent in factorEvents) {
            // Read the factor as a Double directly (it is already stored as a value)
            // — no per-query reconstruction needed.
            val factor = factorEvent.data.value?.asDouble ?: 1.0
            val events = queryWithFactor(factorEvent.part.begin, factorEvent.part.end, ctx, factor)
            result.addAll(events)
        }

        return result
    }

    private fun queryWithFactor(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
        factor: Double,
    ): List<SprudelPatternEvent> {
        // Handle edge case where factor <= 0
        if (factor <= 0.0) {
            return emptyList()
        }

        // If factor is 1.0, just return the source as-is
        if (factor == 1.0) {
            return source.queryArcContextual(from, to, ctx)
        }

        val spanCycles = 1.0 / factor      // compressed-region width as a fraction of a cycle
        val spanDuration = CycleTime.ofCycles(spanCycles)
        val result = createEventList()

        // Determine which cycles we need to query
        for (cycle in calculateCycleBounds(from, to)) {
            val cycleStart = CycleTime.ofCycleIndex(cycle)

            // The compressed region in this cycle: [cycle, cycle + 1/factor)
            val compressedStart = cycleStart
            val compressedEnd = cycleStart + spanDuration

            // Check if our query range intersects with the compressed region
            if (!cycleRegionIntersects(from, to, compressedStart, compressedEnd)) {
                continue // No intersection
            }

            // Query the source pattern for the full cycle (0 to 1)
            val sourceEvents = source.queryArcContextual(cycleStart, cycleStart + CycleTime.ONE, ctx)

            // Map each event from source cycle [0,1) to compressed region [start, start+span)
            for (ev in sourceEvents) {
                val (mappedPart, mappedWhole) = mapEventTimeBySpan(ev, cycleStart, compressedStart, spanCycles)

                // Only include if it intersects with our query range
                if (hasOverlap(mappedPart.begin, mappedPart.end, from, to)) {
                    result.add(
                        ev.copy(
                            part = mappedPart,
                            whole = mappedWhole
                        )
                    )
                }
            }
        }

        return result
    }
}
