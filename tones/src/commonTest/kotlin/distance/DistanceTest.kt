package io.peekandpoke.klang.tones.distance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DistanceTest : StringSpec({
    "transpose" {
        Distance.transpose("d3", "3M") shouldBe "F#3"
        Distance.transpose("D", "3M") shouldBe "F#"
        listOf("C", "D", "E", "F", "G").map { Distance.transpose(it, "M3") } shouldBe listOf("E", "F#", "G#", "A", "B")
    }

    "distance between notes" {
        fun allIntervalsFrom(from: String) = { str: String ->
            str.split(" ").map { Distance.distance(from, it) }.joinToString(" ")
        }

        val fromC3 = allIntervalsFrom("C3")
        fromC3("C3 e3 e4 c2 e2") shouldBe "1P 3M 10M -8P -6m"
    }

    "unison interval edge case #243" {
        Distance.distance("Db4", "C#5") shouldBe "7A"
        Distance.distance("Db4", "C#4") shouldBe "-2d"
        Distance.distance("Db", "C#") shouldBe "7A"
        Distance.distance("C#", "Db") shouldBe "2d"
    }

    "adjacent octaves #428" {
        Distance.distance("B#4", "C4") shouldBe "-7A"
        Distance.distance("B#4", "C6") shouldBe "9d"
        Distance.distance("B#4", "C5") shouldBe "2d"
        Distance.distance("B##4", "C#5") shouldBe "2d"
        Distance.distance("B#5", "C6") shouldBe "2d"
    }

    "intervals between pitch classes are always ascending" {
        Distance.distance("C", "D") shouldBe "2M"

        fun allIntervalsFrom(from: String) = { str: String ->
            str.split(" ").map { Distance.distance(from, it) }.joinToString(" ")
        }

        val fromC = allIntervalsFrom("C")
        fromC("c d e f g a b") shouldBe "1P 2M 3M 4P 5P 6M 7M"

        val fromG = allIntervalsFrom("G")
        fromG("c d e f g a b") shouldBe "4P 5P 6M 7m 1P 2M 3M"
    }

    "if a note is a pitch class, the distance is between pitch classes" {
        Distance.distance("C", "C2") shouldBe "1P"
        Distance.distance("C2", "C") shouldBe "1P"
    }

    "notes must be valid" {
        Distance.distance("one", "two") shouldBe ""
    }
})
