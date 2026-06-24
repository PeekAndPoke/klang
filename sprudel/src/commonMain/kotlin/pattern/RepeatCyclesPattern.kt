/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import kotlin.math.floor

/**
 * Repeats each cycle of the source pattern [n] times.
 *
 * Example:
 * source: Cycle 0="a", Cycle 1="b"
 * repeatCycles(2): "a", "a", "b", "b"
 *
 * For static patterns (like note("c")), this acts as identity.
 */
class RepeatCyclesPattern(
    private val source: SprudelPattern,
    private val n: Double,
) : SprudelPattern.FixedWeight {

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        val result = mutableListOf<SprudelPatternEvent>()
        val nDouble = n

        // Iterate through each cycle in the output range
        var currentCycle = from.cycleIndex()
        val lastCycle = to.ceilToCycle().cycleIndex()

        while (currentCycle < lastCycle) {
            val cycleStart = CycleTime.ofCycleIndex(currentCycle)
            val cycleEnd = cycleStart + CycleTime.ONE

            // Calculate which source cycle corresponds to this output cycle
            // Source cycle = floor(current_output_cycle / n)
            val sourceCycleIndex = floor(currentCycle / nDouble).toInt()
            val sourceCycleStart = CycleTime.ofCycleIndex(sourceCycleIndex)

            // Overlap of current output cycle with query range
            val queryStart = from.coerceAtLeast(cycleStart)
            val queryEnd = to.coerceAtMost(cycleEnd)

            if (queryStart < queryEnd) {
                // Map query times to source times
                // Time within cycle is T - cycleStart
                // Source time = sourceCycleStart + (T - cycleStart)
                val mapToSource = { t: CycleTime -> sourceCycleStart + (t - cycleStart) }

                val sourceQueryStart = mapToSource(queryStart)
                val sourceQueryEnd = mapToSource(queryEnd)

                val events = source.queryArcContextual(sourceQueryStart, sourceQueryEnd, ctx)

                // Map events back to output time
                // T_out = cycleStart + (T_src - sourceCycleStart)
                events.forEach { event ->
                    val offset = cycleStart - sourceCycleStart
                    val shiftedPart = event.part.shift(offset)
                    val shiftedWhole = event.whole.shift(offset)

                    result.add(event.copy(part = shiftedPart, whole = shiftedWhole))
                }
            }

            currentCycle++
        }

        return result
    }

    override fun estimateCycleDuration(): Double {
        return source.estimateCycleDuration() * n
    }

    override val numSteps: Double? = source.numSteps

    companion object {
        /**
         * Creates a RepeatCyclesPattern from a control pattern.
         */
        fun control(
            source: SprudelPattern,
            repetitionsPattern: SprudelPattern,
        ): SprudelPattern {
            return object : SprudelPattern.FixedWeight {
                override fun queryArcContextual(
                    from: CycleTime,
                    to: CycleTime,
                    ctx: SprudelPattern.QueryContext,
                ): List<SprudelPatternEvent> {
                    val repsEvents = repetitionsPattern.queryArcContextual(from, from + CycleTime.ONE, ctx)
                    val repsValue = repsEvents.firstOrNull()?.data?.value?.asDouble ?: 1.0

                    return RepeatCyclesPattern(source, repsValue).queryArcContextual(from, to, ctx)
                }

                override fun estimateCycleDuration(): Double = source.estimateCycleDuration()
                override val numSteps: Double? = source.numSteps
            }
        }
    }
}
