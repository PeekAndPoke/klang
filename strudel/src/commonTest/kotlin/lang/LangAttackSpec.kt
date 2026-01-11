package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangAttackSpec : StringSpec({

    "attack() sets VoiceData.adsr.attack" {
        val p = attack("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.attack } shouldBe listOf(0.1, 0.5)
    }

    "attack() works as pattern extension" {
        val p = note("c").attack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.attack shouldBe 0.1
    }

    "attack() works as string extension" {
        val p = "c".attack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.attack shouldBe 0.1
    }

    "attack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").attack("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.adsr.attack shouldBe 0.1
    }
})
