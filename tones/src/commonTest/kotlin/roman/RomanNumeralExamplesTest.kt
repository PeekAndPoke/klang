package io.peekandpoke.klang.tones.roman

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.interval.interval

class RomanNumeralExamplesTest : StringSpec({
    "RomanNumeral.get" {
        val r = romanNumeral("bVIIMaj7")
        r.empty shouldBe false
        r.name shouldBe "bVIIMaj7"
        r.roman shouldBe "VII"
        r.acc shouldBe "b"
        r.chordType shouldBe "Maj7"
        r.alt shouldBe -1
        r.step shouldBe 6
        r.major shouldBe true
        r.oct shouldBe 0
    }

    "RomanNumeral from interval" {
        romanNumeral(interval("3m")).name shouldBe "bIII"
    }
})
