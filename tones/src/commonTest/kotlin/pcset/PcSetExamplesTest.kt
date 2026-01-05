package io.peekandpoke.klang.tones.pcset

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PcSetExamplesTest : StringSpec({
    "Pcset.get" {
        val s = get(listOf("c", "d", "e"))
        s.setNum shouldBe 2688
        s.chroma shouldBe "101010000000"
        s.intervals shouldBe listOf("1P", "2M", "3M")

        get(2688).chroma shouldBe "101010000000"
        get("101010000000").setNum shouldBe 2688
    }

    "Pcset shorthands" {
        chroma(listOf("c", "d", "e")) shouldBe "101010000000"
        // Note: TonalJS README has num(["c", "d", "e"]) => 2192 but it should be 2688
        num(listOf("c", "d", "e")) shouldBe 2688

        chroma(2688) shouldBe "101010000000"
        num("101010000000") shouldBe 2688
    }

    "Pcset.intervals" {
        // Intervals are always calculated from C
        intervals(listOf("c", "d", "e")) shouldBe listOf("1P", "2M", "3M")
        intervals(listOf("D", "F", "A")) shouldBe listOf("2M", "4P", "6M")
    }

    "Pcset.notes" {
        notes(listOf("D3", "A3", "Bb3", "C4", "D4", "E4", "F4", "G4", "A4")) shouldBe
                listOf("C", "D", "E", "F", "G", "A", "Bb")
        notes("101011010110") shouldBe listOf("C", "D", "E", "F", "G", "A", "Bb")
    }

    "Pcset.isNoteIncludedIn" {
        val isInCTriad = isNoteIncludedIn(listOf("C", "E", "G"))
        isInCTriad("C4") shouldBe true
        isInCTriad("C#4") shouldBe false
        isInCTriad("Fb") shouldBe true
    }

    "Pcset.isSubsetOf" {
        isSubsetOf(listOf("C", "D", "E", "F", "G", "A", "B"))(listOf("C", "E", "G")) shouldBe true
        isSubsetOf(listOf("C", "E", "G"))(listOf("C", "D", "E", "F", "G", "A", "B")) shouldBe false
    }

    "Pcset.isSupersetOf" {
        isSupersetOf(listOf("C", "E", "G"))(listOf("C", "D", "E", "F", "G", "A", "B")) shouldBe true
        isSupersetOf(listOf("C", "D", "E", "F", "G", "A", "B"))(listOf("C", "E", "G")) shouldBe false
    }
})
