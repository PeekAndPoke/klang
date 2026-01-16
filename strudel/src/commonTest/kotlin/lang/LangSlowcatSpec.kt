package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangSlowcatSpec : StringSpec({

    "slowcat() plays patterns sequentially per cycle" {
        // slowcat("a", "b") should play "a" in the first cycle, "b" in the second
        val p = slowcat(note("a"), note("b"))
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2

        // Cycle 0
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Cycle 1
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "slowcat() repeats sequence after all patterns played" {
        val p = slowcat(note("a"), note("b"))
        // Query cycle 2 (third cycle) -> should be "a" again
        val events = p.queryArc(2.0, 3.0)

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "slowcatPrime() aliases to slowcat behavior" {
        val p = slowcatPrime(note("a"), note("b"))
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }
})
