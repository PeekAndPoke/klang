/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.common.math.bjorklund
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData

/**
 * Euclidean Morph Pattern: Morphs between Euclidean rhythm and even distribution.
 *
 * @param pulsesProvider Control value provider for the number of pulses
 * @param stepsProvider Control value provider for the number of steps
 * @param groovePattern Pattern controlling the morph factor (0=euclidean, 1=even)
 */
@Suppress("DuplicatedCode")
internal class EuclideanMorphPattern(
    val pulsesProvider: ControlValueProvider,
    val stepsProvider: ControlValueProvider,
    val groovePattern: SprudelPattern,
) : SprudelPattern {

    override val weight: Double get() = groovePattern.weight

    override val numSteps: Double?
        get() = if (stepsProvider is ControlValueProvider.Static) {
            (stepsProvider.value.asInt ?: 0).toDouble()
        } else {
            null
        }

    override fun estimateCycleDuration(): Double = 1.0

    companion object {
        /**
         * Create an EuclideanMorphPattern with static pulses/steps values and a groove pattern.
         */
        fun static(
            pulses: Int,
            steps: Int,
            groovePattern: SprudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Static((pulses).asVoiceValue()),
                stepsProvider = ControlValueProvider.Static((steps).asVoiceValue()),
                groovePattern = groovePattern
            )
        }

        /**
         * Create an EuclideanMorphPattern with control patterns for pulses and steps.
         */
        fun control(
            pulsesPattern: SprudelPattern,
            stepsPattern: SprudelPattern,
            groovePattern: SprudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Pattern(pulsesPattern),
                stepsProvider = ControlValueProvider.Pattern(stepsPattern),
                groovePattern = groovePattern
            )
        }

        /**
         * Builds the morphed onset arcs for `by` in 0..1, where 0 keeps the Euclidean rhythm and
         * 1 spreads the same number of onsets perfectly evenly across the cycle.
         *
         * Each onset is placed at the linear interpolation between its Euclidean cycle position and
         * its evenly-spaced position, and gets a fixed gate length of one step.
         */
        internal fun calculateMorphedArcs(pulses: Int, steps: Int, by: Double): List<Pair<Double, Double>> {
            // Euclidean onsets: the cycle fraction of every set bit (its index over the step count).
            val euclideanRhythm = bjorklund(pulses, steps)
            val stepCount = euclideanRhythm.size
            val euclideanOnsets = euclideanRhythm.mapIndexedNotNull { index, bit ->
                if (bit == 1) index.toDouble() / stepCount else null
            }

            // Even onsets: the same count of pulses distributed uniformly over the cycle.
            val evenOnsets = List(pulses) { it.toDouble() / pulses }

            // Fixed gate length of a single step.
            val gate = 1.0 / steps

            return euclideanOnsets.zip(evenOnsets).map { (euclideanPos, evenPos) ->
                val start = euclideanPos + by * (evenPos - euclideanPos)
                start to (start + gate)
            }
        }
    }

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        // Query groove pattern for morph factor over time
        val grooveEvents = groovePattern.queryArcContextual(from, to, ctx)
        if (grooveEvents.isEmpty()) return emptyList()

        val pulsesEvents = pulsesProvider.queryEvents(from, to, ctx)
        val stepsEvents = stepsProvider.queryEvents(from, to, ctx)
        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty()) return emptyList()

        val result = createEventList()

        // For each groove event, sample pulses/steps and generate morphed rhythm
        for (grooveEvent in grooveEvents) {
            // Get pulses and steps values for this groove event's timespan
            val pulses = pulsesEvents
                .firstOrNull { it.part.begin < grooveEvent.part.end && it.part.end > grooveEvent.part.begin }
                ?.data?.value?.asInt ?: 0

            val steps = stepsEvents
                .firstOrNull { it.part.begin < grooveEvent.part.end && it.part.end > grooveEvent.part.begin }
                ?.data?.value?.asInt ?: 0

            if (pulses <= 0 || steps <= 0) continue

            val perc = grooveEvent.data.value?.asDouble ?: 0.0

            // Generate the morphed rhythm arcs for these values
            val arcs = calculateMorphedArcs(pulses, steps, perc)

            // Generate events from arcs that intersect with the groove event
            val startCycle = grooveEvent.part.begin.cycleIndex()
            val endCycle = grooveEvent.part.end.ceilToCycle().cycleIndex()

            for (cycle in startCycle until endCycle) {
                val cycleStart = CycleTime.ofCycleIndex(cycle)
                val cycleEnd = cycleStart + CycleTime.ONE

                // Effective window for this cycle is intersection of cycle and groove event
                val windowStart = grooveEvent.part.begin.coerceAtLeast(cycleStart)
                val windowEnd = grooveEvent.part.end.coerceAtMost(cycleEnd)

                if (windowStart >= windowEnd) continue

                for (arc in arcs) {
                    // Arc is relative to cycle start (0..1)
                    val arcStartAbs = cycleStart + CycleTime.ofCycles(arc.first)
                    val arcEndAbs = cycleStart + CycleTime.ofCycles(arc.second)

                    // Intersect arc with the query window (which is constrained by groove event)
                    val intersectStart = windowStart.coerceAtLeast(arcStartAbs)
                    val intersectEnd = windowEnd.coerceAtMost(arcEndAbs)

                    if (intersectEnd > intersectStart) {
                        val timeSpan = CycleTimeSpan(begin = intersectStart, end = intersectEnd)

                        result.add(
                            SprudelPatternEvent(
                                part = timeSpan,
                                whole = timeSpan,
                                data = createSprudelVoiceData { value = 1.asVoiceValue() }
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
