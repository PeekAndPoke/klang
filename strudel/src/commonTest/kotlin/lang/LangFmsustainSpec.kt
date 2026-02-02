package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFmsustainSpec : StringSpec({

    "top-level fmsustain() sets VoiceData.fmSustain correctly" {
        val p = fmsustain("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmsustain() sets VoiceData.fmSustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmsustain("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmSustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmsustain() works as string extension" {
        val p = "c3".fmsustain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmSustain shouldBe 0.5
    }

    "fmsustain() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmsustain("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

    "fmsus() alias works as top-level function" {
        val p = fmsus("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.3, 0.7)
    }

    "fmsus() alias works as pattern extension" {
        val p = note("c d").fmsus("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.4, 0.6)
    }

    "fmsus() alias works as string extension" {
        val p = "e3".fmsus("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmSustain shouldBe 0.8
    }

    "fmsus() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmsus("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.2, 0.9)
    }
})
