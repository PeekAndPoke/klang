package io.peekandpoke.klang.tones.interval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.distance.distance

class IntervalExamplesTest : StringSpec({
    "Interval properties" {
        val p5 = interval("5P")
        p5.name shouldBe "5P"
        p5.num shouldBe 5
        // ...

        interval("d4").name shouldBe "4d"
        interval("5P").num shouldBe 5
        interval("5P").q shouldBe "P"
        interval("P4").semitones shouldBe 5
    }

    "Interval.distance" {
        distance("C4", "G4") shouldBe "5P"
    }

    "Interval.names" {
        names() shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6m", "7m")
    }

    "Interval.fromSemitones" {
        fromSemitones(7) shouldBe "5P"
        fromSemitones(-7) shouldBe "-5P"

        listOf(0, 1, 2, 3, 4).map { fromSemitones(it) } shouldBe
                listOf("1P", "2m", "2M", "3m", "3M")
    }

    "Interval.simplify" {
        simplify("9M") shouldBe "2M"
        simplify("2M") shouldBe "2M"
        // Note: TonalJS README says "7m" but the actual code and tests return "-2M"
        simplify("-2M") shouldBe "-2M"

        listOf("8P", "9M", "10M", "11P", "12P", "13M", "14M", "15P").map { simplify(it) } shouldBe
                listOf("8P", "2M", "3M", "4P", "5P", "6M", "7M", "1P")
    }

    "Interval.invert" {
        invert("3m") shouldBe "6M"
        invert("2M") shouldBe "7m"
    }

    "Interval.add" {
        add("3m", "5P") shouldBe "7m"
    }

    "Interval.subtract" {
        subtract("5P", "3M") shouldBe "3m"
        subtract("3M", "5P") shouldBe "-3m"
    }

    "pitch-interval examples" {
        interval("4P").semitones shouldBe 5

        val d4 = interval("4d")
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

        interval("hello").empty shouldBe true
        interval("hello").name shouldBe ""
    }
})
