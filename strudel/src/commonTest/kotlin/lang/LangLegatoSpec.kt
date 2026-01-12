package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

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

    "legato() works as string extension" {
        val p = "c3".legato("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.legato shouldBe 0.5
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

    "clip() works as string extension" {
        val p = "c3".clip("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.legato shouldBe 0.5
    }

    "legato() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""legato("0.25 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.legato } shouldBe listOf(0.25, 0.75)
    }

    "legato() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").legato("0.25 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.legato } shouldBe listOf(0.25, 0.75)
    }

    "clip() works within compiled code" {
        val p = StrudelPattern.compile("""note("a").clip("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.legato shouldBe (0.5 plusOrMinus EPSILON)
    }

    "legato() with continuous pattern sets legato correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").legato(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.legato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.legato shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.legato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.legato shouldBe (0.0 plusOrMinus EPSILON)
    }
})
