package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSoundSpec : StringSpec({

    "sound() sets VoiceData.sound" {
        val p = sound("bd sd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "sd"
    }

    "sound() parses sound index from string (e.g. bd:1)" {
        val p = sound("bd:0 sd:1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.soundIndex shouldBe 0
        events[1].data.sound shouldBe "sd"
        events[1].data.soundIndex shouldBe 1
    }

    "sound() applied to pattern updates sound" {
        val base = note("c")
        val p = base.sound("piano")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "c"
        events[0].data.sound shouldBe "piano"
    }

    "sound() merges sound index correctly" {
        // sound("bd:1").sound("sd") -> sound="sd", index=1 (preserved)
        val p = sound("bd:1").sound("sd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "sd"
        events[0].data.soundIndex shouldBe 1
    }

    "sound() works as string extension" {
        val p = "c".sound("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
        events[0].data.sound shouldBe "piano"
    }

    "sound() without args reinterprets value as sound" {
        // "bd:1".sound() -> value is "bd:1", should parse to sound="bd", index=1
        val p = "bd:1".sound()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
        events[0].data.soundIndex shouldBe 1
    }

    "s() is an alias for sound()" {
        val p = s("bd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
    }

    "s() works as pattern extension" {
        val p = note("c").s("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "piano"
    }

    "s() works as string extension" {
        val p = "c".s("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "piano"
    }

    "sound() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd sd")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "sd"
    }

    "s() alias works in compiled code" {
        val p = StrudelPattern.compile("""s("bd:1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
        events[0].data.soundIndex shouldBe 1
    }
})
