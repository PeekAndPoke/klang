package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.steady

class TimeShiftPatternWithControlSpec : StringSpec({

    "TimeShiftPatternWithControl samples offset from pattern" {
        val source = note("a")
        val offset = steady(0.25)
        val pattern = TimeShiftPatternWithControl(source, offset)

        val events = pattern.queryArc(0.0, 2.0)
        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (-0.75 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (2.25 plusOrMinus EPSILON)
    }
})
