package io.peekandpoke.klang.tones.mode

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pitch.NamedPitch

class ModeDictionaryTest : StringSpec({
    "mode properties" {
        val ionian = ModeDictionary.get("ionian")
        ionian.empty shouldBe false
        ionian.modeNum shouldBe 0
        ionian.name shouldBe "ionian"
        ionian.setNum shouldBe 2773
        ionian.chroma shouldBe "101011010101"
        ionian.normalized shouldBe "101011010101"
        ionian.alt shouldBe 0
        ionian.triad shouldBe ""
        ionian.seventh shouldBe "Maj7"
        ionian.aliases shouldBe listOf("major")
        ionian.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")

        ModeDictionary.get("major") shouldBe ionian
    }

    "accept NamedPitch as parameter" {
        ModeDictionary.get(ModeDictionary.get("major")) shouldBe ModeDictionary.get("major")
        ModeDictionary.get(object : NamedPitch {
            override val name = "Major"
        }) shouldBe ModeDictionary.get("major")
    }

    "name is case independent" {
        ModeDictionary.get("Dorian") shouldBe ModeDictionary.get("dorian")
    }

    "setNum" {
        val pcsets = ModeDictionary.names().map { ModeDictionary.get(it).setNum }
        pcsets shouldBe listOf(2773, 2902, 3418, 2741, 2774, 2906, 3434)
    }

    "alt" {
        val alt = ModeDictionary.names().map { ModeDictionary.get(it).alt }
        alt shouldBe listOf(0, 2, 4, -1, 1, 3, 5)
    }

    "triad" {
        val triads = ModeDictionary.names().map { ModeDictionary.get(it).triad }
        triads shouldBe listOf("", "m", "m", "", "", "m", "dim")
    }

    "seventh" {
        val sevenths = ModeDictionary.names().map { ModeDictionary.get(it).seventh }
        sevenths shouldBe listOf("Maj7", "m7", "m7", "Maj7", "7", "m7", "m7b5")
    }

    "aliases" {
        ModeDictionary.get("major") shouldBe ModeDictionary.get("ionian")
        ModeDictionary.get("minor") shouldBe ModeDictionary.get("aeolian")
    }

    "names" {
        ModeDictionary.names() shouldBe listOf(
            "ionian",
            "dorian",
            "phrygian",
            "lydian",
            "mixolydian",
            "aeolian",
            "locrian"
        )
    }
})
