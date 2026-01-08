package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.random.Random

internal class DegradePattern(
    val inner: StrudelPattern,
    val probability: Double = 0.5,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.degradeBy(probability: Double): StrudelPattern =
            DegradePattern(this, probability)
    }

    override val weight: Double get() = inner.weight

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        // Use seeded random based on the events start time
        val random = createSeededRandom(from.toDouble())

        return inner.queryArc(from, to).filter {
            random.nextDouble() > probability
        }
    }

    private fun createSeededRandom(value: Double): Random {
        // Use a distinct mixing strategy for ChoicePattern to avoid correlation with DegradePattern
        val cycleHash = value.hashCode().toLong()
        // Different multiplier and addend than DegradePattern
        val seed = (cycleHash * 48271L) + 2147483647L

        return Random(seed)
    }
}
