package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TimeSignatureTest : StringSpec({
    "get" {
        val ts = TimeSignature.get("4/4")
        ts.empty shouldBe false
        ts.name shouldBe "4/4"
        ts.type shouldBe "simple"
        ts.upper shouldBe 4
        ts.lower shouldBe 4
        ts.additive shouldBe emptyList()
    }

    "get invalid" {
        TimeSignature.get("0/0").empty shouldBe true
    }

    "simple" {
        TimeSignature.get("4/4").type shouldBe "simple"
        TimeSignature.get("3/4").type shouldBe "simple"
        TimeSignature.get("2/4").type shouldBe "simple"
        TimeSignature.get("2/2").type shouldBe "simple"
    }

    "compound" {
        TimeSignature.get("3/8").type shouldBe "compound"
        TimeSignature.get("6/8").type shouldBe "compound"
        TimeSignature.get("9/8").type shouldBe "compound"
        TimeSignature.get("12/8").type shouldBe "compound"
    }

    "irregular" {
        TimeSignature.get("2+3+3/8").type shouldBe "irregular"
        TimeSignature.get("3+2+2/8").type shouldBe "irregular"
    }

    "irrational" {
        TimeSignature.get("12/10").type shouldBe "irrational"
        TimeSignature.get("12/19").type shouldBe "irrational"
    }

    "names" {
        TimeSignature.names() shouldBe listOf(
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
