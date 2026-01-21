package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNresonanceSpec : StringSpec({

    "nresonance() sets StrudelVoiceData.nresonance" {
        val p = nresonance("0.5 0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nresonance shouldBe 0.5
        events[1].data.nresonance shouldBe 0.9
    }

    "nres() alias works" {
        val p = nres("0.8")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.nresonance shouldBe 0.8
    }

    "nresonance() sets Notch filter Q specifically" {
        // Apply Notch filter first, then update nresonance to 0.8
        val p = note("c").notchf("1000").nresonance("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
        events[0].data.nresonance shouldBe 0.8

        // Verify conversion to VoiceData
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.Notch).q shouldBe 0.8
    }

    "nresonance() works as string extension" {
        val p = "c".nresonance("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nresonance shouldBe 0.8
    }

    "nresonance() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nresonance("0.8")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nresonance shouldBe 0.8
    }

    "nresonance() with continuous pattern sets nresonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").nresonance(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.nresonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.nresonance shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.nresonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.nresonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
