/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import kotlin.math.floor

/**
 * Applies swing timing to a source pattern by shifting and stretching events
 * based on their position within each subdivision pair.
 *
 * Unlike the previous `inside(n, lateInCycle().stretchBy())` approach, this pattern
 * operates directly on event times without time-domain zooming, avoiding amplification
 * artifacts at large cycle numbers.
 *
 * Each cycle is divided into [n] subdivisions. Within each subdivision:
 * - Events in the first half: no shift, duration stretched by (1 + swing)
 * - Events in the second half: shifted later, duration compressed by (1 - swing)
 *
 * @param source The pattern to apply swing to
 * @param swing Swing amount (-1..1). 1/3 is classic jazz swing. 0 = no swing. Negative = reverse swing.
 * @param n Number of subdivisions per cycle (typically 1, 2, 4, 8)
 */
internal class SwingPattern(
    private val source: SprudelPattern,
    private val swing: Double,
    private val n: Double,
) : SprudelPattern {

    override val weight: Double get() = source.weight
    override val numSteps: Double? get() = source.numSteps
    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        if (swing == 0.0 || n <= 0.0) {
            return source.queryArcContextual(from, to, ctx)
        }

        val events = source.queryArcContextual(from, to, ctx)
        if (events.isEmpty()) return events

        // Duration of one subdivision in cycle time
        val subdivDuration = CycleTime.ofCycles(1.0 / n)

        return events.map { event ->
            if (swing > 0.0) {
                applySwingPositive(event, subdivDuration)
            } else {
                applySwingNegative(event, subdivDuration)
            }
        }
    }

    /** Finds the start of the subdivision containing [onsetInCycle]. */
    private fun subdivStartOf(onsetInCycle: CycleTime, subdivDuration: CycleTime): CycleTime {
        val k = floor(onsetInCycle.ratioTo(subdivDuration)).toInt()
        return subdivDuration * k
    }

    /** Positive swing: stretch first-half events, shift+compress second-half events */
    private fun applySwingPositive(event: SprudelPatternEvent, subdivDuration: CycleTime): SprudelPatternEvent {
        // Find which subdivision this event's onset is in
        val onsetInCycle = event.whole.begin.fracOfCycle()
        val subdivStart = subdivStartOf(onsetInCycle, subdivDuration)
        val posInSubdiv = onsetInCycle - subdivStart
        val subdivHalf = subdivDuration.divBy(2.0)

        // Is the event in the first or second half of its subdivision?
        val isSecondHalf = posInSubdiv >= subdivHalf

        val stretchFactor: Double
        val shift: CycleTime

        if (!isSecondHalf) {
            // First half: stretch, no shift
            stretchFactor = 1.0 + swing
            shift = CycleTime.ZERO
        } else {
            // Second half: compress, shift later
            stretchFactor = 1.0 - swing
            shift = subdivHalf.scaleBy(swing)
        }

        val newPartBegin = event.part.begin + shift
        val newPartEnd = newPartBegin + event.part.duration.scaleBy(stretchFactor)
        val newWholeBegin = event.whole.begin + shift
        val newWholeEnd = newWholeBegin + event.whole.duration.scaleBy(stretchFactor)

        return event.copy(
            part = CycleTimeSpan(newPartBegin, newPartEnd),
            whole = CycleTimeSpan(newWholeBegin, newWholeEnd),
        )
    }

    /** Negative swing: compress first-half events, shift+stretch second-half events (reverse swing) */
    private fun applySwingNegative(event: SprudelPatternEvent, subdivDuration: CycleTime): SprudelPatternEvent {
        val absSwing = -swing
        val onsetInCycle = event.whole.begin.fracOfCycle()
        val subdivStart = subdivStartOf(onsetInCycle, subdivDuration)
        val posInSubdiv = onsetInCycle - subdivStart
        val subdivHalf = subdivDuration.divBy(2.0)

        val isSecondHalf = posInSubdiv >= subdivHalf

        val stretchFactor: Double
        val shift: CycleTime

        if (!isSecondHalf) {
            // First half: compress, shift earlier (negative swing shrinks first half)
            stretchFactor = 1.0 - absSwing
            shift = CycleTime.ZERO
        } else {
            // Second half: stretch, shift earlier to fill the gap
            stretchFactor = 1.0 + absSwing
            shift = -subdivHalf.scaleBy(absSwing)
        }

        val newPartBegin = event.part.begin + shift
        val newPartEnd = newPartBegin + event.part.duration.scaleBy(stretchFactor)
        val newWholeBegin = event.whole.begin + shift
        val newWholeEnd = newWholeBegin + event.whole.duration.scaleBy(stretchFactor)

        return event.copy(
            part = CycleTimeSpan(newPartBegin, newPartEnd),
            whole = CycleTimeSpan(newWholeBegin, newWholeEnd),
        )
    }
}
