package io.peekandpoke.klang.tones.progression

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProgressionTest : StringSpec({
    "fromRomanNumerals" {
        fun inC(chords: List<String>) = Progression.fromRomanNumerals("C", chords)

        inC("I IIm7 V7".split(" ")) shouldBe
                "C Dm7 G7".split(" ")

        inC("Imaj7 2 IIIm7".split(" ")) shouldBe
                listOf("Cmaj7", "", "Em7")

        inC("I II III IV V VI VII".split(" ")) shouldBe
                "C D E F G A B".split(" ")

        inC("bI bII bIII bIV bV bVI bVII".split(" ")) shouldBe
                "Cb Db Eb Fb Gb Ab Bb".split(" ")

        inC("#Im7 #IIm7 #III #IVMaj7 #V7 #VI #VIIo".split(" ")) shouldBe
                "C#m7 D#m7 E# F#Maj7 G#7 A# B#o".split(" ")
    }

    "toRomanNumerals" {
        Progression.toRomanNumerals("C", listOf("Cmaj7", "Dm7", "G7")) shouldBe
                listOf("Imaj7", "IIm7", "V7")
    }
})
