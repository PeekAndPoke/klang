package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.struct
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class StructPatternSpec : StringSpec({

    "StructPattern: rhythm is inherited from the structure" {
        // Source plays a long C3
        val source = note("c3")
        // Structure defines 4 steps, but only 1st and 3rd are active 'x'
        val pattern = source.struct("x ~ x ~")

        val events = pattern.queryArc(0.0.toRational(), 1.0.toRational()).sortedBy { it.begin }

        events.size shouldBe 2

        // First event (from first 'x')
        events[0].data.note shouldBe "c3"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        // Second event (from second 'x')
        events[1].data.note shouldBe "c3"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "StructPattern: clips source events to structural boundaries" {
        // Source plays two fast notes in the first half cycle
        val source = note("c3 d3").fast(2)
        // Structure is one long 'x' for the whole cycle
        val pattern = source.struct("x")

        val events = pattern.queryArc(0.0.toRational(), 1.0.toRational()).sortedBy { it.begin }

        events.size shouldBe 4

        events[0].data.note shouldBe "c3"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].data.note shouldBe "d3"
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[2].data.note shouldBe "c3"
        events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

        events[3].data.note shouldBe "d3"
        events[3].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[3].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }
})
