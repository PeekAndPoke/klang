package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangSqueezeSpec : StringSpec({

    "squeeze() works with swapped arguments (selector, lookup)" {
        // squeeze("0 1", [pat1, pat2])
        val pat1 = seq("bd hh")
        val pat2 = seq("sn cp")
        val selector = "0 1" // 0.0-0.5, 0.5-1.0

        val result = squeeze(selector, listOf(pat1, pat2))
        val events = result.queryArc(0.0, 1.0)

        // Same as inhabit: 4 events (2 from each squeezed into 0.5)
        events shouldHaveSize 4

        // 0.0-0.25: bd (pat1 1st half)
        events[0].data.value?.asString shouldBe "bd"
        // 0.25-0.5: hh (pat1 2nd half)
        events[1].data.value?.asString shouldBe "hh"
        // 0.5-0.75: sn (pat2 1st half)
        events[2].data.value?.asString shouldBe "sn"
        // 0.75-1.0: cp (pat2 2nd half)
        events[3].data.value?.asString shouldBe "cp"
    }

    "squeeze() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("bd"),
            "b" to seq("sn")
        )
        val result = squeeze("a b", lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }

    "squeeze() works as pattern extension" {
        val selector = seq("0 1")
        val result = selector.squeeze(listOf(seq("bd"), seq("sn")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }

    "squeeze() works as string extension" {
        val result = "0 1".squeeze(listOf(seq("bd"), seq("sn")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }

    "squeeze() with spread arguments (selector first?)" {
        // Warning: squeeze usually takes (selector, lookup).
        // If we spread args: squeeze(selector, val1, val2, ...)?
        // Strudel JS squeeze(pat, values) where values is array.
        // If we support spread: squeeze(selector, pat1, pat2).

        val result = squeeze("0 1", seq("bd"), seq("sn"))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }
})
