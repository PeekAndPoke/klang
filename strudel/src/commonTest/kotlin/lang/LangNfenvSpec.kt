package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfenvSpec : StringSpec({

    "top-level nfenv() sets VoiceData.nfenv correctly" {
        val p = nfenv("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfenv() sets VoiceData.nfenv on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfenv("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfenv } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfenv() works as string extension" {
        val p = "c3".nfenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfenv shouldBe 0.5
    }

    "nfenv() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfenv("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    "nfe() alias works as top-level function" {
        val p = nfe("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.3, 0.7)
    }

    "nfe() alias works as pattern extension" {
        val p = note("c d").nfe("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.4, 0.6)
    }

    "nfe() alias works as string extension" {
        val p = "e3".nfe("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfenv shouldBe 0.8
    }

    "nfe() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfe("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.2, 0.9)
    }
})
