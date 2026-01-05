package io.peekandpoke.klang.tones.roman

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.pitch.Pitch

class RomanNumeralTest : StringSpec({
    "names" {
        RomanNumeral.names() shouldBe listOf("I", "II", "III", "IV", "V", "VI", "VII")
        RomanNumeral.names(false) shouldBe listOf("i", "ii", "iii", "iv", "v", "vi", "vii")
    }

    "romanNumeral properties" {
        val rn = RomanNumeral.get("#VIIb5")
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
        val naturals = "1P 2M 3M 4P 5P 6M 7M".split(" ").map { Interval.get(it) }
        naturals.map { RomanNumeral.get(it as Pitch).name } shouldBe "I II III IV V VI VII".split(" ")

        val flats = "1d 2m 3m 4d 5d 6m 7m".split(" ").map { Interval.get(it) }
        flats.map { RomanNumeral.get(it as Pitch).name } shouldBe "bI bII bIII bIV bV bVI bVII".split(" ")

        val sharps = "1A 2A 3A 4A 5A 6A 7A".split(" ").map { Interval.get(it) }
        sharps.map { RomanNumeral.get(it as Pitch).name } shouldBe "#I #II #III #IV #V #VI #VII".split(" ")
    }

    "Can convert to intervals" {
        Interval.get(RomanNumeral.get("I").interval).name shouldBe "1P"
        Interval.get(RomanNumeral.get("bIIImaj4").interval).name shouldBe "3m"
        Interval.get(RomanNumeral.get("#IV7").interval).name shouldBe "4A"
    }

    "step" {
        val decimal = { x: String -> RomanNumeral.get(x).step }
        RomanNumeral.names().map { decimal(it) } shouldBe listOf(0, 1, 2, 3, 4, 5, 6)
    }

    "invalid" {
        RomanNumeral.get("nothing").name shouldBe ""
        RomanNumeral.get("iI").name shouldBe ""
    }

    "roman" {
        RomanNumeral.get("IIIMaj7").roman shouldBe "III"
        RomanNumeral.names().map { RomanNumeral.get(it).name } shouldBe RomanNumeral.names()
    }

    "create from degrees" {
        (1..7).map { RomanNumeral.get(it - 1).name } shouldBe RomanNumeral.names()
    }
})
