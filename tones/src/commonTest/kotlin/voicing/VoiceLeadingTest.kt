package io.peekandpoke.klang.tones.voicing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoiceLeadingTest : StringSpec({
    "topNoteDiff" {
        VoiceLeading.topNoteDiff(
            listOf(
                listOf("F3", "A3", "C4", "E4"),
                listOf("C4", "E4", "F4", "A4")
            ),
            listOf("C4", "E4", "G4", "B4")
        ) shouldBe listOf("C4", "E4", "F4", "A4")
    }
})
