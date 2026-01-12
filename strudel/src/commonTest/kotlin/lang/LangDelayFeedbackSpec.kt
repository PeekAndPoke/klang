package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
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

    "delayfeedback() with continuous pattern sets delayFeedback correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").delayfeedback(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.delayFeedback shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.delayFeedback shouldBe (0.0 plusOrMinus EPSILON)
    }
})
