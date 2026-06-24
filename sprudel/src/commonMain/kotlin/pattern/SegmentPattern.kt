/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

/**
 * Segments a pattern based on a control pattern that determines the number of segments per timespan.
 *
 * For each event in the control pattern, divides that timespan into n equal slices
 * and samples the source pattern at each slice.
 *
 * For static values, this is only used with control patterns since static segmentation
 * is handled via struct(x.fast(n)) in the lang layer.
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
         * Create a SegmentPattern with a static n value.
         * Note: This is rarely used directly; static segmentation is usually done via struct(x.fast(n)).
         */
        fun static(source: SprudelPattern, n: Int): SegmentPattern {
            return SegmentPattern(
                source = source,
                nProvider = ControlValueProvider.Static((n).asVoiceValue())
            )
        }

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

    override val weight: Double get() = source.weight

    override val numSteps: Double? get() = source.numSteps

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

                // Query source for this slice
                val sourceEvents = source.queryArcContextual(sliceBegin, sliceEnd, ctx)

                for (sourceEvent in sourceEvents) {
                    // Clip source event to slice boundaries
                    val sliceSpan = CycleTimeSpan(sliceBegin, sliceEnd)
                    val clippedPart = sourceEvent.part.clipTo(sliceSpan)

                    if (clippedPart != null) {
                        result.add(
                            sourceEvent.copy(
                                part = clippedPart
                                // Preserve whole unchanged
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
