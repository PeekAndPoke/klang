package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

class StepsOverridePatternSpec : StringSpec({

    "StepsOverridePattern overrides steps while delegating behavior" {
        val source = object : StrudelPattern.FixedWeight {
            override val steps: Rational = Rational.ONE
            override fun estimateCycleDuration(): Rational = Rational.ONE

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
                        data = StrudelVoiceData.empty
                    )
                )
            }
        }

        val wrapped = StepsOverridePattern(source, Rational(6))
        wrapped.steps shouldBe Rational(6)

        val events = wrapped.queryArcContextual(Rational.ZERO, Rational.ONE, QueryContext.empty)
        events.size shouldBe 1
    }
})
