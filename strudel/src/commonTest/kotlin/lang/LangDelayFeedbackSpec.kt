package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelayFeedbackSpec : StringSpec({

    "delayfeedback() sets VoiceData.delayFeedback" {
        val p = delayfeedback("0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.5, 0.7)
    }

    "delayfeedback() works as pattern extension" {
        val p = note("c").delayfeedback("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }

    "delayfeedback() works as string extension" {
        val p = "c".delayfeedback("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }

    "delayfeedback() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").delayfeedback("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }
})
