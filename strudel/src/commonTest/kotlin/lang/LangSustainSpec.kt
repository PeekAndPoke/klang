package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSustainSpec : StringSpec({

    "top-level sustain() sets VoiceData.adsr.sustain correctly" {
        val p = sustain("0.7 0.5")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.sustain } shouldBe listOf(0.7, 0.5)
    }

    "control pattern sustain() sets VoiceData.adsr.sustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.sustain("0.8 0.6")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.adsr.sustain } shouldBe listOf(0.8, 0.6, 0.8, 0.6)
    }

    "sustain() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""sustain("0.7 0.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.sustain } shouldBe listOf(0.7, 0.5)
    }

    "sustain() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").sustain("0.7 0.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.sustain } shouldBe listOf(0.7, 0.5)
    }
})
