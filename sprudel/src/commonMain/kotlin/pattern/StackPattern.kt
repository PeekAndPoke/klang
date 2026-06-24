/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.common.math.lcm
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Stack Pattern: Plays multiple patterns simultaneously.
 * Implementation of `stack(a, b)`.
 */
internal class StackPattern(val patterns: List<SprudelPattern>) : SprudelPattern.FixedWeight {

    override val numSteps: Double?
        get() {
            val allSteps = patterns.mapNotNull { it.numSteps?.toInt() }
            if (allSteps.isEmpty()) return null
            return lcm(allSteps).takeIf { it > 0 }?.toDouble()
        }

    override fun estimateCycleDuration(): Double {
        return patterns.maxOfOrNull { it.estimateCycleDuration() } ?: 1.0
    }

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        return patterns
            .flatMap { it.queryArcContextual(from, to, ctx) }
            .sortedBy { it.part.begin } // Sort them to keep order nice (optional but good for debugging)
    }
}
