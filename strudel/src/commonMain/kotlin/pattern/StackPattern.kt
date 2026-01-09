package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Stack Pattern: Plays multiple patterns simultaneously.
 * Implementation of `stack(a, b)`.
 */
internal class StackPattern(val patterns: List<StrudelPattern>) : StrudelPattern.FixedWeight {

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {

        return patterns
            .flatMap { it.queryArcContextual(from, to, ctx) }
            .sortedBy { it.begin } // Sort them to keep order nice (optional but good for debugging)
    }
}
