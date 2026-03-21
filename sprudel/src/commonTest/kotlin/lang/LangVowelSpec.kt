package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangVowelSpec : StringSpec({

    "vowel dsl interface" {
        val pat = "c3"
        val vowelVal = "a"

        dslInterfaceTests(
            "pattern.vowel(v)" to note(pat).vowel(vowelVal),
            "script pattern.vowel(v)" to StrudelPattern.compile("""note("$pat").vowel("$vowelVal")"""),
            "string.vowel(v)" to pat.vowel(vowelVal),
            "script string.vowel(v)" to StrudelPattern.compile(""""$pat".vowel("$vowelVal")"""),
            "vowel(v)" to note(pat).apply(vowel(vowelVal)),
            "script vowel(v)" to StrudelPattern.compile("""note("$pat").apply(vowel("$vowelVal"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vowel shouldBe "a"
        }
    }

    "reinterpret voice data as vowel | seq(\"a e i\").vowel()" {
        val p = seq("a e i").vowel()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowel } shouldBe listOf("a", "e", "i")
    }

    "reinterpret voice data as vowel | \"a e i\".vowel()" {
        val p = "a e i".vowel()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowel } shouldBe listOf("a", "e", "i")
    }

    "reinterpret voice data as vowel | seq(\"a e i\").apply(vowel())" {
        val p = seq("a e i").apply(vowel())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowel } shouldBe listOf("a", "e", "i")
    }

    "vowel() sets the vowel property" {
        val p = note("c3").vowel("a")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vowel shouldBe "a"
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

        vowels.forEach { v ->
            val p = note("c3").vowel(v)

            val events = p.queryArc(0.0, 1.0)
            events[0].data.vowel shouldBe v

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
