package io.peekandpoke.klang.tones.abc

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AbcTest : StringSpec({
    "tokenize" {
        tokenizeAbc("C,',") shouldBe listOf("", "C", ",',")
        tokenizeAbc("g,,'") shouldBe listOf("", "g", ",,'")
        tokenizeAbc("") shouldBe listOf("", "", "")
        tokenizeAbc("m") shouldBe listOf("", "", "")
        tokenizeAbc("c#") shouldBe listOf("", "", "")
    }

    "transpose" {
        transposeAbc("=C", "P19") shouldBe "g'"
    }

    "distance" {
        distanceAbc("=C", "g") shouldBe "12P"
    }

    "abcToScientificNotation" {
        val abc = listOf(
            "__A,,",
            "_B,",
            "=C",
            "d",
            "^e'",
            "^^f''",
            "G,,''",
            "g,,,'''",
            "",
        )
        val scientific = listOf(
            "Abb2",
            "Bb3",
            "C4",
            "D5",
            "E#6",
            "F##7",
            "G4",
            "G5",
            "",
        )
        abc.map { abcToScientificNotation(it) } shouldBe scientific
    }

    "scientificToAbcNotation" {
        val scientific = listOf(
            "Abb2",
            "Bb3",
            "C4",
            "D5",
            "E#6",
            "F##7",
            "G#2",
            "Gb7",
            "",
        )
        val abc = listOf("__A,,", "_B,", "C", "d", "^e'", "^^f''", "^G,,", "_g''", "")
        scientific.map { scientificToAbcNotation(it) } shouldBe abc
    }

    "scientificToAbcNotation Octave 0" {
        val scientific = listOf("A0", "Bb0", "C0", "D0", "E#0", "F##0", "G#0")
        val abc = listOf(
            "A,,,,",
            "_B,,,,",
            "C,,,,",
            "D,,,,",
            "^E,,,,",
            "^^F,,,,",
            "^G,,,,",
        )
        scientific.map { scientificToAbcNotation(it) } shouldBe abc
    }
})
