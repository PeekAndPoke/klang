package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Applies swing/shuffle rhythm to a pattern using control value providers.
 *
 * Divides each cycle into `n` subdivisions. Within each subdivision, events are divided into two groups:
 * - First half: duration = (1 + swing) / 2 * subdivisionDuration
 * - Second half: duration = (1 - swing) / 2 * subdivisionDuration, starts after first half
 *
 * Examples:
 * - swing = 1/3: Creates "long-short" rhythm (2:1 ratio) - classic swing feel
 * - swing = -1/3: Creates "short-long" rhythm (1:2 ratio) - reverse swing
 * - swing = 0: No swing (equal durations)
 *
 * @param source The source pattern to apply swing to
 * @param swingProvider Control value provider for swing amount (-1 to 1)
 * @param nProvider Control value provider for subdivision count
 */
internal class SwingPattern(
    val source: StrudelPattern,
    val swingProvider: ControlValueProvider,
    val nProvider: ControlValueProvider,
) : StrudelPattern {

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // Query control values for this time range
        val n = nProvider.query(from, to, ctx)?.asDouble ?: 1.0

        val events = source.queryArcContextual(from, to, ctx)

        if (n <= 0) {
            return events
        }

        val nRat = n.toRational()
        val subdivisionDuration = Rational.ONE / nRat

        return events.map { ev ->
            val swing = (swingProvider.query(from = ev.begin, ctx = ctx)?.asDouble ?: 0.0).coerceIn(-1.0, 1.0)
            val firstPartRatio = ((1.0 + swing) / 2.0).toRational()
            val secondPartRatio = ((1.0 - swing) / 2.0).toRational()

            // Get the cycle this event is in
            val cycle = ev.begin.floor()

            // Position within the cycle [0, 1)
            val cyclePos = ev.begin - cycle
            val eventDuration = ev.end - ev.begin

            // Which subdivision does this event belong to?
            val subdivisionIndex = (cyclePos / subdivisionDuration).floor()
            val subdivisionStart = subdivisionIndex * subdivisionDuration

            // Position within the subdivision [0, subdivisionDuration)
            val posInSubdivision = cyclePos - subdivisionStart

            // Determine if this is in the first or second half of the subdivision
            val halfDuration = subdivisionDuration / 2.0.toRational()
            val isFirstHalf = posInSubdivision < halfDuration

            if (isFirstHalf) {
                // First half: scale position proportionally within [0, firstPartRatio * subdivisionDuration)
                // Original range: [0, halfDuration)
                // New range: [0, firstPartRatio * subdivisionDuration)
                val scaleFactor = firstPartRatio / 0.5.toRational()

                val newPosInSubdivision = posInSubdivision * scaleFactor
                val newDuration = eventDuration * scaleFactor

                val newBegin = cycle + subdivisionStart + newPosInSubdivision
                val newEnd = newBegin + newDuration

                ev.copy(begin = newBegin, end = newEnd, dur = newDuration)
            } else {
                // Second half: scale position proportionally within [firstPartRatio * subdivisionDuration, subdivisionDuration)
                // Original range: [halfDuration, subdivisionDuration)
                // New range: [firstPartRatio * subdivisionDuration, subdivisionDuration)
                val scaleFactor = secondPartRatio / 0.5.toRational()

                val posWithinSecondHalf = posInSubdivision - halfDuration
                val newPosInSecondHalf = posWithinSecondHalf * scaleFactor
                val newDuration = eventDuration * scaleFactor

                val firstPartDuration = subdivisionDuration * firstPartRatio
                val newBegin = cycle + subdivisionStart + firstPartDuration + newPosInSecondHalf
                val newEnd = newBegin + newDuration

                ev.copy(begin = newBegin, end = newEnd, dur = newDuration)
            }
        }
    }
}
