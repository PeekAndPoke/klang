package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan
import io.peekandpoke.klang.strudel.math.Rational

class ContextRangeMapPatternSpec : StringSpec({

    "ContextRangeMapPattern remaps min/max in context" {
        val base = object : StrudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                val min = ctx.getOrDefault(ContinuousPattern.minKey, 0.0)
                val max = ctx.getOrDefault(ContinuousPattern.maxKey, 0.0)

                return listOf(
                    StrudelPatternEvent(
                        part = TimeSpan(from, to),
                        whole = TimeSpan(from, to),
                        data = StrudelVoiceData.empty.copy(
                            value = (min + max).asVoiceValue()
                        )
                    )
                )
            }
        }

        val wrapped = ContextRangeMapPattern(
            source = base,
            transformMin = { it + 1.0 },
            transformMax = { it * 2.0 }
        )

        val ctx = QueryContext.empty.update {
            set(ContinuousPattern.minKey, 2.0)   // -> 3.0
            set(ContinuousPattern.maxKey, 5.0)   // -> 10.0
        }

        val events = wrapped.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)
        val value = events.first().data.value?.asDouble

        value shouldBe 13.0
    }
})
