package io.peekandpoke.klang.tones.interval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pitch.Pitch
import io.peekandpoke.klang.tones.pitch.PitchCoordinates

class IntervalTest : StringSpec({
    "tokenize" {
        tokenizeInterval("-2M") shouldBe listOf("-2", "M")
        tokenizeInterval("M-3") shouldBe listOf("-3", "M")
    }

    "interval from string has all properties" {
        val i = interval("4d")
        i.empty shouldBe false
        i.name shouldBe "4d"
        i.num shouldBe 4
        i.q shouldBe "d"
        i.type.toString() shouldBe "perfectable"
        i.alt shouldBe -1
        i.chroma shouldBe 4
        i.dir shouldBe 1
        i.coord shouldBe PitchCoordinates.Interval(-8, 5, 1)
        i.oct shouldBe 0
        i.semitones shouldBe 4
        i.simple shouldBe 4
        i.step shouldBe 3
    }

    "interval from string accepts interval as parameter" {
        interval(interval("5P")) shouldBe interval("5P")
    }

    "interval from string name" {
        fun names(src: String) = src.split(" ").map { interval(it).name }.joinToString(" ")
        names("1P 2M 3M 4P 5P 6M 7M") shouldBe "1P 2M 3M 4P 5P 6M 7M"
        names("P1 M2 M3 P4 P5 M6 M7") shouldBe "1P 2M 3M 4P 5P 6M 7M"
        names("-1P -2M -3M -4P -5P -6M -7M") shouldBe "-1P -2M -3M -4P -5P -6M -7M"
        names("P-1 M-2 M-3 P-4 P-5 M-6 M-7") shouldBe "-1P -2M -3M -4P -5P -6M -7M"

        interval("not-an-interval").empty shouldBe true
        interval("2P").empty shouldBe true
    }

    "interval from string q" {
        fun q(str: String) = str.split(" ").map { interval(it).q }
        q("1dd 1d 1P 1A 1AA") shouldBe listOf("dd", "d", "P", "A", "AA")
        q("2dd 2d 2m 2M 2A 2AA") shouldBe listOf("dd", "d", "m", "M", "A", "AA")
    }

    "interval from string alt" {
        fun alt(str: String) = str.split(" ").map { interval(it).alt }
        alt("1dd 2dd 3dd 4dd") shouldBe listOf(-2, -3, -3, -2)
    }

    "interval from string simple" {
        fun simple(str: String) = str.split(" ").map { interval(it).simple }
        simple("1P 2M 3M 4P") shouldBe listOf(1, 2, 3, 4)
        simple("8P 9M 10M 11P") shouldBe listOf(8, 2, 3, 4)
        simple("-8P -9M -10M -11P") shouldBe listOf("-8P", "-2M", "-3M", "-4P").map { interval(it).simple }
        // The previous line was wrong anyway in my first attempt, it should be simple result
        simple("-8P -9M -10M -11P") shouldBe listOf(-8, -2, -3, -4)
    }

    "interval from pitch props requires step, alt and dir" {
        interval(Pitch(step = 0, alt = 0, dir = 1)).name shouldBe "1P"
        interval(Pitch(step = 0, alt = -2, dir = 1)).name shouldBe "1dd"
        interval(Pitch(step = 1, alt = 1, dir = 1)).name shouldBe "2A"
        interval(Pitch(step = 2, alt = -2, dir = 1)).name shouldBe "3d"
        interval(Pitch(step = 1, alt = 1, dir = -1)).name shouldBe "-2A"
        interval(Pitch(step = 1000, alt = 0)).empty shouldBe true
    }

    "interval from pitch props accepts octave" {
        interval(Pitch(step = 0, alt = 0, oct = 0, dir = 1)).name shouldBe "1P"
        interval(Pitch(step = 0, alt = -1, oct = 1, dir = -1)).name shouldBe "-8d"
        interval(Pitch(step = 0, alt = 1, oct = 2, dir = -1)).name shouldBe "-15A"
        interval(Pitch(step = 1, alt = -1, oct = 1, dir = -1)).name shouldBe "-9m"
        interval(Pitch(step = 0, alt = 0, oct = 0, dir = 1)).name shouldBe "1P"
    }

    "isInterval" {
        isInterval(interval("P5")) shouldBe true
        isInterval(interval("M3")) shouldBe true
        isInterval(interval("10i")) shouldBe false
        isInterval(null) shouldBe false
        isInterval(Pitch(step = 0, alt = 0)) shouldBe false
    }
})
