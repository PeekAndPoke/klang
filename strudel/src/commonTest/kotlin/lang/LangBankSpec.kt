package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBankSpec : StringSpec({

    "top-level bank() sets VoiceData.bank correctly" {
        // Given a simple sequence of banks within one cycle
        val p = bank("MPC Akai")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the bank values in order
        events.size shouldBe 2
        events.map { it.data.bank } shouldBe listOf("MPC", "Akai")
    }

    "control pattern bank() sets VoiceData.bank on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the bank per step
        val p = base.bank("A B")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the bank values in order
        events.map { it.data.bank } shouldBe listOf("A", "B", "A", "B")
    }
})
