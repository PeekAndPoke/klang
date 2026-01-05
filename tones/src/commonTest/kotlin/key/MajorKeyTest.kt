package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MajorKeyTest : StringSpec({
    "major keySignature" {
        val tonics = "C D E F G A B".split(" ")
        tonics.joinToString(" ") { Key.majorKey(it).keySignature } shouldBe " ## #### b # ### #####"
    }

    "secondary dominants" {
        Key.majorKey("C").secondaryDominants shouldBe listOf(
            "",
            "A7",
            "B7",
            "C7",
            "D7",
            "E7",
            ""
        )
    }

    "octaves are discarded" {
        Key.majorKey("b4").scale.joinToString(" ") shouldBe "B C# D# E F# G# A#"
        Key.majorKey("g4").chords.joinToString(" ") shouldBe "Gmaj7 Am7 Bm7 Cmaj7 D7 Em7 F#m7b5"
    }

    "empty major key" {
        val emptyMajor = Key.majorKey("")
        emptyMajor.type shouldBe "major"
        emptyMajor.tonic shouldBe ""
    }
})
