package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

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

    "accelerate() works as string extension" {
        val p = "c3".accelerate("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.accelerate shouldBe 0.5
    }

    "accelerate() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""accelerate("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(0.0, 1.0)
    }

    "accelerate() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").accelerate("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(0.0, 1.0)
    }
})
