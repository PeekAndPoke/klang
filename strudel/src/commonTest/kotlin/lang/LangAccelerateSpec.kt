package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangAccelerateSpec : StringSpec({

    "top-level accelerate() sets VoiceData.accelerate correctly" {
        val p = accelerate("-0.5 0.75")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(-0.5, 0.75)
    }

    "control pattern accelerate() sets VoiceData.accelerate on existing pattern" {
        val base = note("c3 e3")
        val p = base.accelerate("0.1 -0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.accelerate } shouldBe listOf(0.1, -0.2, 0.1, -0.2)
    }

    "accelerate() works as string extension" {
        val p = "c3".accelerate("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.accelerate shouldBe 0.5
    }

    "accelerate() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""accelerate("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(0.0, 1.0)
    }

    "accelerate() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").accelerate("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(0.0, 1.0)
    }

    "accelerate() with continuous pattern sets accelerate correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").accelerate(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.accelerate shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.accelerate shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.accelerate shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.accelerate shouldBe (0.0 plusOrMinus EPSILON)
    }
})
