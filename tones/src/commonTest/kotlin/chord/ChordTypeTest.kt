package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChordTypeTest : StringSpec({
    "names" {
        // sorted by setNum
        ChordTypeDictionary.names().take(5) shouldBe listOf(
            "fifth",
            "suspended fourth",
            "suspended fourth seventh",
            "augmented",
            "major seventh flat sixth",
        )
    }

    "symbols" {
        // sorted by setNum
        ChordTypeDictionary.symbols().take(3) shouldBe listOf(
            "5",
            "M7#5sus4",
            "7#5sus4",
        )
    }

    "all returns all chords" {
        ChordTypeDictionary.all().size shouldBe 106
    }

    "get" {
        val major = ChordTypeDictionary.get("major")
        major.empty shouldBe false
        major.setNum shouldBe 2192
        major.name shouldBe "major"
        major.quality shouldBe ChordQuality.Major
        major.intervals shouldBe listOf("1P", "3M", "5P")
        major.aliases shouldBe listOf("M", "^", "", "maj")
        major.chroma shouldBe "100010010000"
        major.normalized shouldBe "100001000100"
    }

    "add a chord" {
        ChordTypeDictionary.add(listOf("1P", "5P"), listOf("q"))
        ChordTypeDictionary.get("q").chroma shouldBe "100000010000"

        ChordTypeDictionary.add(listOf("1P", "5P"), listOf("q"), "quinta")
        ChordTypeDictionary.get("quinta").chroma shouldBe ChordTypeDictionary.get("q").chroma
    }

    "clear dictionary" {
        ChordTypeDictionary.removeAll()
        ChordTypeDictionary.all() shouldBe emptyList()
        ChordTypeDictionary.keys() shouldBe emptyList()
        ChordTypeDictionary.reset()
    }
})
