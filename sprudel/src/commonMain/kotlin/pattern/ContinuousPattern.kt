/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData

/**
 * A pattern that generates a value based on continuous cycle time.
 */
class ContinuousPattern private constructor(
    val getValue: (from: Double, to: Double, ctx: QueryContext) -> Double,
) : SprudelPattern.FixedWeight {
    companion object {
        /** Where a `range` puts the signal's 0 and its 1, set by `range` / `rangex`; the innermost one wins. */
        val rangeFromKey = QueryContext.Key<Double>("rangeFrom")
        val rangeToKey = QueryContext.Key<Double>("rangeTo")

        operator fun invoke(getValue: (from: Double) -> Double) =
            ContinuousPattern { from, _, _ -> getValue(from) }

        operator fun invoke(getValue: (from: Double, to: Double, ctx: QueryContext) -> Double) =
            ContinuousPattern(getValue)
    }

    override val numSteps: Double = 1.0

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {

        val value = getValue(
            rangeFrom = ctx.getOrDefault(rangeFromKey, 0.0),
            rangeTo = ctx.getOrDefault(rangeToKey, 1.0),
            from = from.toCycles(),
            to = to.toCycles(),
            ctx = ctx
        ).asVoiceValue()

        // Make sure we do not run into an infinite loop
        val granularity = CycleTime.ONE
        val result = createEventList()
        var currentFrom = from

        while (to > currentFrom) {
            val nextFrom = to.coerceAtMost(currentFrom + granularity)

            val span = CycleTimeSpan(begin = currentFrom, end = nextFrom)

            val event = SprudelPatternEvent(
                part = span,
                whole = span,
                data = createSprudelVoiceData().also { it.value = value }
            )

            result.add(event)

            // go ahead
            currentFrom = nextFrom
        }

        return result
    }

    /** Creates a new version of this pattern with a transformed value range */
    internal fun getValue(rangeFrom: Double, rangeTo: Double, from: Double, to: Double, ctx: QueryContext): Double {
        val value = getValue(from, to, ctx)
        // The internal oscillators produce 0.0 to 1.0.
        // We map this value onto the target rangeFrom..rangeTo swing.
        return rangeFrom + (value * (rangeTo - rangeFrom))
    }
}
