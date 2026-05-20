package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.soundName

class LangSoundSpec : StringSpec({

    "sound() sets VoiceData.sound" {
        val p = sound("bd sd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[1].data.soundName shouldBe "sd"
    }

    "sound() parses sound index from string (e.g. bd:1)" {
        val p = sound("bd:0 sd:1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[0].data.soundIndex shouldBe 0
        events[1].data.soundName shouldBe "sd"
        events[1].data.soundIndex shouldBe 1
    }

    "sound() applied to pattern updates sound" {
        val base = note("c")
        val p = base.sound("piano")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].data.soundName shouldBe "piano"
    }

    "sound() merges sound index correctly" {
        // sound("bd:1").sound("sd") -> sound="sd", index=1 (preserved)
        val p = sound("bd:1").sound("sd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "sd"
        events[0].data.soundIndex shouldBe 1
    }

    "sound() works as string extension" {
        val p = "c".sound("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[0].data.soundName shouldBe "piano"
    }

    "sound() without args reinterprets value as sound" {
        // "bd:1".sound() -> value is "bd:1", should parse to sound="bd", index=1
        val p = "bd:1".sound()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "bd"
        events[0].data.soundIndex shouldBe 1
    }

    "s() without args reinterprets value as sound" {
        // "bd:1".sound() -> value is "bd:1", should parse to sound="bd", index=1
        val p = "bd:1".s()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "bd"
        events[0].data.soundIndex shouldBe 1
    }

    "s() is an alias for sound()" {
        val p = s("bd")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "bd"
    }

    "s() works as pattern extension" {
        val p = note("c").s("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "piano"
    }

    "s() works as string extension" {
        val p = "c".s("piano")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "piano"
    }

    "sound() works in compiled code" {
        val p = SprudelPattern.compile("""sound("bd sd")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[1].data.soundName shouldBe "sd"
    }

    "s() alias works in compiled code" {
        val p = SprudelPattern.compile("""s("bd:1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.soundName shouldBe "bd"
        events[0].data.soundIndex shouldBe 1
    }

    // ── Inline IgnitorDsl handoff ────────────────────────────────────

    "sound(IgnitorDsl) stores SoundValue.Osc on the event data" {
        val osc = IgnitorDsl.Sine()
        val p = note("c").sound(osc)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val sound = events[0].data.sound
        sound.shouldBeInstanceOf<SoundValue.Osc>()
        sound.osc shouldBe osc
        // .soundName extracts only Named entries, so it's null here.
        events[0].data.soundName shouldBe null
    }

    "sound(IgnitorDsl) replaces a previous SoundValue.Named on the same chain" {
        val p = note("c").sound("bd").sound(IgnitorDsl.Sawtooth())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound.shouldBeInstanceOf<SoundValue.Osc>()
    }
})
