package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNewFeaturesIntegrationSpec : StringSpec({

    "All new pitch envelope functions compile and work" {
        val p = StrudelPattern.compile("""note("c").pattack("0.1").penv("12")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
        events[0].data.pEnv shouldBe 12.0
    }

    "All new loop control functions compile and work" {
        val p = StrudelPattern.compile("""sound("bd").loopBegin("0.25").loopEnd("0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
        events[0].data.loopEnd shouldBe 0.75
    }

    "Splice function compiles and works" {
        val p = StrudelPattern.compile("""sound("bd").splice(4, 1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.begin shouldBe 0.25
        events[0].data.end shouldBe 0.5
        events[0].data.speed shouldBe 4.0
    }
})
