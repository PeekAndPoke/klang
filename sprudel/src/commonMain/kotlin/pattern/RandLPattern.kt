package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret

/**
 * Dynamic randL pattern: length varies according to a control pattern.
 */
internal class RandLPattern(
    private val nPattern: SprudelPattern,
) : SprudelPattern.FixedWeight {

    companion object {
        fun create(nPattern: SprudelPattern, staticN: Int?): SprudelPattern {
            return if (staticN != null) {
                createStatic(staticN)
            } else {
                RandLPattern(nPattern)
            }
        }

        private fun createStatic(n: Int): SprudelPattern {
            if (n < 1) {
                return EmptyPattern
            }

            val atom = AtomicPattern.pure
            val events = (0 until n).map { index ->
                atom.reinterpret { evt, ctx ->
                    val fraction = evt.part.begin - evt.part.begin.floor()
                    val seed = (fraction * n * 10).toInt()
                    val random = ctx.getSeededRandom(seed, index, "randL")
                    val value = random.nextInt(0, 8).asVoiceValue()
                    evt.copy(data = evt.data.copy(value = value))
                }
            }

            return SequencePattern(events)
        }
    }

    override val numSteps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()
        val atom = AtomicPattern.pure

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 0
            if (n < 1) continue

            val events = (0 until n).map { index ->
                atom.reinterpret { evt, ctx2 ->
                    val fraction = evt.part.begin - evt.part.begin.floor()
                    val seed = (fraction * n * 10).toInt()
                    val random = ctx2.getSeededRandom(seed, index, "randL")
                    val value = random.nextInt(0, 8).asVoiceValue()
                    evt.copy(data = evt.data.copy(value = value))
                }
            }

            val seqPattern = SequencePattern(events)
            val seqEvents = seqPattern.queryArcContextual(nEvent.part.begin, nEvent.part.end, ctx)
            result.addAll(seqEvents)
        }

        return result
    }
}
