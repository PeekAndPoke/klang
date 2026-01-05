package io.peekandpoke.klang.tones.pcset

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PcSetTest : StringSpec({
    "pcset from note list" {
        PcSet.get(listOf("c", "d", "e")) shouldBe Pcset(
            empty = false,
            name = "",
            setNum = 2688,
            chroma = "101010000000",
            normalized = "100000001010",
            intervals = listOf("1P", "2M", "3M")
        )
        PcSet.get(listOf("d", "e", "c")) shouldBe PcSet.get(listOf("c", "d", "e"))
        PcSet.get(listOf("not a note or interval")).empty shouldBe true
        PcSet.get(emptyList<String>()).empty shouldBe true
    }

    "pcset from pcset number" {
        PcSet.get(2048) shouldBe PcSet.get(listOf("C"))
        val set = PcSet.get(listOf("D"))
        PcSet.get(set.setNum) shouldBe set
    }

    "pcset num" {
        PcSet.num("000000000001") shouldBe 1
        PcSet.num(listOf("B")) shouldBe 1
        PcSet.num(listOf("Cb")) shouldBe 1
        PcSet.num(listOf("C", "E", "G")) shouldBe 2192
        PcSet.num(listOf("C")) shouldBe 2048
        PcSet.num("100000000000") shouldBe 2048
        PcSet.num("111111111111") shouldBe 4095
    }

    "pcset normalized" {
        val likeC = PcSet.get(listOf("C")).chroma // 100000000000
        "cdefgab".forEach { pc ->
            PcSet.get(listOf(pc.toString())).normalized shouldBe likeC
        }
        PcSet.get(listOf("E", "F#")).normalized shouldBe PcSet.get(listOf("C", "D")).normalized
    }

    "chroma" {
        PcSet.chroma(listOf("C")) shouldBe "100000000000"
        PcSet.chroma(listOf("D")) shouldBe "001000000000"
        PcSet.chroma(listOf("c", "d", "e")) shouldBe "101010000000"
        PcSet.chroma("g g#4 a bb5".split(" ")) shouldBe "000000011110"
        PcSet.chroma("P1 M2 M3 P4 P5 M6 M7".split(" ")) shouldBe PcSet.chroma("c d e f g a b".split(" "))
        PcSet.chroma("101010101010") shouldBe "101010101010"
        PcSet.chroma(listOf("one", "two")) shouldBe "000000000000"
        PcSet.chroma("A B C") shouldBe "000000000000"
    }

    "chromas" {
        PcSet.chromas().size shouldBe 2048
        PcSet.chromas()[0] shouldBe "100000000000"
        PcSet.chromas()[2047] shouldBe "111111111111"
    }

    "intervals" {
        PcSet.intervals("101010101010") shouldBe "1P 2M 3M 5d 6m 7m".split(" ")
        PcSet.intervals("1010") shouldBe emptyList()
        PcSet.intervals(listOf("C", "G", "B")) shouldBe listOf("1P", "5P", "7M")
        PcSet.intervals(listOf("D", "F", "A")) shouldBe listOf("2M", "4P", "6M")
    }

    "isChroma" {
        PcSet.get("101010101010").chroma shouldBe "101010101010"
        PcSet.get("1010101").chroma shouldBe "000000000000"
        PcSet.get("blah").chroma shouldBe "000000000000"
        PcSet.get("c d e").chroma shouldBe "000000000000"
    }

    "isSubsetOf" {
        val isInCMajor = PcSet.isSubsetOf("c4 e6 g".split(" "))
        isInCMajor("c2 g7".split(" ")) shouldBe true
        isInCMajor("c2 e".split(" ")) shouldBe true
        isInCMajor("c2 e3 g4".split(" ")) shouldBe false
        isInCMajor("c2 e3 b5".split(" ")) shouldBe false
        PcSet.isSubsetOf("c d e".split(" "))(listOf("C", "D")) shouldBe true
    }

    "isSubsetOf with chroma" {
        val isSubset = PcSet.isSubsetOf("101010101010")
        isSubset("101000000000") shouldBe true
        isSubset("111000000000") shouldBe false
    }

    "isSupersetOf" {
        val extendsCMajor = PcSet.isSupersetOf(listOf("c", "e", "g"))
        extendsCMajor("c2 g3 e4 f5".split(" ")) shouldBe true
        extendsCMajor("e c g".split(" ")) shouldBe false
        extendsCMajor("c e f".split(" ")) shouldBe false
        PcSet.isSupersetOf(listOf("c", "d"))(listOf("c", "d", "e")) shouldBe true
    }

    "isSupersetOf with chroma" {
        val isSuperset = PcSet.isSupersetOf("101000000000")
        isSuperset("101010101010") shouldBe true
        isSuperset("110010101010") shouldBe false
    }

    "isEqual" {
        PcSet.isEqual("c2 d3 e7 f5".split(" "), "c4 c d5 e6 f1".split(" ")) shouldBe true
        PcSet.isEqual("c f".split(" "), "c4 c f1".split(" ")) shouldBe true
    }

    "isNoteIncludedIn" {
        val isIncludedInC = PcSet.isNoteIncludedIn(listOf("c", "d", "e"))
        isIncludedInC("C4") shouldBe true
        isIncludedInC("C#4") shouldBe false
    }

    "filter" {
        val inCMajor = PcSet.filter("c d e".split(" "))
        inCMajor("c2 c#2 d2 c3 c#3 d3".split(" ")) shouldBe "c2 d2 c3 d3".split(" ")
        PcSet.filter(listOf("c"))("c2 c#2 d2 c3 c#3 d3".split(" ")) shouldBe listOf("c2", "c3")
    }

    "notes" {
        PcSet.notes("c d e f g a b".split(" ")) shouldBe "C D E F G A B".split(" ")
        PcSet.notes("b a g f e d c".split(" ")) shouldBe "C D E F G A B".split(" ")
        PcSet.notes("D3 A3 Bb3 C4 D4 E4 F4 G4 A4".split(" ")) shouldBe "C D E F G A Bb".split(" ")
        PcSet.notes("101011010110") shouldBe "C D E F G A Bb".split(" ")
        PcSet.notes(listOf("blah", "x")) shouldBe emptyList()
    }

    "modes" {
        PcSet.modes("c d e f g a b".split(" ")) shouldBe listOf(
            "101011010101",
            "101101010110",
            "110101011010",
            "101010110101",
            "101011010110",
            "101101011010",
            "110101101010",
        )
        PcSet.modes("c d e f g a b".split(" "), false) shouldBe listOf(
            "101011010101",
            "010110101011",
            "101101010110",
            "011010101101",
            "110101011010",
            "101010110101",
            "010101101011",
            "101011010110",
            "010110101101",
            "101101011010",
            "011010110101",
            "110101101010",
        )
        PcSet.modes(listOf("blah", "bleh")) shouldBe emptyList()
    }
})
