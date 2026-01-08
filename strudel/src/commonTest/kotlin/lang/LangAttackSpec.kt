package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangAttackSpec : StringSpec({

    "top-level attack() sets VoiceData.adsr.attack correctly" {
        val p = attack("0.2 0.4")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.attack } shouldBe listOf(0.2, 0.4)
    }

    "control pattern attack() sets VoiceData.adsr.attack on existing pattern" {
        val base = note("c3 e3")
        val p = base.attack("0.05 0.1")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.adsr.attack } shouldBe listOf(0.05, 0.1, 0.05, 0.1)
    }

    "attack() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""attack("0.2 0.4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.attack } shouldBe listOf(0.2, 0.4)
    }

    "attack() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").attack("0.2 0.4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.attack } shouldBe listOf(0.2, 0.4)
    }
})
