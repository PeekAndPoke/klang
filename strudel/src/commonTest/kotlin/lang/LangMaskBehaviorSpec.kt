package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangMaskSpec : StringSpec({

    "mask() filters source events based on mask truthiness" {
        // note("c e").mask("x ~") -> should only keep "c"
        val p = note("c e").mask("x ~")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].data.note shouldBe "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "mask() works with boolean patterns" {
        // note("c [eb,g] d [eb,g]").mask("<1 [0 1]>")
        val p = note("c [eb,g] d [eb,g]").mask("<1 [0 1]>")

        // Cycle 1: 1 (keeps c and [eb,g]) -> 3 events
        // Cycle 2: [0 1] (keeps only second half: [eb,g]) -> 2 events
        // Total should be 5 events
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 5
        events.filter { it.begin.toDouble() < 1.0 }.size shouldBe 3
        events.filter { it.begin.toDouble() >= 1.0 }.size shouldBe 2
    }

    "mask() top-level function works" {
        val p = mask("x ~", note("c e"))

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "c"
    }

    "mask() as string extension works" {
        val p = "c e".mask("x ~")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
    }

    "mask() works in compiled code" {
        val p = StrudelPattern.compile("""note("c [eb,g]").mask("<1 0>")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // "<1 0>" means keep first half, silence second half.
        // note("c [eb,g]") has 'c' in first half.
        events.size shouldBe 1
        events[0].data.note shouldBe "c"
    }

    "mask() with continuous pattern" {
        // note("c*8").mask(square.fast(4))
        // square goes 0, 1, 0, 1 ... every cycle. fast(4) makes it 4 times per cycle.
        // This should effectively silence every other note.
        val p = note("c*8").mask(square.fast(4))

        val events = p.queryArc(0.0, 1.0)
        // c*8 = 8 events. square fast 4 has 4 truthy blocks.
        // 8 / 2 = 4 events should remain.
        events.size shouldBe 4
    }
})
