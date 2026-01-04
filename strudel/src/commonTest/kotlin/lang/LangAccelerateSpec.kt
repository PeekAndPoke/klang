package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangAccelerateSpec : StringSpec({

    "top-level accelerate() sets VoiceData.accelerate correctly" {
        val p = accelerate("-0.5 0.75")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(-0.5, 0.75)
    }

    "control pattern accelerate() sets VoiceData.accelerate on existing pattern" {
        val base = note("c3 e3")
        val p = base.accelerate("0.1 -0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.accelerate } shouldBe listOf(0.1, -0.2, 0.1, -0.2)
    }
})
