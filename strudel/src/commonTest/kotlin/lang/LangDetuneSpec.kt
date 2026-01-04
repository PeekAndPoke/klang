package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangDetuneSpec : StringSpec({

    "top-level detune() sets VoiceData.freqSpread correctly" {
        // Given a simple sequence of detune values within one cycle
        val p = detune("0.1 0.25")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the freqSpread values in order
        events.size shouldBe 2
        events.map { it.data.freqSpread } shouldBe listOf(0.1, 0.25)
    }

    "control pattern detune() sets VoiceData.freqSpread on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the detune per step
        val p = base.detune("0.05 0.15")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the freqSpread values in order
        events.map { it.data.freqSpread } shouldBe listOf(0.05, 0.15, 0.05, 0.15)
    }
})
