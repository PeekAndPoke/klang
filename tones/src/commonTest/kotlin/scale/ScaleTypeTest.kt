package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pcset.PcSet

class ScaleTypeTest : StringSpec({
    "ScaleType basic properties" {
        val pcset = PcSet.get(listOf("1P", "3M", "5P"))
        val scaleType = ScaleType(pcset, "major triad", listOf("triad"), listOf("1P", "3M", "5P"))

        scaleType.name shouldBe "major triad"
        scaleType.aliases shouldBe listOf("triad")
        scaleType.intervals shouldBe listOf("1P", "3M", "5P")
        scaleType.empty shouldBe false
        scaleType.setNum shouldBe pcset.setNum
        scaleType.chroma shouldBe pcset.chroma
        scaleType.normalized shouldBe pcset.normalized
    }

    "ScaleType.NoScaleType" {
        ScaleType.NoScaleType.empty shouldBe true
        ScaleType.NoScaleType.name shouldBe ""
    }
})
