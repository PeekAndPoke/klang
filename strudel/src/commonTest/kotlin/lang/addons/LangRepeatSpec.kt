package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.slow

class LangRepeatSpec : StringSpec({

    "repeat(2, note(\"a b\")) plays sequence twice sequentially" {
        // Given: a pattern that normally takes 1 cycle
        val p = repeat(2, note("a b"))

        // When: we query for 2 cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        // Then: we should see 4 events total (a b a b)
        events.size shouldBe 4

        // Cycle 0: a b
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Cycle 1: a b (repeating)
        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "b"
        events[3].part.begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[3].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "repeat(3) extends duration to 3 cycles" {
        val p = note("a").repeat(3)

        // It should estimate its duration as 3.0
        p.estimateCycleDuration().toDouble() shouldBe (3.0 plusOrMinus EPSILON)

        val events = p.queryArc(0.0, 3.0).sortedBy { it.part.begin }
        events.size shouldBe 3

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "repeat(1) returns original pattern" {
        val p = note("a").repeat(1)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        // Should behave exactly like normal note("a") -> loops every cycle
        events.size shouldBe 2
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "repeat(0) returns silence" {
        val p = note("a").repeat(0)
        val events = p.queryArc(0.0, 10.0)
        events shouldHaveSize 0
    }

    "repeat() respects internal duration of source pattern" {
        // Source is 2 cycles long
        val source = note("a b").slow(2)

        // Repeat it 2 times -> Total 4 cycles
        val p = source.repeat(2)

        val events = p.queryArc(0.0, 4.0).sortedBy { it.part.begin }

        // Cycle 0-2: a b (slowed)
        // Cycle 2-4: a b (slowed)
        events.size shouldBe 4

        // First repetition
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Second repetition
        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "b"
        events[3].part.begin.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "repeat() works as a string extension" {
        val p = "a b".repeat(2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[2].data.value?.asString shouldBeEqualIgnoringCase "a"
    }
})
