package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Reverses the pattern with control pattern for the reversal factor.
 *
 * When n <= 1: Standard per-cycle reversal
 * When n > 1: Reverses over n cycles using fast/slow approach
 */
internal class ReversePatternWithControl(
    val inner: StrudelPattern,
    val nPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = inner.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // Query the control pattern to get n values
        val nEvents = nPattern.queryArcContextual(from, to, ctx)

        if (nEvents.isEmpty()) {
            // No control events, return silence
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // For each control event, apply reversal with its n value
        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1

            // Query the inner pattern for this time span
            val patternToReverse = if (n <= 1) {
                // Simple per-cycle reversal
                ReversePattern(inner)
            } else {
                // Multi-cycle reversal using fast/slow approach
                // fast(n).rev().slow(n)
                val fast = TempoModifierPattern(inner, factor = n.toDouble(), invertPattern = true)
                val reversed = ReversePattern(fast)
                TempoModifierPattern(reversed, factor = n.toDouble(), invertPattern = false)
            }

            // Query the reversed pattern for the control event's timespan
            val events = patternToReverse.queryArcContextual(nEvent.begin, nEvent.end, ctx)
            result.addAll(events)
        }

        return result
    }
}
