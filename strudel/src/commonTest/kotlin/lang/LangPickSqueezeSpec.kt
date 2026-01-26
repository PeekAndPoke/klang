package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangPickSqueezeSpec : StringSpec({

    "inhabit() squeezes patterns into selector events" {
        // Two patterns, each with 2 events
        val lookup: List<Any> = listOf(
            seq("bd hh"),
            seq("sn cp")
        )
        // Selector has 2 events, each 0.5 duration
        val selector = seq("0 1")

        val result = inhabit(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Should have 4 events total because each picked pattern (2 events)
        // is squeezed into one selector event.
        events shouldHaveSize 4

        // 1st quarter: bd (from 1st pattern, squeezed into 0.0-0.5)
        events[0].data.note shouldBe "bd"
        events[0].begin.toDouble() shouldBe 0.0
        events[0].end.toDouble() shouldBe 0.25

        // 2nd quarter: hh
        events[1].data.note shouldBe "hh"
        events[1].begin.toDouble() shouldBe 0.25
        events[1].end.toDouble() shouldBe 0.5

        // 3rd quarter: sn (from 2nd pattern, squeezed into 0.5-1.0)
        events[2].data.note shouldBe "sn"
        events[2].begin.toDouble() shouldBe 0.5
        events[2].end.toDouble() shouldBe 0.75

        // 4th quarter: cp
        events[3].data.note shouldBe "cp"
        events[3].begin.toDouble() shouldBe 0.75
        events[3].end.toDouble() shouldBe 1.0
    }

    "inhabit() works with string pattern method" {
        val result = "0 1".inhabit(
            listOf(
                seq("bd hh"),
                seq("sn cp")
            )
        )
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 4
        events[0].data.note shouldBe "bd"
        events[3].data.note shouldBe "cp"
    }

    "inhabit() works with map lookup" {
        val lookup = mapOf(
            "a" to seq("bd hh"),
            "b" to seq("sn cp")
        )
        val result = "a b".inhabit(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events[0].data.note shouldBe "bd"
        events[2].data.note shouldBe "sn"
    }

    "pickSqueeze() is alias for inhabit()" {
        val lookup = listOf(seq("bd"))
        val result = "0".pickSqueeze(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 1
        events[0].data.note shouldBe "bd"
    }
})
