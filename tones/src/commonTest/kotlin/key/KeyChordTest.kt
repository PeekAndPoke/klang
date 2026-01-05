package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyChordTest : StringSpec({
    "C major chords" {
        val chords = Key.majorKeyChords("C")
        chords.find { it.name == "Em7" } shouldBe KeyChord(
            name = "Em7",
            roles = listOf("T", "ii/II")
        )
    }
})
