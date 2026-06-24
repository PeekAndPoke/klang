/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.sampleAt

/**
 * Merges voice data from a control pattern into the source pattern's events (Outer Join semantics).
 *
 * For each event in the source pattern, the control pattern is sampled at the event's onset time.
 * If the control pattern has an event at that time, the source event's voice data is merged with
 * the control event's voice data via [SprudelVoiceData.merge].
 *
 * Events for which the control pattern has no sample are dropped.
 */
internal class MergePattern(
    val source: SprudelPattern,
    val control: SprudelPattern,
) : SprudelPattern {
    override val weight: Double get() = source.weight
    override val numSteps: Double? get() = source.numSteps
    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<SprudelPatternEvent>()

        for (srcEvt in sourceEvents) {
            val sampleTime = srcEvt.whole.begin

            val toAdd = when (val ctrlEvt = control.sampleAt(sampleTime, ctx)) {
                null -> srcEvt
                else -> {
                    // srcEvt.data is a single-owner leaf clone — merge in place, no allocation.
                    srcEvt.data.mergeFrom(ctrlEvt.data)
                    srcEvt.copy(data = srcEvt.data).prependLocations(ctrlEvt.sourceLocations)
                }
            }

            result.add(toAdd)
        }

        return result
    }
}
