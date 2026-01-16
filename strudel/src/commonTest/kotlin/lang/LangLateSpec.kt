package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLateSpec : StringSpec({

    "late() with 0 cycles does not shift the pattern" {
        val p1 = note("c d")
        val p2 = note("c d").late(0)

        val events1 = p1.queryArc(0.0, 1.0).sortedBy { it.begin }
        val events2 = p2.queryArc(0.0, 1.0).sortedBy { it.begin }

        events1.size shouldBe events2.size
        events1.forEachIndexed { i, ev ->
            ev.begin shouldBe events2[i].begin
            ev.end shouldBe events2[i].end
        }
    }

    "late(0.5) shifts pattern forward by half a cycle" {
        // Pattern repeats every cycle
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle -1: c(-1--0.5), d(-0.5-0)
        // After late(0.5): cycle 0: c(0.5-1.0), d(1.0-1.5); cycle -1: c(-0.5-0), d(0-0.5)
        // Query 0-1 shows: d from cycle -1 at 0-0.5, c from cycle 0 at 0.5-1.0
        val p = note("c d").late(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "d"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "late(1) shifts pattern forward by one cycle" {
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle -1: c(-1--0.5), d(-0.5-0)
        // After late(1): cycle 0: c(1-1.5), d(1.5-2); cycle -1: c(0-0.5), d(0.5-1.0)
        // Query 0-1 shows cycle -1 shifted to look like original cycle 0
        val p = note("c d").late(1.0)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "late(1) query at shifted time shows next cycle" {
        val p = note("c d").late(1.0)
        val events = p.queryArc(1.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "late() works as method on StrudelPattern" {
        val p = note("c d").late(0.25)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Original: cycle -1: d(-0.5-0); cycle 0: c(0-0.5), d(0.5-1.0)
        // After late(0.25): cycle -1: d(-0.25-0.25); cycle 0: c(0.25-0.75), d(0.75-1.25)
        // In range 0-1: partial d(0-0.25), c(0.25-0.75), partial d(0.75-1)
        events.size shouldBe 3
    }

    "late() works as extension on String" {
        val p = "c d".late(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "d"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "c"
    }

    "late() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").late(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "d"
        events[1].data.note shouldBeEqualIgnoringCase "c"
    }

    "late() works as method in compiled code" {
        val p = StrudelPattern.compile("""note("c d e f").late(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1.0)
        // After late(0.5): shifts everything forward, so we see second half of cycle -1 + first half of cycle 0
        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "e"
        events[1].data.note shouldBeEqualIgnoringCase "f"
        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "late() works as string extension in compiled code" {
        val p = StrudelPattern.compile(""""c d".late(0.25)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 3
    }

    "late() with fractional cycles" {
        val p = note("c d e f").late(0.25)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1)
        // After late(0.25): c(0.25-0.5), d(0.5-0.75), e(0.75-1), prev f(-0.25-0), so f(0-0.25) in next cycle iteration
        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "f"
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[2].data.note shouldBeEqualIgnoringCase "d"
        events[3].data.note shouldBeEqualIgnoringCase "e"
    }

    "late() and early() are inverse operations" {
        val original = note("c d e f")
        val shifted = original.late(0.5).early(0.5)

        val events1 = original.queryArc(0.0, 1.0).sortedBy { it.begin }
        val events2 = shifted.queryArc(0.0, 1.0).sortedBy { it.begin }

        events1.size shouldBe events2.size
        events1.forEachIndexed { i, ev ->
            ev.begin.toDouble() shouldBe (events2[i].begin.toDouble() plusOrMinus EPSILON)
            ev.end.toDouble() shouldBe (events2[i].end.toDouble() plusOrMinus EPSILON)
            ev.data.note shouldBe events2[i].data.note
        }
    }
})
