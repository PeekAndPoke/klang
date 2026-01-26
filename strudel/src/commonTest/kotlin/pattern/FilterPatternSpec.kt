package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

class FilterPatternSpec : StringSpec({

    "FilterPattern filters events and preserves steps/weight" {
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
                        from,
                        from + Rational(0.5),
                        Rational(0.5),
                        StrudelVoiceData.empty.copy(note = "a")
                    ),
                    StrudelPatternEvent(
                        from + Rational(0.5),
                        to,
                        Rational(0.5),
                        StrudelVoiceData.empty.copy(note = "b")
                    ),
                )
            }
        }

        val filtered = FilterPattern(source) { it.data.note == "b" }

        filtered.weight shouldBe 1.0
        filtered.steps shouldBe Rational.ONE

        val events = filtered.queryArcContextual(Rational.ZERO, Rational.ONE, QueryContext.empty)
        events.size shouldBe 1
        events.first().data.note shouldBe "b"
    }
})
