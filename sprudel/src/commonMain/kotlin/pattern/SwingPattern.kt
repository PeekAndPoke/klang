package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.TimeSpan

/**
 * Applies swing timing to a source pattern by shifting and stretching events
 * based on their position within each subdivision pair.
 *
 * Unlike the previous `inside(n, lateInCycle().stretchBy())` approach, this pattern
 * operates directly on event times without time-domain zooming, avoiding amplification
 * artifacts at large cycle numbers.
 *
 * Each cycle is divided into [n] subdivisions. Within each subdivision:
 * - Events in the first half: no shift, duration stretched by (1 + swing)
 * - Events in the second half: shifted later, duration compressed by (1 - swing)
 *
 * @param source The pattern to apply swing to
 * @param swing Swing amount (-1..1). 1/3 is classic jazz swing. 0 = no swing. Negative = reverse swing.
 * @param n Number of subdivisions per cycle (typically 1, 2, 4, 8)
 */
internal class SwingPattern(
    private val source: SprudelPattern,
    private val swing: Rational,
    private val n: Rational,
) : SprudelPattern {

    override val weight: Double get() = source.weight
    override val numSteps: Rational? get() = source.numSteps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        if (swing == Rational.ZERO || n <= Rational.ZERO) {
            return source.queryArcContextual(from, to, ctx)
        }

        val events = source.queryArcContextual(from, to, ctx)
        if (events.isEmpty()) return events

        // Duration of one subdivision in cycle time
        val subdivDuration = Rational.ONE / n

        return events.mapNotNull { event ->
            if (swing > Rational.ZERO) {
                applySwingPositive(event, subdivDuration)
            } else {
                applySwingNegative(event, subdivDuration)
            }
        }
    }

    /** Positive swing: stretch first-half events, shift+compress second-half events */
    private fun applySwingPositive(event: SprudelPatternEvent, subdivDuration: Rational): SprudelPatternEvent {
        // Find which subdivision this event's onset is in
        val onsetInCycle = event.whole.begin - event.whole.begin.floor()
        val subdivStart = (onsetInCycle / subdivDuration).floor() * subdivDuration
        val posInSubdiv = onsetInCycle - subdivStart
        val subdivHalf = subdivDuration / Rational(2)

        // Is the event in the first or second half of its subdivision?
        val isSecondHalf = posInSubdiv >= subdivHalf

        val stretchFactor: Rational
        val shift: Rational

        if (!isSecondHalf) {
            // First half: stretch, no shift
            stretchFactor = Rational.ONE + swing
            shift = Rational.ZERO
        } else {
            // Second half: compress, shift later
            stretchFactor = Rational.ONE - swing
            shift = swing * subdivHalf
        }

        val newPartBegin = event.part.begin + shift
        val newPartEnd = newPartBegin + event.part.duration * stretchFactor
        val newWholeBegin = event.whole.begin + shift
        val newWholeEnd = newWholeBegin + event.whole.duration * stretchFactor

        return event.copy(
            part = TimeSpan(newPartBegin, newPartEnd),
            whole = TimeSpan(newWholeBegin, newWholeEnd),
        )
    }

    /** Negative swing: compress first-half events, shift+stretch second-half events (reverse swing) */
    private fun applySwingNegative(event: SprudelPatternEvent, subdivDuration: Rational): SprudelPatternEvent {
        val absSwing = Rational.ZERO - swing
        val onsetInCycle = event.whole.begin - event.whole.begin.floor()
        val subdivStart = (onsetInCycle / subdivDuration).floor() * subdivDuration
        val posInSubdiv = onsetInCycle - subdivStart
        val subdivHalf = subdivDuration / Rational(2)

        val isSecondHalf = posInSubdiv >= subdivHalf

        val stretchFactor: Rational
        val shift: Rational

        if (!isSecondHalf) {
            // First half: compress, shift earlier (negative swing shrinks first half)
            stretchFactor = Rational.ONE - absSwing
            shift = Rational.ZERO
        } else {
            // Second half: stretch, shift earlier to fill the gap
            stretchFactor = Rational.ONE + absSwing
            shift = Rational.ZERO - absSwing * subdivHalf
        }

        val newPartBegin = event.part.begin + shift
        val newPartEnd = newPartBegin + event.part.duration * stretchFactor
        val newWholeBegin = event.whole.begin + shift
        val newWholeEnd = newWholeBegin + event.whole.duration * stretchFactor

        return event.copy(
            part = TimeSpan(newPartBegin, newPartEnd),
            whole = TimeSpan(newWholeBegin, newWholeEnd),
        )
    }
}
