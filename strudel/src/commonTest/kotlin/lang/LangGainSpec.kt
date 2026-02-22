package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangGainSpec : StringSpec({

    "top-level gain() sets VoiceData.gain correctly" {
        // Given a simple sequence of gain values within one cycle
        val p = sound("hh hh").apply(gain("0.5 1.0"))

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

    "gain() works as string extension" {
        val p = "c3".gain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.gain shouldBe 0.5
    }

    "gain() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""gain("0.5 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.gain } shouldBe listOf(0.5, 1.0)
    }

    "gain() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").gain("0.5 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.gain } shouldBe listOf(0.5, 1.0)
    }

    "gain() with continuous pattern sets gain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").gain(sine)
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 4
            // t=0.0: sine(0) = 0.5
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.25: sine(0.25) = 1.0
            events[1].data.gain shouldBe (1.0 plusOrMinus EPSILON)
            // t=0.5: sine(0.5) = 0.5
            events[2].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.75: sine(0.75) = 0.0
            events[3].data.gain shouldBe (0.0 plusOrMinus EPSILON)
        }
    }
})
