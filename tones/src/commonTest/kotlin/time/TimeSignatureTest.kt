package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TimeSignatureTest : StringSpec({
    "get" {
        val ts = getTimeSignature("4/4")
        ts.empty shouldBe false
        ts.name shouldBe "4/4"
        ts.type shouldBe "simple"
        ts.upper shouldBe 4
        ts.lower shouldBe 4
        ts.additive shouldBe emptyList()
    }

    "get invalid" {
        getTimeSignature("0/0").empty shouldBe true
    }

    "simple" {
        getTimeSignature("4/4").type shouldBe "simple"
        getTimeSignature("3/4").type shouldBe "simple"
        getTimeSignature("2/4").type shouldBe "simple"
        getTimeSignature("2/2").type shouldBe "simple"
    }

    "compound" {
        getTimeSignature("3/8").type shouldBe "compound"
        getTimeSignature("6/8").type shouldBe "compound"
        getTimeSignature("9/8").type shouldBe "compound"
        getTimeSignature("12/8").type shouldBe "compound"
    }

    "irregular" {
        getTimeSignature("2+3+3/8").type shouldBe "irregular"
        getTimeSignature("3+2+2/8").type shouldBe "irregular"
    }

    "irrational" {
        getTimeSignature("12/10").type shouldBe "irrational"
        getTimeSignature("12/19").type shouldBe "irrational"
    }

    "names" {
        timeSignatureNames() shouldBe listOf(
            "4/4",
            "3/4",
            "2/4",
            "2/2",
            "12/8",
            "9/8",
            "6/8",
            "3/8"
        )
    }
})
