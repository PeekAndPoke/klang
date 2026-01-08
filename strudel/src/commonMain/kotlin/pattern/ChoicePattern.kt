package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.math.floor
import kotlin.random.Random

internal class ChoicePattern(
    val patterns: List<StrudelPattern>,
) : StrudelPattern.Fixed {

    companion object {
        // This matches what the parser calls: pattern.choice(right)
        fun StrudelPattern.choice(other: StrudelPattern): StrudelPattern {
            return if (this is ChoicePattern) {
                // Flatten the structure: reuse existing choices and add the new one
                ChoicePattern(this.patterns + other)
            } else {
                // Create a new choice pattern
                ChoicePattern(listOf(this, other))
            }
        }
    }

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        // Strudel's choice works per cycle.
        // We find which cycle we are in and pick a pattern for that cycle.
        val cycle = floor(from.toDouble()).toLong()
        val seed = cycle.hashCode().toLong()

        // Standard behavior: Equal probability for all choices
        val pickedIndex = Random(seed).nextInt(patterns.size)

        return patterns[pickedIndex].queryArc(from, to)
    }
}
