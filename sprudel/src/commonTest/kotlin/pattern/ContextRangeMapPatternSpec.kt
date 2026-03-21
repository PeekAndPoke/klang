package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan

class ContextRangeMapPatternSpec : StringSpec({

    "ContextRangeMapPattern remaps min/max in context" {
        val base = object : SprudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<SprudelPatternEvent> {
                val min = ctx.getOrDefault(ContinuousPattern.minKey, 0.0)
                val max = ctx.getOrDefault(ContinuousPattern.maxKey, 0.0)

                return listOf(
                    SprudelPatternEvent(
                        part = TimeSpan(from, to),
                        whole = TimeSpan(from, to),
                        data = SprudelVoiceData.empty.copy(
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
