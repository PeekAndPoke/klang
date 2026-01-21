package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Speeds up a pattern and also increases the speed parameter by the same factor.
 *
 * This is like fast() but also affects sample playback speed, resulting in pitch changes.
 * For example, hurry(2) will make the pattern play twice as fast AND make samples play
 * at double speed (higher pitch).
 *
 * @param source The source pattern to speed up
 * @param factor The speed multiplication factor
 */
internal class HurryPattern(
    val source: StrudelPattern,
    val factor: Double,
) : StrudelPattern {

    private val factorRat = factor.toRational()
    private val scale = factorRat

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        val sourceDur = source.estimateCycleDuration()
        return sourceDur / scale
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val innerFrom = from * scale
        val innerTo = to * scale

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val mappedBegin = ev.begin / scale
            val mappedEnd = ev.end / scale
            val mappedDur = ev.dur / scale

            if (mappedEnd > from && mappedBegin < to) {
                // Multiply the speed parameter by the factor
                val currentSpeed = ev.data.speed ?: 1.0
                val newSpeed = currentSpeed * factor

                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                    dur = mappedDur,
                    data = ev.data.copy(speed = newSpeed)
                )
            } else {
                null
            }
        }
    }
}
