package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.math.Rational

class RandLPatternSpec : StringSpec({

    "RandLPattern emits n events per control window" {
        val nPattern = object : StrudelPattern.FixedWeight {
            override val steps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                return listOf(
                    StrudelPatternEvent(
                        begin = from,
                        end = to,
                        dur = to - from,
                        data = StrudelVoiceData.empty.copy(value = 4.asVoiceValue())
                    )
                )
            }
        }

        val pattern = RandLPattern(nPattern)
        val events = pattern.queryArcContextual(Rational.ZERO, Rational.ONE, QueryContext.empty)

        events.size shouldBe 4
    }

    "RandLPattern.create uses static path for fixed n" {
        val nPattern = AtomicPattern.pure
        val pattern = RandLPattern.create(nPattern, staticN = 3)

        val events = pattern.queryArcContextual(Rational.ZERO, Rational.ONE, QueryContext.empty)
        events.size shouldBe 3
    }
})
