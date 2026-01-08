package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelayFeedbackSpec : StringSpec({

    "top-level delayfeedback() sets VoiceData.delayFeedback correctly" {
        // Given a delayfeedback pattern with space-delimited values
        val p = delayfeedback("0.0 0.5 0.9")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delayFeedback values are set
        events.size shouldBe 3
        events[0].data.delayFeedback shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.delayFeedback shouldBe (0.9 plusOrMinus EPSILON)
    }

    "control pattern delayfeedback() sets delayFeedback on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying delayfeedback as control pattern with space-delimited values
        val p = base.delayfeedback("0.2 0.5 0.8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and delayFeedback values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.delayFeedback shouldBe (0.2 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.delayFeedback shouldBe (0.8 plusOrMinus EPSILON)
    }

    "delayfeedback() with zero value (no repeats)" {
        // Given delayfeedback with 0 (no feedback)
        val p = sound("bd").delayfeedback("0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delayFeedback is 0 (no repeats)
        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe (0.0 plusOrMinus EPSILON)
    }

    "delayfeedback() with high value (long echo tail)" {
        // Given delayfeedback with high value approaching 1
        val p = sound("bd").delayfeedback("0.95")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then high delayFeedback is applied (long echo)
        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe (0.95 plusOrMinus EPSILON)
    }

    "delayfeedback() with moderate values" {
        // Given delayfeedback with moderate values
        val p = sound("bd hh").delayfeedback("0.3 0.6")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then moderate feedback values are applied
        events.size shouldBe 2
        events[0].data.delayFeedback shouldBe (0.3 plusOrMinus EPSILON)
        events[1].data.delayFeedback shouldBe (0.6 plusOrMinus EPSILON)
    }

    "delayfeedback() works within compiled code" {
        val p = StrudelPattern.compile("""delayfeedback("0.0 0.25 0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.delayFeedback shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.delayFeedback shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.delayFeedback shouldBe (0.75 plusOrMinus EPSILON)
    }

    "delayfeedback() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").delayfeedback("0.4 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.delayFeedback shouldBe (0.4 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delayFeedback shouldBe (0.8 plusOrMinus EPSILON)
    }
})
