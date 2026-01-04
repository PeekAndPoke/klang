package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangSpreadSpec : StringSpec({

    "top-level spread() sets VoiceData.panSpread correctly" {
        // Given a simple sequence of spread values within one cycle
        val p = spread("0.1 0.2")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the panSpread values in order
        events.size shouldBe 2
        events.map { it.data.panSpread } shouldBe listOf(0.1, 0.2)
    }

    "control pattern spread() sets VoiceData.panSpread on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the spread per step
        val p = base.spread("0.3 0.6")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the panSpread values in order
        events.map { it.data.panSpread } shouldBe listOf(0.3, 0.6, 0.3, 0.6)
    }
})
