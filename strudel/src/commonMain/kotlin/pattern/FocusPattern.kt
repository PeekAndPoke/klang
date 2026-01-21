package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.early
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.lang.silence
import io.peekandpoke.klang.strudel.math.Rational

/**
 * A pattern that focuses on a portion of each cycle, keeping the original timing.
 *
 * Implemented as: pattern.early(start).fast(1/(end-start)).late(start)
 * This speeds up the pattern and shifts it so only the desired portion is visible.
 *
 * @param source The pattern to focus
 * @param start The start position in the cycle (0.0 to 1.0)
 * @param end The end position in the cycle (0.0 to 1.0)
 */
internal class FocusPattern(
    val source: StrudelPattern,
    val start: Rational,
    val end: Rational,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    val transformed = when {
        start >= end -> silence

        else -> source.early(start.floor())
            .fast(Rational.ONE / (end - start))
            .late(start)
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return transformed.queryArcContextual(from, to, ctx)
    }
}
