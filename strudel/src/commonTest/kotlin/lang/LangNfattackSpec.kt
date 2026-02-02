package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfattackSpec : StringSpec({

    "top-level nfattack() sets VoiceData.nfattack correctly" {
        val p = nfattack("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfattack() sets VoiceData.nfattack on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfattack("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfattack } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfattack() works as string extension" {
        val p = "c3".nfattack("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfattack shouldBe 0.5
    }

    "nfattack() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfattack("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    "nfa() alias works as top-level function" {
        val p = nfa("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.3, 0.7)
    }

    "nfa() alias works as pattern extension" {
        val p = note("c d").nfa("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.4, 0.6)
    }

    "nfa() alias works as string extension" {
        val p = "e3".nfa("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfattack shouldBe 0.8
    }

    "nfa() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfa("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.2, 0.9)
    }
})
