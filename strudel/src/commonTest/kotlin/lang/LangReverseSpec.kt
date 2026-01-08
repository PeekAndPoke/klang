package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRevSpec : StringSpec({

    "rev() reverses a pattern within each cycle" {
        // Given a simple sequence [a b] (a: 0..0.5, b: 0.5..1.0)
        val p = note("a b").rev()

        // When querying cycle 0
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Then b should come first and a should come second
        events.size shouldBe 2

        // Original b (0.5..1.0) becomes (1 - 1.0 .. 1 - 0.5) = (0.0 .. 0.5)
        events[0].data.note shouldBe "b"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        // Original a (0.0..0.5) becomes (1 - 0.5 .. 1 - 0.0) = (0.5 .. 1.0)
        events[1].data.note shouldBe "a"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "rev() handles multiple cycles independently" {
        val p = note("a b").rev()

        // When querying cycle 1 (1.0..2.0)
        val events = p.queryArc(1.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2

        // Cycle 1 reverse mapping: t' = 1 + 2*1 - t = 3 - t
        // b at 1.5..2.0 becomes 3-2.0 .. 3-1.5 = 1.0 .. 1.5
        events[0].data.note shouldBe "b"
        events[0].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // a at 1.0..1.5 becomes 3-1.5 .. 3-1.0 = 1.5 .. 2.0
        events[1].data.note shouldBe "a"
        events[1].begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
    }

    "rev() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").rev()""")

        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "b"
        events[1].data.note shouldBe "a"
    }
})
