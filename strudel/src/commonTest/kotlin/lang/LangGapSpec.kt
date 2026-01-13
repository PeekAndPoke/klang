package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangGapSpec : StringSpec({

    "gap() with no arguments creates silence lasting 1 cycle" {
        val p = gap()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "gap(1) creates silence lasting 1 cycle" {
        val p = gap(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "gap(3) creates silence lasting 3 cycles" {
        val p = gap(3)
        // Should be empty in first 3 cycles
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 0
    }

    "gap(2) behaves like silence.slow(2)" {
        val p1 = gap(2)
        val p2 = silence.slow(2)

        p1.queryArc(0.0, 2.0).size shouldBe p2.queryArc(0.0, 2.0).size
        p1.queryArc(0.0, 2.0).size shouldBe 0
    }

    "gap() works as method on StrudelPattern" {
        // note("c d").gap(2) -> silence lasting 2 cycles
        val p = note("c d").gap(2)
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 0
    }

    "gap() works as extension on String" {
        // "a b c".gap(3)
        val p = "a b c".gap(3)
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 0
    }

    "gap() works in compiled code" {
        val p = StrudelPattern.compile("""gap(2)""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() works as method in compiled code" {
        // note("c d").gap(2)
        val p = StrudelPattern.compile("""note("c d").gap(2)""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() works as string extension in compiled code" {
        // "a b c".gap(3)
        val p = StrudelPattern.compile(""""a b c".gap(3)""")
        val events = p?.queryArc(0.0, 3.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() with fractional steps" {
        // gap(0.5) should create silence lasting half a cycle
        val p = gap(0.5)
        val events = p.queryArc(0.0, 0.5)

        events.size shouldBe 0
    }
})
