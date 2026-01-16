package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangStackOperationsSpec : StringSpec({

    "stackBy() aligns patterns with different durations" {
        // pattern 1: 1 cycle duration
        val p1 = note("c")
        // pattern 2: 2 cycles duration (slow(2))
        val p2 = note("e").slow(2)

        // stackBy(0.5) -> center alignment
        // p1 (dur=1) should be centered relative to p2 (dur=2)
        // p2 starts at 0.0, ends at 2.0
        // p1 should start at (2.0 - 1.0) * 0.5 = 0.5
        val p = stackBy(0.5, p1, p2)

        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2

        // e starts at 0.0, ends at 2.0
        val eEvent = events.find { it.data.note?.lowercase() == "e" }!!
        eEvent.begin shouldBe Rational.ZERO
        eEvent.end shouldBe Rational(2)

        // c starts at 0.5, ends at 1.5
        val cEvent = events.find { it.data.note?.lowercase() == "c" }!!
        cEvent.begin shouldBe 0.5.toRational()
        cEvent.end shouldBe 1.5.toRational()
    }

    "stackLeft() aligns to the start" {
        val p1 = note("c")
        val p2 = note("e").slow(2)

        val p = stackLeft(p1, p2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Both start at 0.0
        events.find { it.data.note?.lowercase() == "e" }!!.begin shouldBe Rational.ZERO
        events.find { it.data.note?.lowercase() == "c" }!!.begin shouldBe Rational.ZERO
    }

    "stackRight() aligns to the end" {
        val p1 = note("c")
        val p2 = note("e").slow(2)

        val p = stackRight(p1, p2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // p2 (dur=2) starts at 0.0
        events.find { it.data.note?.lowercase() == "e" }!!.begin shouldBe Rational.ZERO

        // p1 (dur=1) should start at 1.0 so it ends at 2.0
        events.find { it.data.note?.lowercase() == "c" }!!.begin shouldBe Rational.ONE
    }

    "stackCentre() aligns to the center" {
        val p1 = note("c")
        val p2 = note("e").slow(2)

        val p = stackCentre(p1, p2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // p1 (dur=1) should start at 0.5
        events.find { it.data.note?.lowercase() == "c" }!!.begin shouldBe 0.5.toRational()
    }

    "stackBy works with ArrangementPattern" {
        // [1 cycle of a, 1 cycle of b] -> total 2 cycles
        val p1 = arrange(note("a"), note("b"))
        // [1 cycle of c] -> total 1 cycle
        val p2 = note("c")

        // Align p2 to the center of p1
        val p = stackCentre(p1, p2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // p1 starts at 0.0, dur 2.0
        // p2 (dur 1) should start at 0.5 relative to p1
        events.find { it.data.note?.lowercase() == "c" }!!.begin shouldBe 0.5.toRational()
    }
})
