package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSpreadSpec : StringSpec({

    "spread() sets VoiceData.panSpread" {
        val p = spread("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.panSpread } shouldBe listOf(0.5, 1.0)
    }

    "spread() works as pattern extension" {
        val p = note("c").spread("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.panSpread shouldBe 0.5
    }

    "spread() works as string extension" {
        val p = "c".spread("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.panSpread shouldBe 0.5
    }

    "spread() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").spread("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.panSpread shouldBe 0.5
    }
})
