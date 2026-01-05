package io.peekandpoke.klang.tones.distance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DistanceExamplesTest : StringSpec({
    "transpose examples" {
        transpose("C4", "5P") shouldBe "G4"
        transpose("d3", "3M") shouldBe "F#3"
        transpose("D", "3M") shouldBe "F#"
        listOf("C", "D", "E", "F", "G").map { transpose(it, "M3") } shouldBe
                listOf("E", "F#", "G#", "A", "B")

        transpose("one", "two") shouldBe ""
    }

    "distance examples" {
        distance("C4", "G4") shouldBe "5P"
        distance("C3", "E4") shouldBe "10M"

        distance("C", "E") shouldBe "3M"
        distance("C", "E4") shouldBe "3M"
        distance("C4", "E") shouldBe "3M"

        distance("today", "tomorrow") shouldBe ""
    }
})
