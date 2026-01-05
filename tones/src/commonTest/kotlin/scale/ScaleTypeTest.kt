package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pcset.PcSet

class ScaleTypeTest : StringSpec({
    "ScaleType basic properties" {
        val pcSet = PcSet.get(listOf("1P", "3M", "5P"))
        val scaleType = ScaleType(pcSet, "major triad", listOf("triad"), listOf("1P", "3M", "5P"))

        scaleType.name shouldBe "major triad"
        scaleType.aliases shouldBe listOf("triad")
        scaleType.intervals shouldBe listOf("1P", "3M", "5P")
        scaleType.empty shouldBe false
        scaleType.setNum shouldBe pcSet.setNum
        scaleType.chroma shouldBe pcSet.chroma
        scaleType.normalized shouldBe pcSet.normalized
    }

    "ScaleType.NoScaleType" {
        ScaleType.NoScaleType.empty shouldBe true
        ScaleType.NoScaleType.name shouldBe ""
    }
})
