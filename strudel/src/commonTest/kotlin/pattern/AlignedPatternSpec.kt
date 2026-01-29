package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational

class AlignedPatternSpec : StringSpec({

    "AlignedPattern aligns to left, center, right" {
        val source = object : StrudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE
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

        val left = AlignedPattern(
            source = source,
            sourceDuration = Rational.ONE,
            targetDuration = Rational(2),
            alignment = 0.0
        )

        val center = AlignedPattern(
            source = source,
            sourceDuration = Rational.ONE,
            targetDuration = Rational(2),
            alignment = 0.5
        )

        val right = AlignedPattern(
            source = source,
            sourceDuration = Rational.ONE,
            targetDuration = Rational(2),
            alignment = 1.0
        )

        val leftEvt: StrudelPatternEvent =
            left.queryArcContextual(from = Rational.ZERO, to = Rational(2), ctx = QueryContext.empty).first()

        val centerEvt: StrudelPatternEvent =
            center.queryArcContextual(from = Rational.ZERO, to = Rational(2), ctx = QueryContext.empty).first()

        val rightEvt: StrudelPatternEvent =
            right.queryArcContextual(from = Rational.ZERO, to = Rational(2), ctx = QueryContext.empty).first()

        leftEvt.begin.toDouble() shouldBe 0.0
        leftEvt.end.toDouble() shouldBe 1.0

        centerEvt.begin.toDouble() shouldBe 0.5
        centerEvt.end.toDouble() shouldBe 1.5

        rightEvt.begin.toDouble() shouldBe 1.0
        rightEvt.end.toDouble() shouldBe 2.0
    }
})
