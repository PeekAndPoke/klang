package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPanSpec : StringSpec({

    "top-level pan() sets VoiceData.pan correctly" {
        val p = pan("0.5 -0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pan } shouldBe listOf(0.5, -0.5)
    }

    "control pattern pan() sets pan on existing pattern" {
        val base = note("c3 e3")
        val p = base.pan("-1 1")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.pan shouldBe -1.0
        events[1].data.pan shouldBe 1.0
    }

    "pan() works as string extension" {
        val p = "c3".pan("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pan shouldBe 0.5
    }

    "pan() works within compiled code" {
        val p = StrudelPattern.compile("""pan("-0.5 0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pan } shouldBe listOf(-0.5, 0.5)
    }

    "pan() as method works within compiled code" {
        val p = StrudelPattern.compile("""note("c").pan("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.pan shouldBe 0.5
    }
})
