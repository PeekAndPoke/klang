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
 * A pattern that modifies the query context before querying the source pattern.
 */
internal class ContextModifierPattern(
    /** The wrapped pattern. */
    val source: SprudelPattern,
    /** The context modifier function. */
    val modifier: QueryContext.Updater.() -> Unit,
) : SprudelPattern {
    companion object {
        fun SprudelPattern.withContext(modifier: QueryContext.Updater.() -> Unit) =
            ContextModifierPattern(this, modifier)
    }

    override val weight: Double get() = source.weight

    override val numSteps: Double? get() = source.numSteps

    override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val updated = ctx.update(modifier)

        return source.queryArcContextual(from, to, updated)
    }
}
