package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

internal class ChoicePattern(
    val choices: List<StrudelPattern>,
) : StrudelPattern.FixedWeight {

    companion object {
        // This matches what the parser calls: pattern.choice(right)
        fun StrudelPattern.choice(other: StrudelPattern): StrudelPattern {
            // Flatten both sides to ensure equal probability for all items in a sequence like a|b|c
            val left = if (this is ChoicePattern) this.choices else listOf(this)
            val right = if (other is ChoicePattern) other.choices else listOf(other)

            return ChoicePattern(left + right)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        if (choices.isEmpty()) return emptyList()

        val random = ctx.getSeededRandom(from, choices, "ChoicePattern")

        return choices.random(random).queryArcContextual(from, to, ctx)
    }
}
