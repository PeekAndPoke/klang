package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangVibratoSpec : StringSpec({

    "top-level vibrato() sets VoiceData.vibrato correctly" {
        val p = vibrato("5 7.5")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 7.5)
    }

    "control pattern vibrato() sets VoiceData.vibrato on existing pattern" {
        val base = note("c3 e3")
        val p = base.vibrato("4 8")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.vibrato } shouldBe listOf(4.0, 8.0, 4.0, 8.0)
    }

    "alias vib behaves like vibrato" {
        val pTop = vib("3 9")
        val eTop = pTop.queryArc(0.0, 1.0)
        eTop.size shouldBe 2
        eTop.map { it.data.vibrato } shouldBe listOf(3.0, 9.0)

        val base = note("c3 e3")
        val pCtrl = base.vib("2 6")
        val eCtrl = pCtrl.queryArc(0.0, 2.0)
        eCtrl.size shouldBe 4
        eCtrl.map { it.data.vibrato } shouldBe listOf(2.0, 6.0, 2.0, 6.0)
    }
})
