package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDecaySpec : StringSpec({

    "decay() sets VoiceData.adsr.decay" {
        val p = decay("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.decay } shouldBe listOf(0.1, 0.5)
    }

    "decay() works as pattern extension" {
        val p = note("c").decay("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.decay shouldBe 0.1
    }

    "decay() works as string extension" {
        val p = "c".decay("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.decay shouldBe 0.1
    }

    "decay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").decay("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.adsr.decay shouldBe 0.1
    }
})
