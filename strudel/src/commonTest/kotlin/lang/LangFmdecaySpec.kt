package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFmdecaySpec : StringSpec({

    "top-level fmdecay() sets VoiceData.fmDecay correctly" {
        val p = fmdecay("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmdecay() sets VoiceData.fmDecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmdecay("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmDecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmdecay() works as string extension" {
        val p = "c3".fmdecay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmDecay shouldBe 0.5
    }

    "fmdecay() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmdecay("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

    "fmdec() alias works as top-level function" {
        val p = fmdec("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.3, 0.7)
    }

    "fmdec() alias works as pattern extension" {
        val p = note("c d").fmdec("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.4, 0.6)
    }

    "fmdec() alias works as string extension" {
        val p = "e3".fmdec("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmDecay shouldBe 0.8
    }

    "fmdec() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmdec("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.2, 0.9)
    }
})
