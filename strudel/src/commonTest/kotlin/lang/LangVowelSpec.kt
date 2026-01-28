package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangVowelSpec : StringSpec({

    "vowel() sets the vowel property" {
        val p = note("c3").vowel("a")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vowel shouldBe "a"
    }

    "vowel() works as standalone function" {
        val p = vowel("a e i")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.vowel shouldBe "a"
        events[1].data.vowel shouldBe "e"
        events[2].data.vowel shouldBe "i"
    }

    "vowel() works with string pattern sequences" {
        val p = note("c3 e3").vowel("a o")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.vowel shouldBe "a"
        events[1].data.vowel shouldBe "o"
    }

    "vowel() handles case insensitively" {
        val p = note("c3").vowel("A")

        val events = p.queryArc(0.0, 1.0)

        events[0].data.vowel shouldBe "a"
    }

    "vowel() converts to FilterDef.Formant in toVoiceData()" {
        val p = note("c3").vowel("a")

        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.filters.filters.size shouldBe 1
        val formant = voiceData.filters.filters[0] as io.peekandpoke.klang.audio_bridge.FilterDef.Formant

        // Verify vowel 'a' formant bands
        formant.bands.size shouldBe 5
    }

    "vowel() works with all vowels (a, e, i, o, u)" {
        val vowels = listOf("a", "e", "i", "o", "u")

        vowels.forEach { vowel ->
            val p = note("c3").vowel(vowel)

            val events = p.queryArc(0.0, 1.0)
            events[0].data.vowel shouldBe vowel

            // Verify vowel creates a formant filter
            val voiceData = events[0].data.toVoiceData()
            voiceData.filters.filters.size shouldBe 1

            // Type check for formant filter
            val filter = voiceData.filters.filters[0]
            (filter is io.peekandpoke.klang.audio_bridge.FilterDef.Formant) shouldBe true
        }
    }

    "vowel() with unknown vowel is ignored" {
        val p = note("c3").vowel("x")

        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        // Should not create a formant filter for unknown vowel
        voiceData.filters.filters.size shouldBe 0
    }

    "vowel() as string extension" {
        val p = "c3 e3".vowel("a")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.vowel shouldBe "a"
        events[1].data.vowel shouldBe "a"
    }
})
