package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangUnisonSpec : StringSpec({

    "top-level unison() sets VoiceData.voices correctly" {
        // Given a simple sequence of unison voice counts within one cycle
        val p = unison("2 4")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the voices values in order
        events.size shouldBe 2
        events.map { it.data.voices } shouldBe listOf(2.0, 4.0)
    }

    "control pattern unison() sets VoiceData.voices on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the unison per step
        val p = base.unison("3 6")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the voices values in order
        events.map { it.data.voices } shouldBe listOf(3.0, 6.0, 3.0, 6.0)
    }

    "alias uni behaves like unison" {
        // Top-level creator alias
        val pTop = uni("1 5")
        val eventsTop = pTop.queryArc(0.0, 1.0)
        eventsTop.size shouldBe 2
        eventsTop.map { it.data.voices } shouldBe listOf(1.0, 5.0)

        // Modifier alias on existing pattern
        val base = note("c3 e3")
        val pCtrl = base.uni("2 3")
        val eventsCtrl = pCtrl.queryArc(0.0, 2.0)
        eventsCtrl.size shouldBe 4
        eventsCtrl.map { it.data.voices } shouldBe listOf(2.0, 3.0, 2.0, 3.0)
    }

    "unison() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""unison("2 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.voices } shouldBe listOf(2.0, 4.0)
    }

    "unison() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").unison("2 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.voices } shouldBe listOf(2.0, 4.0)
    }
})
