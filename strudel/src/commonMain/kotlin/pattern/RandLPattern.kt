package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpret

/**
 * Dynamic randL pattern: length varies according to a control pattern.
 */
internal class RandLPattern(
    private val nPattern: StrudelPattern,
) : StrudelPattern.FixedWeight {

    companion object {
        fun create(nPattern: StrudelPattern, staticN: Int?): StrudelPattern {
            return if (staticN != null) {
                createStatic(staticN)
            } else {
                RandLPattern(nPattern)
            }
        }

        private fun createStatic(n: Int): StrudelPattern {
            if (n < 1) {
                return EmptyPattern
            }

            val atom = AtomicPattern.pure
            val events = (0 until n).map { index ->
                atom.reinterpret { evt, ctx ->
                    val fraction = evt.begin - evt.begin.floor()
                    val seed = (fraction * n * 10).toInt()
                    val random = ctx.getSeededRandom(seed, index, "randL")
                    val value = random.nextInt(0, 8).asVoiceValue()
                    evt.copy(data = evt.data.copy(value = value))
                }
            }

            return SequencePattern(events)
        }
    }

    override val steps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        if (nEvents.isEmpty()) return emptyList()

        val result = createEventList()
        val atom = AtomicPattern.pure

        for (nEvent in nEvents) {
            val n = nEvent.data.value?.asInt ?: 0
            if (n < 1) continue

            val events = (0 until n).map { index ->
                atom.reinterpret { evt, ctx2 ->
                    val fraction = evt.begin - evt.begin.floor()
                    val seed = (fraction * n * 10).toInt()
                    val random = ctx2.getSeededRandom(seed, index, "randL")
                    val value = random.nextInt(0, 8).asVoiceValue()
                    evt.copy(data = evt.data.copy(value = value))
                }
            }

            val seqPattern = SequencePattern(events)
            val seqEvents = seqPattern.queryArcContextual(nEvent.begin, nEvent.end, ctx)
            result.addAll(seqEvents)
        }

        return result
    }
}
