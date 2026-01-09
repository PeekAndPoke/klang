package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.random.Random

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

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        if (choices.isEmpty()) return emptyList()

        // Use the event's start time as a seed for deterministic randomness
        // We use a large prime multiplier to scramble the bits effectively
        val random = createSeededRandom(from.toDouble())

        return choices.random(random).queryArc(from, to)
    }

    fun createSeededRandom(value: Double): Random {
        val hash = value.hashCode().toLong()
        val seed = (hash * 6364136223846793005L) + 1442695040888963407L
        return Random(seed)
    }
}
