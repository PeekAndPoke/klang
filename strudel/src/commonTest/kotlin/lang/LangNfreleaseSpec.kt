package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfreleaseSpec : StringSpec({

    "top-level nfrelease() sets VoiceData.nfrelease correctly" {
        val p = nfrelease("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfrelease() sets VoiceData.nfrelease on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfrelease("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfrelease } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfrelease() works as string extension" {
        val p = "c3".nfrelease("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfrelease shouldBe 0.5
    }

    "nfrelease() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfrelease("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    "nfr() alias works as top-level function" {
        val p = nfr("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.3, 0.7)
    }

    "nfr() alias works as pattern extension" {
        val p = note("c d").nfr("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.4, 0.6)
    }

    "nfr() alias works as string extension" {
        val p = "e3".nfr("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfrelease shouldBe 0.8
    }

    "nfr() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfr("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.2, 0.9)
    }
})
