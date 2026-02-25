package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe

class LangPickRestartSpec : StringSpec({

    "pickRestart() restarts pattern at event start" {
        val inner = seq("0 1 2 3")
        val lookup = listOf(inner)
        val selector = seq("0 ~ 0 ~")

        val result = selector.pickRestart(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        events[0].part.begin.toDouble() shouldBeExactly 0.0
        events[0].part.end.toDouble() shouldBeExactly 0.25
        events[0].data.value?.asDouble shouldBe 0.0

        // inner restarted at 0.5, so first event of inner ("0") plays again
        events[1].part.begin.toDouble() shouldBeExactly 0.5
        events[1].part.end.toDouble() shouldBeExactly 0.75
        events[1].data.value?.asDouble shouldBe 0.0
    }

    "pickRestart() supports varargs style" {
        val pat1 = seq("0 1 2 3")
        val pat2 = seq("a b c d")

        val result = seq("0 1").pickRestart(pat1, pat2)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 1.0

        events[2].data.value?.asString shouldBe "a"
        events[3].data.value?.asString shouldBe "b"
    }

    "pickRestart() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("0 1"),
            "b" to seq("2 3")
        )
        val result = seq("a b").pickRestart(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 2.0
    }

    "pickRestart() works as pattern extension with List" {
        val selector = seq("0 ~ 0 ~")
        val result = selector.pickRestart(listOf(seq("0 1 2 3")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 0.0  // restarted
    }

    // -- pickmodRestart tests --

    "pickmodRestart() wraps indices and restarts" {
        val inner = seq("0 1")
        val lookup = listOf(inner)
        val selector = seq("0 1 2 3")

        val result = selector.pickmodRestart(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asDouble shouldBe 0.0
        }
    }

    "pickmodRestart() supports varargs style" {
        val pat = seq("a b c d")
        val result = seq("0 1 2 3").pickmodRestart(pat)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asString shouldBe "a"
        }
    }

    "pick() (standard) does NOT restart (contrast test)" {
        val inner = seq("0 1 2 3")
        val lookup = listOf(inner)
        val selector = seq("0 ~ 0 ~")

        val result = selector.pick(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        events[0].part.begin.toDouble() shouldBeExactly 0.0
        events[0].data.value?.asDouble shouldBe 0.0

        // Standard pick queries inner at 0.5 → gets "2"
        events[1].part.begin.toDouble() shouldBeExactly 0.5
        events[1].data.value?.asDouble shouldBe 2.0
    }
})