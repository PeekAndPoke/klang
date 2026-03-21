package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNSpec : StringSpec({

    "top-level n() sets VoiceData.soundIndex correctly" {
        val p = n("0 1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 0
        events[1].data.soundIndex shouldBe 1
    }

    "n() with scale resolves to notes" {
        val p = n("0 2").scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "E4"
    }

    "control pattern n() sets soundIndex on existing pattern" {
        val base = s("bd sd")
        val p = base.n("0 1") // accessing bank index 0 and 1
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.soundIndex shouldBe 0
        events[1].data.sound shouldBe "sd"
        events[1].data.soundIndex shouldBe 1
    }

    "n() works as string extension" {
        // "0".n("1") -> "0" is parsed as pattern, then n("1") overrides/sets index
        val p = "0".n("1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 1
    }

    "n() re-interprets value as index when called without args" {
        // "0" pattern has value="0". .n() reinterprets this value as index.
        // If we add scale, it should resolve to note.
        val p = "0".scale("C4:major").n()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "n() works within compiled code" {
        val p = StrudelPattern.compile("""n("0 1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 0
        events[1].data.soundIndex shouldBe 1
    }

    "n() works as string extension in compiled code" {
        val p = StrudelPattern.compile(""""0".n("1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 1
    }
})
