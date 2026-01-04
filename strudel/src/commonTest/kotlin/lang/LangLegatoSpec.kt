package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangLegatoSpec : StringSpec({

    "top-level legato() sets VoiceData.legato correctly" {
        // Given a simple sequence of legato values within one cycle
        val p = legato("0.25 0.75")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the legato values in order
        events.size shouldBe 2
        events.map { it.data.legato } shouldBe listOf(0.25, 0.75)
    }

    "control pattern legato() sets VoiceData.legato on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the legato per step
        val p = base.legato("0.1 0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the legato values in order
        events.map { it.data.legato } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "alias clip behaves like legato" {
        // Given: use alias clip (both top-level creator and modifier exist)
        val pTop = clip("0.3 0.6")
        val eventsTop = pTop.queryArc(0.0, 1.0)
        eventsTop.size shouldBe 2
        eventsTop.map { it.data.legato } shouldBe listOf(0.3, 0.6)

        val base = note("c3 e3")
        val pCtrl = base.clip("0.8 0.4")
        val eventsCtrl = pCtrl.queryArc(0.0, 2.0)
        eventsCtrl.size shouldBe 4
        eventsCtrl.map { it.data.legato } shouldBe listOf(0.8, 0.4, 0.8, 0.4)
    }
})
