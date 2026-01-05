package io.peekandpoke.klang.tones.abc

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AbcExamplesTest : StringSpec({
    "Abc.abcToScientificNotation" {
        abcToScientificNotation("c") shouldBe "C5"
    }

    "Abc.scientificToAbcNotation" {
        scientificToAbcNotation("C#4") shouldBe "^C"
    }

    "Abc.transpose" {
        transposeAbc("=C", "P19") shouldBe "g'"
    }

    "Abc.distance" {
        distanceAbc("=C", "g") shouldBe "12P"
    }
})
