package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSustainSpec : StringSpec({

    "sustain() sets VoiceData.adsr.sustain" {
        val p = sustain("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.sustain } shouldBe listOf(0.1, 0.5)
    }

    "sustain() works as pattern extension" {
        val p = note("c").sustain("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.sustain shouldBe 0.1
    }

    "sustain() works as string extension" {
        val p = "c".sustain("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.sustain shouldBe 0.1
    }

    "sustain() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").sustain("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.adsr.sustain shouldBe 0.1
    }

    "sustain() with continuous pattern sets sustain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").sustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.adsr.sustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.adsr.sustain shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.adsr.sustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.adsr.sustain shouldBe (0.0 plusOrMinus EPSILON)
    }
})
