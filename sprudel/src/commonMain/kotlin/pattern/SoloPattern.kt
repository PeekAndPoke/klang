/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.sampleAt
import kotlin.math.floor

/**
 * Solos [source]: every event it plays is stamped with the solo amount and with [soloId], and the whole query
 * window is covered by control events that tell the engine the solo is alive, rests included.
 *
 * For every query window `[from, to)`:
 * 1. Source events keep their data, plus the solo amount sampled from [soloControl] at their onset and
 *    `patternId = soloId`.
 * 2. For every event of [soloControl] that overlaps the window, CONTROL events that cover the overlap, cut on a
 *    grid of [CONTROL_GRID_PER_CYCLE] steps per cycle (`whole == part` for each piece, so every piece is an onset in
 *    its query window). Their data is `control = true`, the amount and `patternId = soloId`, and nothing else: no
 *    note, no frequency, no sound, no gain.
 *
 *    Why the grid: the engine records a control event until its end, and a live edit resends only the events that
 *    start after the edit's cutoff (`KlangPatternScheduler.resyncCurrentCycle`, now plus 0.2 s). With one control
 *    event per cycle, adding or removing `.solo()` took effect up to a whole cycle (or two) later. With the grid,
 *    both directions take effect within 1/8 cycle after the cutoff, then the engine's ramp runs. `control` survives every later op (`merge` never takes it from the other side,
 *    the field setters copy it along), and the engine never builds a voice from a control event, so nothing after
 *    `.solo()` can make one sound.
 *
 * The engine records "source [soloId] is soloed at this amount until the event's end" from any event that carries
 * a positive amount, control events and sounding notes alike (`VoiceScheduler`). The control events therefore keep
 * the solo alive through a rest, which sounding fillers used to do until 2026-10-07
 * (`docs/tasks-archive/2026-10/20261009-bugfix-solo-rests-and-amount.md`).
 *
 * Pure and immutable: [soloId] is fixed at construction by the door, and a query depends on its arguments only.
 *
 * @param soloId the id of the `solo` call that made this pattern, stamped as the voice's `sourceId` on the wire.
 */
class SoloPattern(
    private val source: SprudelPattern,
    private val soloControl: SprudelPattern,
    val soloId: String,
) : SprudelPattern {

    companion object {
        /** Control events are cut at every 1/8 cycle, so a live edit of the solo takes effect within 1/8 cycle. */
        const val CONTROL_GRID_PER_CYCLE: Int = 8

        private val gridStep = CycleTime(CycleTime.T / CONTROL_GRID_PER_CYCLE)
    }

    override val weight get() = source.weight
    override val numSteps get() = source.numSteps
    override fun estimateCycleDuration() = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        val result = mutableListOf<SprudelPatternEvent>()

        // 1. The source events, decorated with the amount sampled at their onset and with this call's id.
        for (event in source.queryArcContextual(from, to, ctx)) {
            val sample = soloControl.sampleAt(event.whole.begin, ctx)

            result.add(
                event.copy(
                    data = event.data.copy(
                        solo = soloAmountOf(sample),
                        patternId = soloId,
                    ),
                ).prependLocations(sample?.sourceLocations),
            )
        }

        // 2. The control events: the amount over the whole window, rests included, cut on the grid.
        for (controlEvent in soloControl.queryArcContextual(from, to, ctx)) {
            val span = controlEvent.part.clipTo(begin = from, end = to) ?: continue
            val amount = soloAmountOf(controlEvent) ?: continue
            var begin = span.begin

            while (begin < span.end) {
                val nextGridLine = CycleTime((floor(begin.ticks / gridStep.ticks) + 1.0) * gridStep.ticks)
                val piece = CycleTimeSpan(begin, nextGridLine.coerceAtMost(span.end))

                result.add(
                    SprudelPatternEvent(
                        part = piece,
                        whole = piece, // whole == part: an onset in every query window
                        data = createSprudelVoiceData().also {
                            it.control = true
                            it.solo = amount
                            it.patternId = soloId
                        },
                    ),
                )

                begin = piece.end
            }
        }

        return result
    }

    /**
     * The amount a control event carries, coerced into `0.0..1.0` (`/code-style` §21): a NaN reads as 0.0, which
     * engages nothing; a value that is not a number reads as unset (null).
     */
    private fun soloAmountOf(event: SprudelPatternEvent?): Double? {
        val raw = event?.data?.value?.asDouble ?: return null

        // NaN-guard: a NaN fails both compares of coerceIn and would pass through
        return if (raw.isNaN()) 0.0 else raw.coerceIn(0.0, 1.0)
    }
}
