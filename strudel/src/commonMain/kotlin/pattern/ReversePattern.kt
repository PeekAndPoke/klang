package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Reverses the pattern within each cycle (or over multiple cycles based on n).
 *
 * When n <= 1: Standard per-cycle reversal
 * When n > 1: Reverses over n cycles using fast/slow approach
 *
 * @param inner The pattern to reverse
 * @param nProvider Control value provider for the reversal factor (default: 1.0)
 */
internal class ReversePattern(
    val inner: StrudelPattern,
    val nProvider: ControlValueProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(1.0)),
) : StrudelPattern {
    companion object {
        /**
         * Create a ReversePattern with a static n value.
         */
        fun static(inner: StrudelPattern, n: Rational = Rational.ONE): ReversePattern {
            return ReversePattern(
                inner = inner,
                nProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(n.toDouble()))
            )
        }

        /**
         * Create a ReversePattern with a control pattern for n.
         */
        fun control(inner: StrudelPattern, nPattern: StrudelPattern): ReversePattern {
            return ReversePattern(
                inner = inner,
                nProvider = ControlValueProvider.Pattern(nPattern)
            )
        }
    }

    override val weight: Double get() = inner.weight

    override val steps: Rational? get() = inner.steps

    override fun estimateCycleDuration(): Rational = inner.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // For static n=1, use optimized per-cycle reversal
        if (nProvider is ControlValueProvider.Static) {
            val n = nProvider.value.asInt ?: 1
            if (n <= 1) {
                return querySimpleReverse(from, to, ctx)
            } else {
                return queryMultiCycleReverse(from, to, ctx, n.toRational())
            }
        }

        // For control patterns, segment by control events
        val controlPattern = (nProvider as? ControlValueProvider.Pattern)?.pattern
            ?: return querySimpleReverse(from, to, ctx)

        val nEvents = controlPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // For each control event, apply reversal with its n value
        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1

            val events = if (n <= 1) {
                // Simple per-cycle reversal
                querySimpleReverse(nEvent.begin, nEvent.end, ctx)
            } else {
                // Multi-cycle reversal
                queryMultiCycleReverse(nEvent.begin, nEvent.end, ctx, n.toRational())
            }

            result.addAll(events)
        }

        return result
    }

    private fun querySimpleReverse(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val startCycle = from.floor()
        val endCycle = to.ceil()
        val events = mutableListOf<StrudelPatternEvent>()

        for (c in startCycle.toLong()..endCycle.toLong()) {
            val cycle = c.toRational()
            val nextCycle = cycle + Rational.ONE

            // Intersect query with current cycle
            val intersectStart = maxOf(from, cycle)
            val intersectEnd = minOf(to, nextCycle)

            if (intersectEnd > intersectStart) {
                // Map the intersected range to reversed inner time
                val innerTo = Rational.ONE + (cycle * Rational(2)) - intersectStart
                val innerFrom = Rational.ONE + (cycle * Rational(2)) - intersectEnd

                inner.queryArcContextual(innerFrom, innerTo, ctx).forEach { ev ->
                    val mappedBegin = Rational.ONE + (cycle * Rational(2)) - ev.end
                    val mappedEnd = Rational.ONE + (cycle * Rational(2)) - ev.begin
                    events.add(ev.copy(begin = mappedBegin, end = mappedEnd))
                }
            }
        }

        return events
    }

    private fun queryMultiCycleReverse(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        nRat: Rational,
    ): List<StrudelPatternEvent> {
        // Multi-cycle reversal using fast/slow approach
        // fast(n).rev().slow(n)
        val fast = TempoModifierPattern.static(source = inner, factor = nRat, invertPattern = true)
        val reversed = ReversePattern(inner = fast, nProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(1.0)))
        val slowed = TempoModifierPattern.static(source = reversed, factor = nRat, invertPattern = false)

        return slowed.queryArcContextual(from, to, ctx)
    }
}
