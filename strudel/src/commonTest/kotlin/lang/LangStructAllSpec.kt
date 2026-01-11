package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangStructAllSpec : StringSpec({

    "structAll() keeps multiple source events within one structure step" {
        // Given: source "c e" (c at 0-0.5, e at 0.5-1.0)
        // Struct: "x" (active 0-1.0)
        val p = "c e".structAll("x")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Unlike struct() which would pick only 'c', structAll keeps both
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[1].data.value?.asString shouldBe "e"
    }

    "structAll() clips source events to structure boundaries" {
        // Source: "c" (0-1)
        // Struct: "x ~" (active 0-0.5)
        val p = "c".structAll("x ~")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "structAll() works as standalone function" {
        val p = structAll("x", note("a b"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
    }

    "structAll() works in compiled code" {
        val p = StrudelPattern.compile("""note("a b").structAll("x")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
    }
})
