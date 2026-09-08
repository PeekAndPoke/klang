/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Segments a pattern based on a control pattern that determines the number of segments per timespan.
 *
 * For each event in the control pattern, divides that event's span into n equal slices and samples
 * the source pattern at each slice. A slice is a new event: its whole IS the slice, so
 * `"0".segment(4).note()` plays four notes and `"c e g a b c d e".seg(4)` plays the four notes under
 * the slice starts. A source event that merely overlaps a slice without starting in it comes back
 * as a non-onset fragment. For a static n this is Strudel's `segment` (`struct(pure(true).fast(n))`);
 * for a patterned n Strudel keeps n slices per CYCLE, whereas here each control event's span gets
 * n slices (`sine.segment("2 4")` yields 2 + 4 slices, not 3).
 *
 * The slices are the structure, so [numSteps] counts them (see there for a patterned n) and [weight]
 * is 1, as for `struct`; the source keeps only its cycle length.
 *
 * @param source The pattern to segment
 * @param nProvider Control value provider for the number of segments
 */
internal class SegmentPattern(
    val source: SprudelPattern,
    val nProvider: ControlValueProvider,
) : SprudelPattern {
    companion object {
        /**
         * Create a SegmentPattern with a control pattern for n.
         */
        fun control(source: SprudelPattern, nPattern: SprudelPattern): SegmentPattern {
            return SegmentPattern(
                source = source,
                nProvider = ControlValueProvider.Pattern(nPattern)
            )
        }
    }

    override val weight: Double get() = 1.0

    /**
     * n when the first cycle is one control event covering exactly that cycle (`seg(4)` is four equal
     * steps, like `"x x x x"`); otherwise the control's own step count, because unequal slices are
     * subdivisions of the control's steps, not steps (`seg("2 4")` is `"[x x] [x x x x]"`, two steps).
     */
    override val numSteps: Double? by lazy {
        val firstCycle = nProvider.queryEvents(CycleTime.ZERO, CycleTime.ONE, QueryContext())
        val single = firstCycle.singleOrNull()

        if (single != null && single.part.begin == CycleTime.ZERO && single.part.end == CycleTime.ONE) {
            (single.data.value?.asInt ?: 1).coerceAtLeast(0).toDouble()
        } else {
            when (nProvider) {
                is ControlValueProvider.Pattern -> nProvider.pattern.numSteps
                is ControlValueProvider.Static -> 1.0
            }
        }
    }

    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val nEvents = nProvider.queryEvents(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1
            if (n <= 0) continue

            val duration = nEvent.part.duration
            val base = nEvent.part.begin

            // Create n slices with absolute boundaries so the last slice ends exactly at base+duration
            // (no cumulative-rounding gap for n that don't divide the tick grid).
            for (i in 0 until n) {
                val sliceBegin = base + duration.scaleBy(i.toDouble() / n)
                val sliceEnd = base + duration.scaleBy((i + 1).toDouble() / n)

                // Leaves answer a query with their full part (an atom queried at a point still
                // reports its whole cycle), so the slices must be checked against the query arc
                // here: a point query at 0.5 into segment(2) must yield the SECOND slice, not the
                // first one of the cycle. Without this, `sampleAt` (every source-structured join)
                // read the first slice for every onset.
                if (sliceEnd <= from || sliceBegin >= to) {
                    continue
                }

                // Query source for the full slice, so a continuous source is read at the slice
                // start regardless of where the query arc begins.
                val sourceEvents = source.queryArcContextual(sliceBegin, sliceEnd, ctx)

                val sliceSpan = CycleTimeSpan(sliceBegin, sliceEnd)

                for (sourceEvent in sourceEvents) {
                    // Clip the source event to the slice; the slice becomes the whole (see the class KDoc)
                    val clippedPart = sourceEvent.part.clipTo(sliceSpan) ?: continue

                    result.add(sourceEvent.copy(part = clippedPart, whole = sliceSpan))
                }
            }
        }

        return result
    }
}
