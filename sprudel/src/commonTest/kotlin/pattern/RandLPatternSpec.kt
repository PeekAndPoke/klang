package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

class RandLPatternSpec : StringSpec({

    "RandLPattern emits n events per control window" {
        val nPattern = object : SprudelPattern.FixedWeight {
            override val numSteps: Double = 1.0

            override fun queryArcContextual(
                from: CycleTime,
                to: CycleTime,
                ctx: QueryContext,
            ): List<SprudelPatternEvent> {
                return listOf(
                    SprudelPatternEvent(
                        part = CycleTimeSpan(from, to),
                        whole = CycleTimeSpan(from, to),
                        data = SprudelVoiceData(value = 4.asVoiceValue())
                    )
                )
            }
        }

        val pattern = RandLPattern(nPattern)
        val events = pattern.queryArcContextual(0.0, 1.0, QueryContext.empty)

        events.size shouldBe 4
    }

    "RandLPattern.create uses static path for fixed n" {
        val nPattern = AtomicPattern.pure
        val pattern = RandLPattern.create(nPattern, staticN = 3)

        val events = pattern.queryArcContextual(0.0, 1.0, QueryContext.empty)
        events.size shouldBe 3
    }
})
