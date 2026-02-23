package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VoiceCompressorSpec : StringSpec({

    "fromStringConfig returns null for null input" {
        Voice.Compressor.fromStringConfig(null) shouldBe null
    }

    "fromStringConfig returns null for invalid input" {
        Voice.Compressor.fromStringConfig("invalid") shouldBe null
        Voice.Compressor.fromStringConfig("") shouldBe null
        Voice.Compressor.fromStringConfig("abc:def") shouldBe null
    }

    "fromStringConfig parses full format" {
        val c = Voice.Compressor.fromStringConfig("-20:4:6:0.003:0.1")

        c shouldNotBe null
        c!!.thresholdDb shouldBe -20.0
        c.ratio shouldBe 4.0
        c.kneeDb shouldBe 6.0
        c.attackSeconds shouldBe 0.003
        c.releaseSeconds shouldBe 0.1
    }

    "fromStringConfig parses short format (threshold:ratio only)" {
        val c = Voice.Compressor.fromStringConfig("-15:3")

        c shouldNotBe null
        c!!.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
        c.kneeDb shouldBe 6.0
        c.attackSeconds shouldBe 0.003
        c.releaseSeconds shouldBe 0.1
    }
})
