package io.peekandpoke.klang.strudel

/**
 * Strudel pattern.
 */
interface StrudelPattern {
    /**
     * Queries events from [from] and [to] cycles.
     */
    fun queryArc(from: Double, to: Double): List<StrudelPatternEvent>
}

fun StrudelPattern.makeStatic(from: Double, to: Double) = StaticStrudelPattern(
    events = queryArc(from, to)
)
