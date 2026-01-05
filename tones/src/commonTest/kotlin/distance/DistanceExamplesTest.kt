package io.peekandpoke.klang.tones.distance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DistanceExamplesTest : StringSpec({
    "transposeNote examples" {
        Distance.transpose("C4", "5P") shouldBe "G4"
        Distance.transpose("d3", "3M") shouldBe "F#3"
        Distance.transpose("D", "3M") shouldBe "F#"
        listOf("C", "D", "E", "F", "G").map { Distance.transpose(it, "M3") } shouldBe
                listOf("E", "F#", "G#", "A", "B")

        Distance.transpose("one", "two") shouldBe ""
    }

    "distance examples" {
        Distance.distance("C4", "G4") shouldBe "5P"
        Distance.distance("C3", "E4") shouldBe "10M"

        Distance.distance("C", "E") shouldBe "3M"
        Distance.distance("C", "E4") shouldBe "3M"
        Distance.distance("C4", "E") shouldBe "3M"

        Distance.distance("today", "tomorrow") shouldBe ""
    }
})
