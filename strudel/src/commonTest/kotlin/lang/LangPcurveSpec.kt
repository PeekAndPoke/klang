package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPcurveSpec : StringSpec({

    "top-level pcurve() sets VoiceData.pCurve correctly" {
        val p = pcurve("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.5, 1.0)
    }

    "control pattern pcurve() sets VoiceData.pCurve on existing pattern" {
        val base = note("c3 e3")
        val p = base.pcurve("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.pCurve } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "pcurve() works as string extension" {
        val p = "c3".pcurve("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pCurve shouldBe 0.5
    }

    "pcurve() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").pcurve("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.5, 1.0)
    }

    "pcrv() alias works as top-level function" {
        val p = pcrv("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.3, 0.7)
    }

    "pcrv() alias works as pattern extension" {
        val p = note("c d").pcrv("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.4, 0.6)
    }

    "pcrv() alias works as string extension" {
        val p = "e3".pcrv("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.pCurve shouldBe 0.8
    }

    "pcrv() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").pcrv("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.2, 0.9)
    }
})
