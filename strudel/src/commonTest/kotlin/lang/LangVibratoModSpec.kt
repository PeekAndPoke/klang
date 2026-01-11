package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangVibratoModSpec : StringSpec({

    "vibratoMod() sets VoiceData.vibratoMod depth" {
        val p = vibratoMod("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibmod() alias sets VoiceData.vibratoMod depth" {
        val p = vibmod("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibratoMod() works as pattern extension" {
        val p = note("c").vibratoMod("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibratoMod() works as string extension" {
        val p = "c".vibratoMod("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibratoMod() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").vibratoMod("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }
})
