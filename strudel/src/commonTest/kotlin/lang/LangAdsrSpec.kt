package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangAdsrSpec : StringSpec({

    "top-level adsr() sets VoiceData.adsr components correctly" {
        // Two ADSR steps in one cycle
        val p = adsr("0.2:0.3:0.8:0.5 0.1:0.2:0.7:0.9")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { it.data.adsr.attack } shouldBe listOf(0.2, 0.1)
        events.map { it.data.adsr.decay } shouldBe listOf(0.3, 0.2)
        events.map { it.data.adsr.sustain } shouldBe listOf(0.8, 0.7)
        events.map { it.data.adsr.release } shouldBe listOf(0.5, 0.9)
    }

    "control pattern adsr() sets VoiceData.adsr components on existing pattern" {
        val base = note("c3 e3")
        val p = base.adsr("0.01:0.2:0.9:0.7 0.02:0.3:0.8:0.6")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        events.map { it.data.adsr.attack } shouldBe listOf(0.01, 0.02, 0.01, 0.02)
        events.map { it.data.adsr.decay } shouldBe listOf(0.2, 0.3, 0.2, 0.3)
        events.map { it.data.adsr.sustain } shouldBe listOf(0.9, 0.8, 0.9, 0.8)
        events.map { it.data.adsr.release } shouldBe listOf(0.7, 0.6, 0.7, 0.6)
    }
})
