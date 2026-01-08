package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPanSpec : StringSpec({

    "top-level pan() sets VoiceData.pan correctly" {
        // Given a simple sequence of pan values within one cycle
        val p = pan("-0.5 0.75")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the pan values in order
        events.size shouldBe 2
        events.map { it.data.pan } shouldBe listOf(-0.5, 0.75)
    }

    "control pattern pan() sets VoiceData.pan on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the pan per step
        val p = base.pan("-1.0 1.0")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the pan values in order
        events.map { it.data.pan } shouldBe listOf(-1.0, 1.0, -1.0, 1.0)
    }

    "pan() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""pan("-0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pan } shouldBe listOf(-0.5, 0.75)
    }

    "pan() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").pan("-0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pan } shouldBe listOf(-0.5, 0.75)
    }
})
