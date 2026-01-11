package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCrushSpec : StringSpec({

    "crush() sets VoiceData.crush" {
        val p = crush("4 8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(4.0, 8.0)
    }

    "crush() works as pattern extension" {
        val p = note("c").crush("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }

    "crush() works as string extension" {
        val p = "c".crush("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }

    "crush() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").crush("4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }
})
