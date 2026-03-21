package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.StrudelPatternEvent
import io.peekandpoke.klang.sprudel.StrudelVoiceData
import io.peekandpoke.klang.sprudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan

class RandLPatternSpec : StringSpec({

    "RandLPattern emits n events per control window" {
        val nPattern = object : StrudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                return listOf(
                    StrudelPatternEvent(
                        part = TimeSpan(from, to),
                        whole = TimeSpan(from, to),
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
