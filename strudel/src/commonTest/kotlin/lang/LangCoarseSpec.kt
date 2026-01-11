package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCoarseSpec : StringSpec({

    "coarse() sets VoiceData.coarse" {
        val p = coarse("2 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(2.0, 4.0)
    }

    "coarse() works as pattern extension" {
        val p = note("c").coarse("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }

    "coarse() works as string extension" {
        val p = "c".coarse("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }

    "coarse() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").coarse("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }
})
