package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDetuneSpec : StringSpec({

    "detune() sets VoiceData.freqSpread" {
        val p = detune("0.1 0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.freqSpread } shouldBe listOf(0.1, 0.2)
    }

    "detune() works as pattern extension" {
        val p = note("c").detune("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.freqSpread shouldBe 0.1
    }

    "detune() works as string extension" {
        val p = "c".detune("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.freqSpread shouldBe 0.1
    }

    "detune() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").detune("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.freqSpread shouldBe 0.1
    }
})
