package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelaySpec : StringSpec({

    "delay() sets VoiceData.delay" {
        val p = delay("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.5, 0.8)
    }

    "delay() works as pattern extension" {
        val p = note("c").delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works as string extension" {
        val p = "c".delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").delay("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }
})
