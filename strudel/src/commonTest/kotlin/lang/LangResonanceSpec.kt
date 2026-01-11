package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

class LangResonanceSpec : StringSpec({

    "resonance() sets VoiceData.resonance" {
        val p = resonance("5 10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.resonance shouldBe 5.0
        events[1].data.resonance shouldBe 10.0
    }

    "res() alias works" {
        val p = res("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() updates existing filters" {
        // Apply LPF first (default Q=1.0), then update resonance to 5.0
        val p = note("c").lpf("1000").resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
        events[0].data.filters.getByType<FilterDef.LowPass>()?.q shouldBe 5.0
    }

    "resonance() works as string extension" {
        val p = "c".resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").resonance("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }
})
