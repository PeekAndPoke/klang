package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON

class LangPickResetSpec : StringSpec({

    "pickReset() resets pattern phase to event start" {
        val inner = seq("0 1 2 3")
        val lookup = listOf(inner)
        val selector = seq("0 ~ 0 ~")

        val result = selector.pickReset(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[0].data.value?.asDouble shouldBe 0.0

        // inner reset at 0.5 — phase aligns to event start
        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe 0.0
    }

    "pickReset() supports varargs style" {
        val pat1 = seq("0 1 2 3")
        val pat2 = seq("a b c d")

        val result = seq("0 1").pickReset(pat1, pat2)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 1.0

        events[2].data.value?.asString shouldBe "a"
        events[3].data.value?.asString shouldBe "b"
    }

    "pickReset() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("0 1"),
            "b" to seq("2 3")
        )
        val result = seq("a b").pickReset(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 2.0
    }

    "pickReset() works as pattern extension with List" {
        val selector = seq("0 ~ 0 ~")
        val result = selector.pickReset(listOf(seq("0 1 2 3")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 0.0  // phase reset
    }

    // -- pickmodReset tests --

    "pickmodReset() wraps indices and resets phase" {
        val inner = seq("0 1")
        val lookup = listOf(inner)
        val selector = seq("0 1 2 3")

        val result = selector.pickmodReset(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asDouble shouldBe 0.0
        }
    }

    "pickmodReset() supports varargs style" {
        val pat = seq("a b c d")
        val result = seq("0 1 2 3").pickmodReset(pat)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asString shouldBe "a"
        }
    }
})
