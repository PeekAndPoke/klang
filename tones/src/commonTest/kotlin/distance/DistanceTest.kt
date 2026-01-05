package io.peekandpoke.klang.tones.distance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DistanceTest : StringSpec({
    "transpose" {
        transpose("d3", "3M") shouldBe "F#3"
        transpose("D", "3M") shouldBe "F#"
        listOf("C", "D", "E", "F", "G").map { transpose(it, "M3") } shouldBe listOf("E", "F#", "G#", "A", "B")
    }

    "distance between notes" {
        fun allIntervalsFrom(from: String) = { str: String ->
            str.split(" ").map { distance(from, it) }.joinToString(" ")
        }

        val fromC3 = allIntervalsFrom("C3")
        fromC3("C3 e3 e4 c2 e2") shouldBe "1P 3M 10M -8P -6m"
    }

    "unison interval edge case #243" {
        distance("Db4", "C#5") shouldBe "7A"
        distance("Db4", "C#4") shouldBe "-2d"
        distance("Db", "C#") shouldBe "7A"
        distance("C#", "Db") shouldBe "2d"
    }

    "adjacent octaves #428" {
        distance("B#4", "C4") shouldBe "-7A"
        distance("B#4", "C6") shouldBe "9d"
        distance("B#4", "C5") shouldBe "2d"
        distance("B##4", "C#5") shouldBe "2d"
        distance("B#5", "C6") shouldBe "2d"
    }

    "intervals between pitch classes are always ascending" {
        distance("C", "D") shouldBe "2M"

        fun allIntervalsFrom(from: String) = { str: String ->
            str.split(" ").map { distance(from, it) }.joinToString(" ")
        }

        val fromC = allIntervalsFrom("C")
        fromC("c d e f g a b") shouldBe "1P 2M 3M 4P 5P 6M 7M"

        val fromG = allIntervalsFrom("G")
        fromG("c d e f g a b") shouldBe "4P 5P 6M 7m 1P 2M 3M"
    }

    "if a note is a pitch class, the distance is between pitch classes" {
        distance("C", "C2") shouldBe "1P"
        distance("C2", "C") shouldBe "1P"
    }

    "notes must be valid" {
        distance("one", "two") shouldBe ""
    }
})
