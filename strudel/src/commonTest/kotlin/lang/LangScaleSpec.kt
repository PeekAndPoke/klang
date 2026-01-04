package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangScaleSpec : StringSpec({

    "top-level scale() sets VoiceData.scale correctly" {
        val p = scale("C4:minor pentatonic")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.scale } shouldBe listOf("C4:minor", "pentatonic")
    }

    "control pattern scale() sets VoiceData.scale on existing pattern" {
        val base = note("c3 e3")
        val p = base.scale("chromatic dorian")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.scale } shouldBe listOf("chromatic", "dorian", "chromatic", "dorian")
    }
})
