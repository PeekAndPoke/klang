package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDistortSpec : StringSpec({

    "distort() sets VoiceData.distort" {
        val p = distort("0.5 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.5, 10.0)
    }

    "distort() works as pattern extension" {
        val p = note("c").distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works as string extension" {
        val p = "c".distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").distort("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }
})
