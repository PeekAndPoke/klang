package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfdecaySpec : StringSpec({

    "top-level nfdecay() sets VoiceData.nfdecay correctly" {
        val p = nfdecay("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfdecay() sets VoiceData.nfdecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfdecay("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfdecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfdecay() works as string extension" {
        val p = "c3".nfdecay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfdecay shouldBe 0.5
    }

    "nfdecay() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfdecay("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    "nfd() alias works as top-level function" {
        val p = nfd("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.3, 0.7)
    }

    "nfd() alias works as pattern extension" {
        val p = note("c d").nfd("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.4, 0.6)
    }

    "nfd() alias works as string extension" {
        val p = "e3".nfd("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfdecay shouldBe 0.8
    }

    "nfd() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfd("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.2, 0.9)
    }
})
