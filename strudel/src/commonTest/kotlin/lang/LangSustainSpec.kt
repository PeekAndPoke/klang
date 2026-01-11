package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
})
