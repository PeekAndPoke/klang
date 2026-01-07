package io.peekandpoke.klang.strudel.patterns

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent

/**
 * Stack Pattern: Plays multiple patterns simultaneously.
 * Implementation of `stack(a, b)`.
 */
internal class StackPattern(val patterns: List<StrudelPattern>) : StrudelPattern {
    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        // Simply collect events from all patterns for the same time arc
        return patterns.flatMap { it.queryArc(from, to) }
            .sortedBy { it.begin } // Sort them to keep order nice (optional but good for debugging)
    }
}
