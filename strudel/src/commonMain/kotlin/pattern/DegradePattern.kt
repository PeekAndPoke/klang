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
        return inner.queryArc(from, to).filter { event ->
            // Use the event's start time as a seed for deterministic randomness
            // We use the hash of the start time to get a stable seed for that specific event
            val seed = event.begin.hashCode().toLong()
            val random = Random(seed)
            random.nextDouble() > probability
        }
    }
}
