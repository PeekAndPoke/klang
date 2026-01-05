package io.peekandpoke.klang.tones.roman

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.interval.interval

class RomanNumeralTest : StringSpec({
    "names" {
        romanNumeralNames() shouldBe listOf("I", "II", "III", "IV", "V", "VI", "VII")
        romanNumeralNames(false) shouldBe listOf("i", "ii", "iii", "iv", "v", "vi", "vii")
    }

    "romanNumeral properties" {
        val rn = romanNumeral("#VIIb5")
        rn.empty shouldBe false
        rn.name shouldBe "#VIIb5"
        rn.roman shouldBe "VII"
        rn.interval shouldBe "7A"
        rn.acc shouldBe "#"
        rn.chordType shouldBe "b5"
        rn.major shouldBe true
        rn.step shouldBe 6
        rn.alt shouldBe 1
        rn.oct shouldBe 0
        rn.dir shouldBe 1
    }

    "RomanNumeral is compatible with Pitch" {
        val naturals = "1P 2M 3M 4P 5P 6M 7M".split(" ").map { interval(it) }
        naturals.map { romanNumeral(it).name } shouldBe "I II III IV V VI VII".split(" ")

        val flats = "1d 2m 3m 4d 5d 6m 7m".split(" ").map { interval(it) }
        flats.map { romanNumeral(it).name } shouldBe "bI bII bIII bIV bV bVI bVII".split(" ")

        val sharps = "1A 2A 3A 4A 5A 6A 7A".split(" ").map { interval(it) }
        sharps.map { romanNumeral(it).name } shouldBe "#I #II #III #IV #V #VI #VII".split(" ")
    }

    "Can convert to intervals" {
        interval(romanNumeral("I")).name shouldBe "1P"
        interval(romanNumeral("bIIImaj4")).name shouldBe "3m"
        interval(romanNumeral("#IV7")).name shouldBe "4A"
    }

    "step" {
        val decimal = { x: String -> romanNumeral(x).step }
        romanNumeralNames().map { decimal(it) } shouldBe listOf(0, 1, 2, 3, 4, 5, 6)
    }

    "invalid" {
        romanNumeral("nothing").name shouldBe ""
        romanNumeral("iI").name shouldBe ""
    }

    "roman" {
        romanNumeral("IIIMaj7").roman shouldBe "III"
        romanNumeralNames().map { romanNumeral(it).name } shouldBe romanNumeralNames()
    }

    "create from degrees" {
        (1..7).map { romanNumeral(it - 1).name } shouldBe romanNumeralNames()
    }
})
