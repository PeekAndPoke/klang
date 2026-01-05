package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TimeExamplesTest : StringSpec({
    "TimeSignature.get" {
        val ts = TimeSignature.get("3/4")
        ts.empty shouldBe false
        ts.name shouldBe "3/4"
        ts.upper shouldBe 3
        ts.lower shouldBe 4
        ts.type shouldBe "simple"
        ts.additive shouldBe emptyList<Int>()

        val ts2 = TimeSignature.get("3+2+3/8")
        ts2.empty shouldBe false
        ts2.name shouldBe "3+2+3/8"
        ts2.type shouldBe "irregular"
        ts2.upper shouldBe 8
        ts2.lower shouldBe 8
        ts2.additive shouldBe listOf(3, 2, 3)

        val ts3 = TimeSignature.get("12/10")
        ts3.empty shouldBe false
        ts3.name shouldBe "12/10"
        ts3.type shouldBe "irrational"
        ts3.upper shouldBe 12
        ts3.lower shouldBe 10
        ts3.additive shouldBe emptyList<Int>()
    }

    "DurationValue.get" {
        val d = DurationValue.get("quarter")
        d.empty shouldBe false
        d.name shouldBe "quarter"
        d.value shouldBe 0.25
        d.fraction shouldBe Pair(1, 4)
        d.shorthand shouldBe "q"
        d.dots shouldBe ""
        d.names shouldBe listOf("quarter", "crotchet")

        val d2 = DurationValue.get("quarter..")
        d2.empty shouldBe false
        d2.name shouldBe "quarter.."
        d2.value shouldBe 0.4375
        d2.fraction shouldBe Pair(7, 16)
        d2.shorthand shouldBe "q"
        d2.dots shouldBe ".."
        d2.names shouldBe listOf("quarter", "crotchet")

        DurationValue.get("q").value shouldBe DurationValue.get("quarter").value
        DurationValue.get("q.").value shouldBe DurationValue.get("quarter.").value
        DurationValue.get("q..").value shouldBe DurationValue.get("quarter..").value
    }

    "RhythmPattern" {
        Rhythm.binary(13) shouldBe listOf(1, 1, 0, 1)
        Rhythm.binary(12, 13) shouldBe listOf(1, 1, 0, 0, 1, 1, 0, 1)

        Rhythm.hex("8f") shouldBe listOf(1, 0, 0, 0, 1, 1, 1, 1)

        Rhythm.onsets(1, 2, 2, 1) shouldBe listOf(1, 0, 1, 0, 0, 1, 0, 0, 1, 0)

        // rhythmRandom(4) is random

        // rhythmProbability(listOf(0.6, 0.0, 0.2, 0.5)) is random

        Rhythm.rotate(listOf(1, 0, 0, 1), 2) shouldBe listOf(0, 1, 1, 0)

        Rhythm.euclid(8, 3) shouldBe listOf(1, 0, 0, 1, 0, 0, 1, 0)
    }
})
