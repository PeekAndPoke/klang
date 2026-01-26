package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Aligns a pattern within a target cycle duration using left/center/right alignment.
 *
 * @param source The original pattern
 * @param sourceDuration The cycle duration of the source pattern
 * @param targetDuration The target cycle duration to align to
 * @param alignment 0.0 = left, 0.5 = center, 1.0 = right
 */
internal class AlignedPattern(
    private val source: StrudelPattern,
    private val sourceDuration: Rational,
    private val targetDuration: Rational,
    private val alignment: Double,
) : StrudelPattern {

    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = targetDuration

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        if (sourceDuration == targetDuration) {
            return source.queryArcContextual(from, to, ctx)
        }

        val totalGap = targetDuration - sourceDuration
        val timeShift = totalGap * alignment.toRational()
        val results = createEventList()

        val startCycle = (from / targetDuration).floor().toInt()
        val endCycle = (to / targetDuration).ceil().toInt()

        for (cycle in startCycle until endCycle) {
            val cycleOffset = Rational(cycle) * targetDuration
            val patternStart = cycleOffset + timeShift
            val patternEnd = patternStart + sourceDuration

            if (patternEnd > from && patternStart < to) {
                val originalStart = Rational(cycle) * sourceDuration
                val originalEnd = originalStart + sourceDuration

                val events = source.queryArcContextual(originalStart, originalEnd, ctx)

                results.addAll(
                    events.map { event ->
                        event.copy(
                            begin = event.begin - originalStart + patternStart,
                            end = event.end - originalStart + patternStart,
                        )
                    }.filter { event ->
                        event.end > from && event.begin < to
                    }
                )
            }
        }

        return results
    }
}
