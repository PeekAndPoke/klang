package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangGainSpec : StringSpec({

    "top-level gain() sets VoiceData.gain correctly" {
        // Given a simple sequence of gain values within one cycle
        val p = gain("0.5 1.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the gain values in order
        events.size shouldBe 2
        events.map { it.data.gain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern gain() sets VoiceData.gain on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the gain per step
        val p = base.gain("0.1 0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the gain values in order
        events.map { it.data.gain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }
})
