package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDensitySpec : StringSpec({

    "top-level density() sets VoiceData.density correctly" {
        // Given a simple sequence of density values within one cycle
        val p = density("1 3")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the density values in order
        events.size shouldBe 2
        events.map { it.data.density } shouldBe listOf(1.0, 3.0)
    }

    "control pattern density() sets VoiceData.density on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the density per step
        val p = base.density("2 4")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the density values in order
        events.map { it.data.density } shouldBe listOf(2.0, 4.0, 2.0, 4.0)
    }

    "density() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""density("1 3")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.density } shouldBe listOf(1.0, 3.0)
    }

    "density() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").density("1 3")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.density } shouldBe listOf(1.0, 3.0)
    }
})
