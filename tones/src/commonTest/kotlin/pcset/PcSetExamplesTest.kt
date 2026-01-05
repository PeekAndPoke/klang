package io.peekandpoke.klang.tones.pcset

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PcSetExamplesTest : StringSpec({
    "PcSet.get" {
        val s = PcSet.get(listOf("c", "d", "e"))
        s.setNum shouldBe 2688
        s.chroma shouldBe "101010000000"
        s.intervals shouldBe listOf("1P", "2M", "3M")

        PcSet.get(2688).chroma shouldBe "101010000000"
        PcSet.get("101010000000").setNum shouldBe 2688
    }

    "PcSet shorthands" {
        PcSet.chroma(listOf("c", "d", "e")) shouldBe "101010000000"
        // Note: TonalJS README has num(["c", "d", "e"]) => 2192 but it should be 2688
        PcSet.num(listOf("c", "d", "e")) shouldBe 2688

        PcSet.chroma(2688) shouldBe "101010000000"
        PcSet.num("101010000000") shouldBe 2688
    }

    "PcSet.intervals" {
        // Intervals are always calculated from C
        PcSet.intervals(listOf("c", "d", "e")) shouldBe listOf("1P", "2M", "3M")
        PcSet.intervals(listOf("D", "F", "A")) shouldBe listOf("2M", "4P", "6M")
    }

    "PcSet.notes" {
        PcSet.notes(listOf("D3", "A3", "Bb3", "C4", "D4", "E4", "F4", "G4", "A4")) shouldBe
                listOf("C", "D", "E", "F", "G", "A", "Bb")
        PcSet.notes("101011010110") shouldBe listOf("C", "D", "E", "F", "G", "A", "Bb")
    }

    "PcSet.isNoteIncludedIn" {
        val isInCTriad = PcSet.isNoteIncludedIn(listOf("C", "E", "G"))
        isInCTriad("C4") shouldBe true
        isInCTriad("C#4") shouldBe false
        isInCTriad("Fb") shouldBe true
    }

    "PcSet.isSubsetOf" {
        PcSet.isSubsetOf(listOf("C", "D", "E", "F", "G", "A", "B"))(listOf("C", "E", "G")) shouldBe true
        PcSet.isSubsetOf(listOf("C", "E", "G"))(listOf("C", "D", "E", "F", "G", "A", "B")) shouldBe false
    }

    "PcSet.isSupersetOf" {
        PcSet.isSupersetOf(listOf("C", "E", "G"))(listOf("C", "D", "E", "F", "G", "A", "B")) shouldBe true
        PcSet.isSupersetOf(listOf("C", "D", "E", "F", "G", "A", "B"))(listOf("C", "E", "G")) shouldBe false
    }
})
