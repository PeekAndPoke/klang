package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDecaySpec : StringSpec({

    "top-level decay() sets VoiceData.adsr.decay correctly" {
        val p = decay("0.3 0.6")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.decay } shouldBe listOf(0.3, 0.6)
    }

    "control pattern decay() sets VoiceData.adsr.decay on existing pattern" {
        val base = note("c3 e3")
        val p = base.decay("0.2 0.4")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.adsr.decay } shouldBe listOf(0.2, 0.4, 0.2, 0.4)
    }

    "decay() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""decay("0.3 0.6")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.decay } shouldBe listOf(0.3, 0.6)
    }

    "decay() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").decay("0.3 0.6")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.decay } shouldBe listOf(0.3, 0.6)
    }
})
