package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan
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
    val nProvider: ControlValueProvider = ControlValueProvider.Static.ONE,
) : StrudelPattern {
    companion object {
        /**
         * Create a ReversePattern with a static n value.
         */
        fun static(inner: StrudelPattern, n: Rational = Rational.ONE): ReversePattern {
            return ReversePattern(
                inner = inner,
                nProvider = ControlValueProvider.Static(n.asVoiceValue())
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

    override val numSteps: Rational? get() = inner.numSteps

    override fun estimateCycleDuration(): Rational = inner.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val nEvents = nProvider.queryEvents(from, to, ctx)
        if (nEvents.isEmpty()) return querySimpleReverse(from, to, ctx)

        // Fast-path when provider behaves like a static value over the full range
        if (nEvents.size == 1 && nEvents[0].part.begin == from && nEvents[0].part.end == to) {
            val n = nEvents[0].data.value?.asInt ?: 1
            return if (n <= 1) {
                querySimpleReverse(from, to, ctx)
            } else {
                queryMultiCycleReverse(from, to, ctx, n.toRational())
            }
        }

        val result = createEventList()

        // For each control event, apply reversal with its n value
        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 1

            val events = if (n <= 1) {
                // Simple per-cycle reversal
                querySimpleReverse(nEvent.part.begin, nEvent.part.end, ctx)
            } else {
                // Multi-cycle reversal
                queryMultiCycleReverse(nEvent.part.begin, nEvent.part.end, ctx, n.toRational())
            }

            result.addAll(events)
        }

        return result
    }

    private fun querySimpleReverse(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val startCycle = from.floor()
        val endCycle = to.ceil()
        val events = createEventList()

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
                    val pivot = Rational.ONE + (cycle * Rational(2))
                    val reversedPart = TimeSpan(begin = pivot - ev.part.end, end = pivot - ev.part.begin)
                    val reversedWhole = ev.whole.let {
                        TimeSpan(begin = pivot - it.end, end = pivot - it.begin)
                    }

                    events.add(ev.copy(part = reversedPart, whole = reversedWhole))
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
        val fast: TempoModifierPattern =
            TempoModifierPattern.static(source = inner, factor = nRat, invertPattern = true)

        val reversed =
            ReversePattern(inner = fast, nProvider = ControlValueProvider.Static.ONE)

        val slowed: TempoModifierPattern =
            TempoModifierPattern.static(source = reversed, factor = nRat, invertPattern = false)

        return slowed.queryArcContextual(from, to, ctx)
    }
}
