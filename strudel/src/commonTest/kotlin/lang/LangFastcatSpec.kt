package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangFastcatSpec : StringSpec({

    "fastcat() squashes patterns into one cycle" {
        // fastcat("a", "b") should play "a" then "b" within a single cycle (0.0 to 1.0)
        val p = fastcat(note("a"), note("b"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "fastcat() works as a string extension" {
        val p = "a".fastcat("b")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b"
    }
})
