package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangEarlySpec : StringSpec({

    "early() with 0 cycles does not shift the pattern" {
        val p1 = note("c d")
        val p2 = note("c d").early(0)

        val events1 = p1.queryArc(0.0, 1.0).sortedBy { it.begin }
        val events2 = p2.queryArc(0.0, 1.0).sortedBy { it.begin }

        events1.size shouldBe events2.size
        events1.forEachIndexed { i, ev ->
            ev.begin shouldBe events2[i].begin
            ev.end shouldBe events2[i].end
        }
    }

    "early(0.5) shifts pattern backward by half a cycle" {
        // Pattern repeats every cycle
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle 1: c(1.0-1.5), d(1.5-2.0)
        // After early(0.5): cycle 0: c(-0.5-0), d(0-0.5); cycle 1: c(0.5-1.0), d(1.0-1.5)
        // Query 0-1 shows: d from cycle 0 at 0-0.5, c from cycle 1 at 0.5-1.0
        val p = note("c d").early(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "d"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBe "c"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "early(1) shifts pattern backward by one cycle" {
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle 1: c(1.0-1.5), d(1.5-2.0)
        // After early(1): cycle 0: c(-1--0.5), d(-0.5-0); cycle 1: c(0-0.5), d(0.5-1.0)
        // Query 0-1 shows cycle 1 shifted to look like original cycle 0
        val p = note("c d").early(1.0)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "c"
        events[1].data.note shouldBe "d"
    }

    "early(1) query at shifted time shows previous cycle" {
        val p = note("c d").early(1.0)
        val events = p.queryArc(-1.0, 0.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "c"
        events[0].begin.toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (-0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBe "d"
        events[1].begin.toDouble() shouldBe (-0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "early() works as method on StrudelPattern" {
        val p = note("c d").early(0.25)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Original: c(0-0.5), d(0.5-1.0), next c(1.0-1.5)
        // After early(0.25): c(-0.25-0.25), d(0.25-0.75), c(0.75-1.25)
        // In range 0-1: partial c(0-0.25), d(0.25-0.75), partial c(0.75-1)
        events.size shouldBe 3
    }

    "early() works as extension on String" {
        val p = "c d".early(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "d"
        events[1].data.value?.asString shouldBe "c"
    }

    "early() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").early(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "d"
        events[1].data.note shouldBe "c"
    }

    "early() works as method in compiled code" {
        val p = StrudelPattern.compile("""note("c d e f").early(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1.0)
        // After early(0.5): shifts everything back, so we see second half of cycle 0 + first half of cycle 1
        events.size shouldBe 4
        events[0].data.note shouldBe "e"
        events[1].data.note shouldBe "f"
        events[2].data.note shouldBe "c"
        events[3].data.note shouldBe "d"
    }

    "early() works as string extension in compiled code" {
        val p = StrudelPattern.compile(""""c d".early(0.25)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 3
    }

    "early() with fractional cycles" {
        val p = note("c d e f").early(0.25)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1)
        // After early(0.25): c(-0.25-0), d(0-0.25), e(0.25-0.5), f(0.5-0.75), next c(0.75-1)
        events.size shouldBe 4
        events[0].data.note shouldBe "d"
        events[1].data.note shouldBe "e"
        events[2].data.note shouldBe "f"
        events[3].data.note shouldBe "c"
    }

    "early() with pattern parameter" {
        // Use seq pattern to vary the shift amount
        val p = note("c d e f").early(seq(0.0, 0.25))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should produce events, though exact behavior depends on sampling
        events.size shouldBe 4
    }

    "early() with continuous pattern like sine" {
        // Use sine to vary the shift amount continuously
        val p = note("c d").early(sine.range(0.0, 0.5))
        val events = p.queryArc(0.0, 1.0)

        // Should produce events with varying shifts based on sine wave
        // The exact count may vary (typically 2-3) due to the extended query margin and continuous offsets
        events.size shouldBe 3
        events.forEach { ev ->
            ev.data.note.shouldNotBeNull()
        }
    }
})
