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
import io.peekandpoke.klang.sprudel.lang.struct
import kotlin.math.abs

/**
 * Euclidean Pattern: Applies a Euclidean rhythm to an inner pattern.
 * Syntax: `pattern(pulses, steps)` e.g., `bd(3,8)`
 *
 * This pattern acts as a gate (`struct`). It only allows the inner pattern to play
 * during the active steps of the Euclidean rhythm.
 * Events are strictly clipped to the step duration to ensure the rhythmic structure is preserved.
 *
 * @param inner The pattern to apply the Euclidean rhythm to
 * @param pulsesProvider Control value provider for the number of pulses
 * @param stepsProvider Control value provider for the number of steps
 * @param rotationProvider Control value provider for rotation offset (optional)
 * @param legato If true, pulses are held until the next pulse (no gaps)
 */
@Suppress("DuplicatedCode")
internal class EuclideanPattern(
    val inner: SprudelPattern,
    val pulsesProvider: ControlValueProvider,
    val stepsProvider: ControlValueProvider,
    val rotationProvider: ControlValueProvider?,
    val legato: Boolean = false,
) : SprudelPattern {

    override val weight: Double get() = inner.weight

    override val numSteps: Double?
        get() {
            return if (stepsProvider is ControlValueProvider.Static) {
                (stepsProvider.value.asInt ?: 0).toDouble()
            } else {
                inner.numSteps
            }
        }

    override fun estimateCycleDuration(): Double = 1.0

    companion object {
        /**
         * Create a EuclideanPattern with static values.
         */
        fun static(
            inner: SprudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
            legato: Boolean = false,
        ): EuclideanPattern {
            return EuclideanPattern(
                inner = inner,
                pulsesProvider = ControlValueProvider.Static((pulses).asVoiceValue()),
                stepsProvider = ControlValueProvider.Static((steps).asVoiceValue()),
                rotationProvider = ControlValueProvider.Static((rotation).asVoiceValue()),
                legato = legato
            )
        }

        /**
         * Create a EuclideanPattern with control patterns.
         */
        fun control(
            inner: SprudelPattern,
            pulsesPattern: SprudelPattern,
            stepsPattern: SprudelPattern,
            rotationPattern: SprudelPattern?,
            legato: Boolean = false,
        ): EuclideanPattern {
            return EuclideanPattern(
                inner = inner,
                pulsesProvider = ControlValueProvider.Pattern(pulsesPattern),
                stepsProvider = ControlValueProvider.Pattern(stepsPattern),
                rotationProvider = rotationPattern?.let { ControlValueProvider.Pattern(it) },
                legato = legato
            )
        }

        /**
         * Creates a standard Euclidean Pattern (gated) - legacy method for internal use.
         * Returns the inner pattern if inputs are invalid (e.g. steps <= 0).
         */
        internal fun create(
            inner: SprudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): SprudelPattern {
            if (steps <= 0 || steps < abs(pulses)) return inner
            // pulses < 0 is valid (inversion)
            // pulses > steps is valid (returns all 1s)

            return static(inner, pulses, steps, rotation, legato = false)
        }

        /**
         * Creates a Legato Euclidean Pattern - legacy method for internal use.
         * In this version, pulses are held until the next pulse (no gaps).
         */
        internal fun createLegato(
            inner: SprudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): SprudelPattern {
            return static(inner, pulses, steps, rotation, legato = true)
        }

        /**
         * Internal implementation for static legato euclidean pattern.
         */
        private fun createLegatoStatic(
            inner: SprudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int,
        ): SprudelPattern {
            if (pulses <= 0 || steps <= 0) return EmptyPattern

            // Build the euclidean bitmap, then rotate it. Negate so a positive rotation shifts the
            // onsets towards later steps; the modulo keeps large rotation values within one cycle.
            val bitmap = bjorklund(pulses, steps)
            val effectiveRotation = rotation % steps
            val rotatedBitmap = rotate(bitmap, -effectiveRotation)

            val onsets = rotatedBitmap.mapIndexedNotNull { i, v -> if (v == 1) i else null }

            if (onsets.isEmpty()) return EmptyPattern

            val ratSteps = steps.toDouble()
            val segments = ArrayList<Pair<CycleTime, CycleTime>>()

            var i = 0
            while (i < steps) {
                if (rotatedBitmap[i] == 1) {
                    // It's an onset. Find distance to next onset.
                    var dist = 1
                    var safety = 0
                    while (safety < steps) {
                        val nextIdx = (i + dist) % steps
                        if (rotatedBitmap[nextIdx] == 1) break
                        dist++
                        safety++
                    }

                    // Create segment relative to cycle start: [i/steps, (i+dist)/steps)
                    val start = CycleTime.ofSubdivision(i, steps)
                    val end = CycleTime.ofSubdivision(i + dist, steps)
                    segments.add(start to end)

                    // Advance by dist (skipping gaps covered by this event)
                    i += dist
                } else {
                    // It's a gap
                    i++
                }
            }

            // Create a custom pattern that repeats the segments every cycle.
            // This ensures alignment with the metric grid (1 cycle = 1 unit)
            // even if events overhang into the next cycle.
            val geometry = object : SprudelPattern {
                override val weight = 1.0
                override val numSteps: Double = ratSteps

                override fun estimateCycleDuration(): Double = 1.0

                override fun queryArcContextual(
                    from: CycleTime,
                    to: CycleTime,
                    ctx: QueryContext,
                ): List<SprudelPatternEvent> {
                    val results = createEventList()

                    val startCycle = from.cycleIndex()
                    val endCycle = to.ceilToCycle().cycleIndex()

                    // Check from startCycle - 1 to handle events overlapping from previous cycle
                    for (cycle in (startCycle - 1) until endCycle) {
                        val cycleStart = CycleTime.ofCycleIndex(cycle)

                        for ((segStart, segEnd) in segments) {
                            val absStart = cycleStart + segStart
                            val absEnd = cycleStart + segEnd

                            // Check intersection with query arc
                            val s = from.coerceAtLeast(absStart)
                            val e = to.coerceAtMost(absEnd)

                            if (s < e) {
                                results.add(
                                    SprudelPatternEvent(
                                        part = CycleTimeSpan(s, e),
                                        whole = CycleTimeSpan(absStart, absEnd),
                                        data = createSprudelVoiceData { value = 1.asVoiceValue() }
                                    )
                                )
                            }
                        }
                    }
                    return results
                }
            }

            return inner.struct(geometry)
        }

        /**
         * Rotates [list] by [by] positions: a positive offset moves the head to the back, a
         * negative offset moves the tail to the front. The offset is clamped to the list length,
         * so an offset whose magnitude reaches the length leaves the list unchanged.
         */
        private fun rotate(list: List<Int>, by: Int): List<Int> {
            if (list.isEmpty()) return list
            val split = (if (by >= 0) by else list.size + by).coerceIn(0, list.size)
            return list.subList(split, list.size) + list.subList(0, split)
        }
    }

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val pulsesEvents = pulsesProvider.queryEvents(from, to, ctx)
        val stepsEvents = stepsProvider.queryEvents(from, to, ctx)

        val rotationEvents = rotationProvider?.queryEvents(from, to, ctx)
            ?: run {
                val timeSpan = CycleTimeSpan(begin = from, end = to)

                listOf(
                    SprudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = createSprudelVoiceData { value = 1.0.asVoiceValue() }
                    )
                )
            }

        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty() || rotationEvents.isEmpty()) {
            return emptyList()
        }

        val result = createEventList()

        // Find all overlapping combinations
        for (pulsesEvent in pulsesEvents) {
            for (stepsEvent in stepsEvents) {
                for (rotationEvent in rotationEvents) {
                    val overlapBegin: CycleTime = pulsesEvent.part.begin
                        .coerceAtLeast(stepsEvent.part.begin).coerceAtLeast(rotationEvent.part.begin)

                    val overlapEnd: CycleTime = pulsesEvent.part.end
                        .coerceAtMost(stepsEvent.part.end).coerceAtMost(rotationEvent.part.end)

                    if (overlapEnd <= overlapBegin) continue

                    val pulses = pulsesEvent.data.value?.asInt ?: 0
                    val steps = stepsEvent.data.value?.asInt ?: 0
                    val rotation = rotationEvent.data.value?.asInt ?: 0

                    val events: List<SprudelPatternEvent> =
                        applyEuclideanRhythm(overlapBegin, overlapEnd, ctx, pulses, steps, rotation)

                    result.addAll(events)
                }
            }
        }

        return result
    }

    private fun applyEuclideanRhythm(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<SprudelPatternEvent> {
        return if (legato) {
            queryLegatoStatic(from, to, ctx, pulses, steps, rotation)
        } else {
            queryStatic(from, to, ctx, pulses, steps, rotation)
        }
    }

    private fun queryStatic(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<SprudelPatternEvent> {
        if (steps <= 0 || steps < abs(pulses)) return emptyList()

        val events = createEventList()
        val rhythm = rotate(bjorklund(pulses, steps), -rotation)

        val startCycle = from.cycleIndex()
        val endCycle = to.ceilToCycle().cycleIndex()

        for (cycle in startCycle until endCycle) {
            val cycleOffset = CycleTime.ofCycleIndex(cycle)

            rhythm.forEachIndexed { stepIndex, isActive ->
                if (isActive == 1) {
                    // Step boundaries [stepIndex/steps, (stepIndex+1)/steps), snapped to the tick
                    // grid (exact when steps divides T = 2^13·3·5·7, else nearest tick).
                    val stepStart = cycleOffset + CycleTime.ofSubdivision(stepIndex, steps)
                    val stepEnd = cycleOffset + CycleTime.ofSubdivision(stepIndex + 1, steps)

                    // Check intersection with query arc
                    val intersectStart = from.coerceAtLeast(stepStart)
                    val intersectEnd = to.coerceAtMost(stepEnd)

                    if (intersectEnd > intersectStart) {
                        val innerEvents = inner.queryArcContextual(intersectStart, intersectEnd, ctx)
                            .sortedBy { it.part.begin }
                            .take(1)

                        events.addAll(innerEvents.map { ev ->
                            val timeSpan = CycleTimeSpan(begin = intersectStart, end = intersectEnd)

                            ev.copy(
                                part = timeSpan,
                                whole = timeSpan  // Each Euclidean hit is independent
                            )
                        })
                    }
                }
            }
        }

        return events
    }

    private fun queryLegatoStatic(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<SprudelPatternEvent> {
        val legatoPattern = createLegatoStatic(inner, pulses, steps, rotation)
        return legatoPattern.queryArcContextual(from, to, ctx)
    }
}
