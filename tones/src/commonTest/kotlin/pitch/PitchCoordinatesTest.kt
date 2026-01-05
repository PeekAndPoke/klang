@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.tones.pitch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PitchCoordinatesTest : StringSpec({
    "PitchCoordinates.toList" {
        PitchCoordinates.PitchClass(3).toList() shouldBe listOf(3)
        PitchCoordinates.Note(3, 3).toList() shouldBe listOf(3, 3)
        PitchCoordinates.Interval(1, 0, 1).toList() shouldBe listOf(1, 0, 1)
    }
})
