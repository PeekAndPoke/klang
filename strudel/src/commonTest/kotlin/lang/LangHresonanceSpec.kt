package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHresonanceSpec : StringSpec({

    "hresonance() sets StrudelVoiceData.hresonance" {
        val p = hresonance("5 10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hresonance shouldBe 5.0
        events[1].data.hresonance shouldBe 10.0
    }

    "hres() alias works" {
        val p = hres("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.hresonance shouldBe 5.0
    }

    "hresonance() sets HPF Q specifically" {
        // Apply HPF first, then update hresonance to 5.0
        val p = note("c").hpf("1000").hresonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
        events[0].data.hresonance shouldBe 5.0

        // Verify conversion to VoiceData
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.HighPass).q shouldBe 5.0
    }

    "hresonance() works as string extension" {
        val p = "c".hresonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hresonance shouldBe 5.0
    }

    "hresonance() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hresonance("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hresonance shouldBe 5.0
    }

    "hresonance() with continuous pattern sets hresonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hresonance(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hresonance shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hresonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
