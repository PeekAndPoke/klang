package io.peekandpoke.klang.tones.interval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.distance.Distance

class IntervalExamplesTest : StringSpec({
    "Interval properties" {
        val p5 = Interval.get("5P")
        p5.name shouldBe "5P"
        p5.num shouldBe 5
        // ...

        Interval.get("d4").name shouldBe "4d"
        Interval.get("5P").num shouldBe 5
        Interval.get("5P").q shouldBe "P"
        Interval.get("P4").semitones shouldBe 5
    }

    "Interval.distance" {
        Distance.distance("C4", "G4") shouldBe "5P"
    }

    "Interval.names" {
        Interval.names() shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6m", "7m")
    }

    "Interval.fromSemitones" {
        Interval.fromSemitones(7) shouldBe "5P"
        Interval.fromSemitones(-7) shouldBe "-5P"

        listOf(0, 1, 2, 3, 4).map { Interval.fromSemitones(it) } shouldBe
                listOf("1P", "2m", "2M", "3m", "3M")
    }

    "Interval.simplify" {
        Interval.simplify("9M") shouldBe "2M"
        Interval.simplify("2M") shouldBe "2M"
        // Note: TonalJS README says "7m" but the actual code and tests return "-2M"
        Interval.simplify("-2M") shouldBe "-2M"

        listOf("8P", "9M", "10M", "11P", "12P", "13M", "14M", "15P").map { Interval.simplify(it) } shouldBe
                listOf("8P", "2M", "3M", "4P", "5P", "6M", "7M", "1P")
    }

    "Interval.invert" {
        Interval.invert("3m") shouldBe "6M"
        Interval.invert("2M") shouldBe "7m"
    }

    "Interval.add" {
        Interval.add("3m", "5P") shouldBe "7m"
    }

    "Interval.subtract" {
        Interval.subtract("5P", "3M") shouldBe "3m"
        Interval.subtract("3M", "5P") shouldBe "-3m"
    }

    "pitch-interval examples" {
        Interval.get("4P").semitones shouldBe 5

        val d4 = Interval.get("4d")
        d4.name shouldBe "4d"
        d4.type.toString() shouldBe "perfectable"
        d4.dir shouldBe 1
        d4.num shouldBe 4
        d4.q shouldBe "d"
        d4.alt shouldBe -1
        d4.chroma shouldBe 4
        d4.oct shouldBe 0
        d4.semitones shouldBe 4
        d4.simple shouldBe 4

        Interval.get("hello").empty shouldBe true
        Interval.get("hello").name shouldBe ""
    }
})
