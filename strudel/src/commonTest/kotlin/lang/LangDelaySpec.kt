package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelaySpec : StringSpec({

    "top-level delay() sets VoiceData.delay correctly" {
        // Given a delay pattern with space-delimited values
        val p = delay("0.0 0.5 1.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delay values are set
        events.size shouldBe 3
        events[0].data.delay shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.delay shouldBe (1.0 plusOrMinus EPSILON)
    }

    "control pattern delay() sets delay on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying delay as control pattern with space-delimited values
        val p = base.delay("0.2 0.5 0.8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and delay values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.delay shouldBe (0.2 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.delay shouldBe (0.8 plusOrMinus EPSILON)
    }

    "delay() with zero value (no delay)" {
        // Given delay with 0 (dry signal)
        val p = sound("bd").delay("0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delay is 0 (no effect)
        events.size shouldBe 1
        events[0].data.delay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "delay() with value 1 (fully wet)" {
        // Given delay with 1 (full wet signal)
        val p = sound("bd").delay("1")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delay is 1 (fully wet)
        events.size shouldBe 1
        events[0].data.delay shouldBe (1.0 plusOrMinus EPSILON)
    }

    "delay() works within compiled code" {
        val p = StrudelPattern.compile("""delay("0.0 0.25 0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.delay shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.delay shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.delay shouldBe (0.75 plusOrMinus EPSILON)
    }

    "delay() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").delay("0.3 0.7")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.delay shouldBe (0.3 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delay shouldBe (0.7 plusOrMinus EPSILON)
    }
})
