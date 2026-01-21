package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class TimeShiftPatternSpec : StringSpec({

    "TimeShiftPattern shifts events forward" {
        val source = note("a") // Starts at 0, ends at 1
        val shift = 0.5.toRational() // 0.5
        val pattern = TimeShiftPattern.static(source, shift)

        val events = pattern.queryArc(0.0, 2.0)
        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (-0.5 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (2.5 plusOrMinus EPSILON)
    }

    "TimeShiftPattern shifts events backward" {
        val source = note("a")
        val shift = (-0.25).toRational()
        val pattern = TimeShiftPattern.static(source, shift)

        val events = pattern.queryArc(-1.0, 1.0)
        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (-1.25 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (-0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (-0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (1.75 plusOrMinus EPSILON)
    }
})
