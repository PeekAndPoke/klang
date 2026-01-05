package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.chord.Chord

class KeyTest : StringSpec({
    "majorTonicFromKeySignature" {
        Key.majorTonicFromKeySignature("###") shouldBe "A"
        Key.majorTonicFromKeySignature(3) shouldBe "A"
        Key.majorTonicFromKeySignature("b") shouldBe "F"
        Key.majorTonicFromKeySignature("bb") shouldBe "Bb"
        Key.majorTonicFromKeySignature("other") shouldBe null
    }

    "valid chord names" {
        val major = Key.majorKey("C")
        val minor = Key.minorKey("C")

        listOf(
            major.chords,
            major.secondaryDominants,
            major.substituteDominantSupertonics,
            major.substituteDominants,
            major.substituteDominantsMinorRelative,
            minor.natural.chords,
            minor.harmonic.chords,
            minor.melodic.chords
        ).forEach { chords ->
            chords.forEach { name ->
                if (name.isNotEmpty()) {
                    Chord.get(name).empty shouldBe false
                }
            }
        }
    }
})
