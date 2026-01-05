package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pcset.PcSet

class ScaleTypeTest : StringSpec({
    "list names" {
        ScaleTypeDictionary.all() shouldHaveSize 92
        ScaleTypeDictionary.all()[0].name shouldBe "major pentatonic"
    }

    "get" {
        val major = ScaleTypeDictionary.get("major")
        major.empty shouldBe false
        major.setNum shouldBe 2773
        major.name shouldBe "major"
        major.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        major.aliases shouldBe listOf("ionian")
        major.chroma shouldBe "101011010101"
        major.normalized shouldBe "101010110101"
    }

    "not valid get type" {
        val unknown = ScaleTypeDictionary.get("unknown")
        unknown.empty shouldBe true
        unknown.name shouldBe ""
        unknown.setNum shouldBe 0
        unknown.aliases shouldBe emptyList<String>()
        unknown.chroma shouldBe "000000000000"
        unknown.intervals shouldBe emptyList<String>()
        unknown.normalized shouldBe "000000000000"
    }

    "add a scale type" {
        ScaleTypeDictionary.add(listOf("1P", "5P"), "quinta")
        ScaleTypeDictionary.get("quinta").chroma shouldBe "100000010000"

        ScaleTypeDictionary.add(listOf("1P", "5P"), "quinta", listOf("q", "Q"))
        ScaleTypeDictionary.get("q") shouldBe ScaleTypeDictionary.get("quinta")
        ScaleTypeDictionary.get("Q") shouldBe ScaleTypeDictionary.get("quinta")
    }

    "major modes" {
        val chromas = PcSet.modes(ScaleTypeDictionary.get("major").intervals, true)
        val names = chromas.map { chroma -> ScaleTypeDictionary.get(chroma).name }
        names shouldBe listOf(
            "major",
            "dorian",
            "phrygian",
            "lydian",
            "mixolydian",
            "minor",
            "locrian",
        )
    }

    "harmonic minor modes" {
        val chromas = PcSet.modes(ScaleTypeDictionary.get("harmonic minor").intervals, true)
        val names = chromas.map { chroma -> ScaleTypeDictionary.get(chroma).name }
        names shouldBe listOf(
            "harmonic minor",
            "locrian 6",
            "major augmented",
            "dorian #4",
            "phrygian dominant",
            "lydian #9",
            "ultralocrian",
        )
    }

    "melodic minor modes" {
        val chromas = PcSet.modes(ScaleTypeDictionary.get("melodic minor").intervals, true)
        val names = chromas.map { chroma -> ScaleTypeDictionary.get(chroma).name }
        names shouldBe listOf(
            "melodic minor",
            "dorian b2",
            "lydian augmented",
            "lydian dominant",
            "mixolydian b6",
            "locrian #2",
            "altered",
        )
    }

    "clear dictionary" {
        ScaleTypeDictionary.removeAll()
        ScaleTypeDictionary.all() shouldBe emptyList<ScaleType>()
        ScaleTypeDictionary.keys() shouldBe emptyList<String>()
    }
})
