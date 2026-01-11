package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangVibratoSpec : StringSpec({

    "vibrato() sets VoiceData.vibrato rate" {
        val p = vibrato("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vib() alias sets VoiceData.vibrato rate" {
        val p = vib("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vibrato() works as pattern extension" {
        val p = note("c").vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works as string extension" {
        val p = "c".vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").vibrato("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }
})
