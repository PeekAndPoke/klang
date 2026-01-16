package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangPolymeterSpec : StringSpec({

    "polymeter() aligns patterns based on LCM of step counts (2 vs 3)" {
        // "a b" has 2 steps. "c d e" has 3 steps. LCM is 6.
        // "a b" fits into 6 steps -> repeats 3 times in 1 cycle
        // "c d e" fits into 6 steps -> repeats 2 times in 1 cycle
        val p = polymeter(note("a b"), note("c d e"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Expected: a b a b a b (6 events) + c d e c d e (6 events) = 12 events total
        events.size shouldBe 12

        val aEvents = events.filter { it.data.note?.lowercase() == "a" }
        aEvents.size shouldBe 3 // "a" appears 3 times

        val cEvents = events.filter { it.data.note?.lowercase() == "c" }
        cEvents.size shouldBe 2 // "c" appears 2 times

        // Check timing of first "a" and "c" - should start together
        aEvents[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        cEvents[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "polymeter() preserves structure for single patterns" {
        // "a b" (2 steps) aligned to LCM(2) = 2 steps
        // Should play "a b" in 1 cycle, not sped up to 4
        val p = polymeter(note("a b"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }

    "polymeter() with gap() contributes to step count" {
        // gap(3) has 3 steps (silence). "a b" has 2 steps. LCM is 6.
        // "a b" -> repeats 3 times (6 events)
        // gap(3) -> repeats 2 times (0 events, just silence)
        // Total visible events: 6
        val p = polymeter(note("a b"), gap(3))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 6
        events.map { it.data.note?.lowercase() } shouldBe listOf("a", "b", "a", "b", "a", "b")
    }

    "polymeter() with euclid()" {
        // "a(3,8)" has 8 steps. "b c" has 2 steps. LCM is 8.
        // "a(3,8)" -> repeats 1 time (3 events)
        // "b c" -> repeats 4 times (8 events)
        // Total: 11 events
        val p = polymeter(note("a").euclid(3, 8), note("b c"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 11
        events.count { it.data.note?.lowercase() == "a" } shouldBe 3
        events.count { it.data.note?.lowercase() == "b" } shouldBe 4
        events.count { it.data.note?.lowercase() == "c" } shouldBe 4
    }

    "polymeter() ignores patterns without steps (e.g. pure)" {
        // pure("a") has 1 step (AtomicPattern default). "b c" has 2 steps. LCM is 2.
        // pure("a") -> repeats 2 times -> "a a"
        // "b c" -> repeats 1 time -> "b c"
        val p = polymeter(pure("a"), note("b c"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 4
        val aEvents = events.filter { it.data.value?.asString == "a" }
        aEvents.size shouldBe 2
    }

    // -- polymeterSteps

    "polymeterSteps() uses explicit step count" {
        // "a b" (2 steps) aligned to explicit 2 steps
        // Should play normally "a b" once in 1 cycle
        val p = polymeterSteps(2, note("a b"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "polymeterSteps() speeds up pattern to fit higher step count" {
        // "a" (1 step) aligned to 4 steps
        // Should play "a a a a" in 1 cycle
        val p = polymeterSteps(4, note("a"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.forEach { it.data.note shouldBeEqualIgnoringCase "a" }
    }

    "polymeterSteps() slows down pattern to fit lower step count" {
        // "a b c d" (4 steps) aligned to 2 steps
        // Should play "a b" in 1 cycle (effectively half speed, first half of pattern)
        // Wait, polymeter stretches the pattern.
        // fast(target / source) = fast(2/4) = slow(2)
        // So "a b c d" becomes length 2 cycles.
        // In 1 cycle (0..1), we should hear "a b"
        val p = polymeterSteps(2, note("a b c d"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }
})
