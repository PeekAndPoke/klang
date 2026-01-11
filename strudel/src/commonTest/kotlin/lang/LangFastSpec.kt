package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFastSpec : StringSpec({

    "fast() compresses a pattern by the given factor" {
        // Given a pattern with two sounds in one cycle
        val p = sound("bd hh").fast(2)

        // When querying one cycle (should contain two full repetitions of "bd hh" if cycle was compressed by 2)
        // Wait, fast(2) makes it play twice as fast. So "bd hh" (normally 1 cycle) takes 0.5 cycles.
        // In 1.0 cycle, we should see "bd hh" twice if repeated? No, the pattern plays faster.
        // If the pattern is finite (like sound("bd hh")), fast(2) makes it shorter.
        // sound("bd hh") is infinite (implicitly cycles).
        // Let's assume standard behavior: it speeds up time.

        // When querying 0.5 cycles
        val events = p.queryArc(0.0, 0.5).sortedBy { it.begin }

        // It should contain the full "bd hh" sequence in 0.5 time
        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "hh"
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "fast() works as a standalone function" {
        val p = fast(2, sound("bd hh"))
        val events = p.queryArc(0.0, 0.5).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "fast() works as extension on String" {
        val p = "bd hh".fast(2)
        val events = p.queryArc(0.0, 0.5).sortedBy { it.begin }

        events.size shouldBe 2
        // "bd hh" -> note("bd hh") by default
        events[0].data.value?.asString shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "fast() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").fast(2)""")
        val events = p?.queryArc(0.0, 0.5)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "fast() as function works in compiled code" {
        val p = StrudelPattern.compile("""fast(2, sound("bd hh"))""")
        val events = p?.queryArc(0.0, 0.5)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }
})
