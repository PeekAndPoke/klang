package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.FilterDef

/**
 * Comprehensive test coverage for vowel formant synthesis.
 *
 * Tests all combinations of voice types and vowels to ensure formant filters are correctly created.
 */
class LangVowelComprehensiveSpec : StringSpec({

    // Helper to verify formant filter creation
    fun verifyFormantFilter(vowelSpec: String, shouldCreateFilter: Boolean = true) {
        val p = note("c3").vowel(vowelSpec)
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        if (shouldCreateFilter) {
            voiceData.filters.filters.size shouldBe 1
            val formant = voiceData.filters.filters[0]
            formant.shouldBeInstanceOf<FilterDef.Formant>()
            formant.bands.size shouldBe 5
        } else {
            voiceData.filters.filters.size shouldBe 0
        }
    }

    // =================================================================================================================
    // SOPRANO (Default Voice)
    // =================================================================================================================

    "soprano vowel 'a' (default voice)" {
        verifyFormantFilter("a")
    }

    "soprano vowel 'e' (default voice)" {
        verifyFormantFilter("e")
    }

    "soprano vowel 'i' (default voice)" {
        verifyFormantFilter("i")
    }

    "soprano vowel 'o' (default voice)" {
        verifyFormantFilter("o")
    }

    "soprano vowel 'u' (default voice)" {
        verifyFormantFilter("u")
    }

    "soprano vowel 'ae' (default voice)" {
        verifyFormantFilter("ae")
    }

    "soprano vowel 'ä' (default voice)" {
        verifyFormantFilter("ä")
    }

    "soprano vowel 'oe' (default voice)" {
        verifyFormantFilter("oe")
    }

    "soprano vowel 'ö' (default voice)" {
        verifyFormantFilter("ö")
    }

    "soprano vowel 'ue' (default voice)" {
        verifyFormantFilter("ue")
    }

    "soprano vowel 'ü' (default voice)" {
        verifyFormantFilter("ü")
    }

    "soprano vowel 'ei' (default voice)" {
        verifyFormantFilter("ei")
    }

    "soprano vowel 'au' (default voice)" {
        verifyFormantFilter("au")
    }

    "soprano vowel 'eu' (default voice)" {
        verifyFormantFilter("eu")
    }

    "soprano vowel 'äu' (default voice)" {
        verifyFormantFilter("äu")
    }

    // =================================================================================================================
    // SOPRANO (Explicit)
    // =================================================================================================================

    "soprano:a explicit" {
        verifyFormantFilter("soprano:a")
    }

    "soprano:e explicit" {
        verifyFormantFilter("soprano:e")
    }

    "soprano:i explicit" {
        verifyFormantFilter("soprano:i")
    }

    "soprano:o explicit" {
        verifyFormantFilter("soprano:o")
    }

    "soprano:u explicit" {
        verifyFormantFilter("soprano:u")
    }

    "soprano:ae explicit" {
        verifyFormantFilter("soprano:ae")
    }

    "soprano:ä explicit" {
        verifyFormantFilter("soprano:ä")
    }

    "soprano:oe explicit" {
        verifyFormantFilter("soprano:oe")
    }

    "soprano:ö explicit" {
        verifyFormantFilter("soprano:ö")
    }

    "soprano:ue explicit" {
        verifyFormantFilter("soprano:ue")
    }

    "soprano:ü explicit" {
        verifyFormantFilter("soprano:ü")
    }

    "soprano:ei explicit" {
        verifyFormantFilter("soprano:ei")
    }

    "soprano:au explicit" {
        verifyFormantFilter("soprano:au")
    }

    "soprano:eu explicit" {
        verifyFormantFilter("soprano:eu")
    }

    "soprano:äu explicit" {
        verifyFormantFilter("soprano:äu")
    }

    // =================================================================================================================
    // ALTO / COUNTERTENOR
    // =================================================================================================================

    "alto:a" {
        verifyFormantFilter("alto:a")
    }

    "alto:e" {
        verifyFormantFilter("alto:e")
    }

    "alto:i" {
        verifyFormantFilter("alto:i")
    }

    "alto:o" {
        verifyFormantFilter("alto:o")
    }

    "alto:u" {
        verifyFormantFilter("alto:u")
    }

    "alto:ae" {
        verifyFormantFilter("alto:ae")
    }

    "alto:ä" {
        verifyFormantFilter("alto:ä")
    }

    "alto:oe" {
        verifyFormantFilter("alto:oe")
    }

    "alto:ö" {
        verifyFormantFilter("alto:ö")
    }

    "alto:ue" {
        verifyFormantFilter("alto:ue")
    }

    "alto:ü" {
        verifyFormantFilter("alto:ü")
    }

    "alto:ei" {
        verifyFormantFilter("alto:ei")
    }

    "alto:au" {
        verifyFormantFilter("alto:au")
    }

    "alto:eu" {
        verifyFormantFilter("alto:eu")
    }

    "alto:äu" {
        verifyFormantFilter("alto:äu")
    }

    "countertenor:a (alias for alto)" {
        verifyFormantFilter("countertenor:a")
    }

    "countertenor:e (alias for alto)" {
        verifyFormantFilter("countertenor:e")
    }

    "countertenor:i (alias for alto)" {
        verifyFormantFilter("countertenor:i")
    }

    "countertenor:o (alias for alto)" {
        verifyFormantFilter("countertenor:o")
    }

    "countertenor:u (alias for alto)" {
        verifyFormantFilter("countertenor:u")
    }

    // =================================================================================================================
    // TENOR
    // =================================================================================================================

    "tenor:a" {
        verifyFormantFilter("tenor:a")
    }

    "tenor:e" {
        verifyFormantFilter("tenor:e")
    }

    "tenor:i" {
        verifyFormantFilter("tenor:i")
    }

    "tenor:o" {
        verifyFormantFilter("tenor:o")
    }

    "tenor:u" {
        verifyFormantFilter("tenor:u")
    }

    "tenor:ae" {
        verifyFormantFilter("tenor:ae")
    }

    "tenor:ä" {
        verifyFormantFilter("tenor:ä")
    }

    "tenor:oe" {
        verifyFormantFilter("tenor:oe")
    }

    "tenor:ö" {
        verifyFormantFilter("tenor:ö")
    }

    "tenor:ue" {
        verifyFormantFilter("tenor:ue")
    }

    "tenor:ü" {
        verifyFormantFilter("tenor:ü")
    }

    "tenor:ei" {
        verifyFormantFilter("tenor:ei")
    }

    "tenor:au" {
        verifyFormantFilter("tenor:au")
    }

    "tenor:eu" {
        verifyFormantFilter("tenor:eu")
    }

    "tenor:äu" {
        verifyFormantFilter("tenor:äu")
    }

    // =================================================================================================================
    // BASS
    // =================================================================================================================

    "bass:a" {
        verifyFormantFilter("bass:a")
    }

    "bass:e" {
        verifyFormantFilter("bass:e")
    }

    "bass:i" {
        verifyFormantFilter("bass:i")
    }

    "bass:o" {
        verifyFormantFilter("bass:o")
    }

    "bass:u" {
        verifyFormantFilter("bass:u")
    }

    "bass:ae" {
        verifyFormantFilter("bass:ae")
    }

    "bass:ä" {
        verifyFormantFilter("bass:ä")
    }

    "bass:oe" {
        verifyFormantFilter("bass:oe")
    }

    "bass:ö" {
        verifyFormantFilter("bass:ö")
    }

    "bass:ue" {
        verifyFormantFilter("bass:ue")
    }

    "bass:ü" {
        verifyFormantFilter("bass:ü")
    }

    "bass:ei" {
        verifyFormantFilter("bass:ei")
    }

    "bass:au" {
        verifyFormantFilter("bass:au")
    }

    "bass:eu" {
        verifyFormantFilter("bass:eu")
    }

    "bass:äu" {
        verifyFormantFilter("bass:äu")
    }

    // =================================================================================================================
    // CASE INSENSITIVITY
    // =================================================================================================================

    "vowel 'A' uppercase (case insensitive)" {
        verifyFormantFilter("A")
    }

    "vowel 'SOPRANO:A' uppercase (case insensitive)" {
        verifyFormantFilter("SOPRANO:A")
    }

    "vowel 'Bass:E' mixed case (case insensitive)" {
        verifyFormantFilter("Bass:E")
    }

    "vowel 'TENOR:OE' uppercase (case insensitive)" {
        verifyFormantFilter("TENOR:OE")
    }

    // =================================================================================================================
    // NEGATIVE TEST CASES - Unknown Vowels
    // =================================================================================================================

    "unknown vowel 'x' should not create filter" {
        verifyFormantFilter("x", shouldCreateFilter = false)
    }

    "unknown vowel 'y' should not create filter" {
        verifyFormantFilter("y", shouldCreateFilter = false)
    }

    "unknown vowel 'z' should not create filter" {
        verifyFormantFilter("z", shouldCreateFilter = false)
    }

    "unknown vowel 'aa' should not create filter" {
        verifyFormantFilter("aa", shouldCreateFilter = false)
    }

    "unknown vowel 'oo' should not create filter" {
        verifyFormantFilter("oo", shouldCreateFilter = false)
    }

    "empty vowel should not create filter" {
        verifyFormantFilter("", shouldCreateFilter = false)
    }

    // =================================================================================================================
    // NEGATIVE TEST CASES - Unknown Voice Types
    // =================================================================================================================

    "unknown voice 'baritone:a' should not create filter" {
        verifyFormantFilter("baritone:a", shouldCreateFilter = false)
    }

    "unknown voice 'mezzo:e' should not create filter" {
        verifyFormantFilter("mezzo:e", shouldCreateFilter = false)
    }

    "unknown voice 'contralto:i' should not create filter" {
        verifyFormantFilter("contralto:i", shouldCreateFilter = false)
    }

    "unknown voice 'unknown:a' should not create filter" {
        verifyFormantFilter("unknown:a", shouldCreateFilter = false)
    }

    // =================================================================================================================
    // NEGATIVE TEST CASES - Known Voice + Unknown Vowel
    // =================================================================================================================

    "soprano:x (known voice, unknown vowel) should not create filter" {
        verifyFormantFilter("soprano:x", shouldCreateFilter = false)
    }

    "bass:y (known voice, unknown vowel) should not create filter" {
        verifyFormantFilter("bass:y", shouldCreateFilter = false)
    }

    "tenor:unknown (known voice, unknown vowel) should not create filter" {
        verifyFormantFilter("tenor:unknown", shouldCreateFilter = false)
    }

    "alto:xyz (known voice, unknown vowel) should not create filter" {
        verifyFormantFilter("alto:xyz", shouldCreateFilter = false)
    }

    // =================================================================================================================
    // FORMANT BAND VERIFICATION - Sample detailed checks
    // =================================================================================================================

    "soprano:a has correct formant frequencies" {
        val p = note("c3").vowel("soprano:a")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()
        val formant = voiceData.filters.filters[0] as FilterDef.Formant

        formant.bands[0].freq shouldBe 800.0
        formant.bands[1].freq shouldBe 1150.0
        formant.bands[2].freq shouldBe 2900.0
        formant.bands[3].freq shouldBe 3900.0
        formant.bands[4].freq shouldBe 4950.0
    }

    "bass:e has correct formant frequencies" {
        val p = note("c3").vowel("bass:e")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()
        val formant = voiceData.filters.filters[0] as FilterDef.Formant

        formant.bands[0].freq shouldBe 400.0
        formant.bands[1].freq shouldBe 1620.0
        formant.bands[2].freq shouldBe 2400.0
        formant.bands[3].freq shouldBe 2800.0
        formant.bands[4].freq shouldBe 3100.0
    }

    "tenor:ü (umlaut) has correct formant frequencies" {
        val p = note("c3").vowel("tenor:ü")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()
        val formant = voiceData.filters.filters[0] as FilterDef.Formant

        formant.bands[0].freq shouldBe 290.0
        formant.bands[1].freq shouldBe 1500.0
        formant.bands[2].freq shouldBe 2300.0
        formant.bands[3].freq shouldBe 3250.0
        formant.bands[4].freq shouldBe 3540.0
    }

    "alto:au (diphthong nucleus) has correct formant frequencies" {
        val p = note("c3").vowel("alto:au")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()
        val formant = voiceData.filters.filters[0] as FilterDef.Formant

        // 'au' maps to 'a' formants
        formant.bands[0].freq shouldBe 660.0
        formant.bands[1].freq shouldBe 1120.0
        formant.bands[2].freq shouldBe 2750.0
        formant.bands[3].freq shouldBe 3000.0
        formant.bands[4].freq shouldBe 3350.0
    }

    // =================================================================================================================
    // PATTERN SEQUENCES
    // =================================================================================================================

    "vowel sequence with different voices" {
        val p = note("c3 c3 c3 c3").vowel("soprano:a bass:e tenor:i alto:o")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // Verify all create formant filters
        events.forEach { event ->
            val voiceData = event.data.toVoiceData()
            voiceData.filters.filters.size shouldBe 1
            voiceData.filters.filters[0].shouldBeInstanceOf<FilterDef.Formant>()
        }
    }

    "vowel sequence with mixed valid and invalid" {
        val p = note("c3 c3 c3").vowel("a x bass:e")
        val events = p.queryArc(0.0, 1.0)

        // First event: 'a' - should have formant filter
        events[0].data.toVoiceData().filters.filters.size shouldBe 1

        // Second event: 'x' - should not have formant filter
        events[1].data.toVoiceData().filters.filters.size shouldBe 0

        // Third event: 'bass:e' - should have formant filter
        events[2].data.toVoiceData().filters.filters.size shouldBe 1
    }
})
