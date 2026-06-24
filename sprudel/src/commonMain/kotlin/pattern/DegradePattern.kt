/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Keeps or drops events of [source] by thresholding a randomness pattern — the engine primitive
 * behind `degradeBy` / `degradeByWith` and their `undegrade` inverses.
 *
 * For each event of [source], the [randomness] pattern is sampled across that event's whole. The
 * event is kept only where an overlapping randomness value clears the threshold, and the kept part
 * is clipped to that overlap (so the source value and whole are preserved, only the part narrows).
 *
 * @param source     pattern whose events may be dropped.
 * @param randomness randomness source, values in `0..1` (e.g. `rand`); sampled per source event.
 * @param threshold  comparison threshold in `0..1`.
 * @param keepStrictlyAbove `true` keeps where value `>` threshold (degrade); `false` keeps where
 *        value `>=` threshold (undegrade), so the two are exact complements at a shared seed.
 */
internal class DegradePattern(
    val source: SprudelPattern,
    val randomness: SprudelPattern,
    val threshold: Double,
    val keepStrictlyAbove: Boolean,
) : SprudelPattern {

    override val weight: Double get() = source.weight
    override val numSteps: Double? get() = source.numSteps
    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val result = createEventList()

        for (event in source.queryArcContextual(from, to, ctx)) {
            val whole = event.whole
            val samples = randomness.queryArcContextual(whole.begin, whole.end, ctx)

            for (sample in samples) {
                val value = sample.data.value?.asDouble ?: 0.0
                val passes = if (keepStrictlyAbove) value > threshold else value >= threshold
                if (!passes) continue

                // Narrow the kept event to where the source part and this sample overlap.
                val clipped = event.part.clipTo(sample.part) ?: continue
                result.add(event.copy(part = clipped))
            }
        }

        return result
    }
}
