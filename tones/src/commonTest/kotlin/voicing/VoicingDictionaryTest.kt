package io.peekandpoke.klang.tones.voicing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoicingDictionaryTest : StringSpec({
    "lookup" {
        VoicingDictionaries.lookup("M", VoicingDictionaries.triads) shouldBe listOf(
            "1P 3M 5P",
            "3M 5P 8P",
            "5P 8P 10M"
        )
        VoicingDictionaries.lookup("", VoicingDictionaries.triads) shouldBe listOf(
            "1P 3M 5P",
            "3M 5P 8P",
            "5P 8P 10M"
        )
        VoicingDictionaries.lookup("minor", mapOf("minor" to listOf("1P 3m 5P"))) shouldBe listOf(
            "1P 3m 5P"
        )
    }
})
