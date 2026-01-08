package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelayTimeSpec : StringSpec({

    "top-level delaytime() sets VoiceData.delayTime correctly" {
        // Given a delaytime pattern with space-delimited values (in seconds)
        val p = delaytime("0.1 0.25 0.5")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then delayTime values are set
        events.size shouldBe 3
        events[0].data.delayTime shouldBe (0.1 plusOrMinus EPSILON)
        events[1].data.delayTime shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.delayTime shouldBe (0.5 plusOrMinus EPSILON)
    }

    "control pattern delaytime() sets delayTime on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying delaytime as control pattern with space-delimited values
        val p = base.delaytime("0.125 0.25 0.5")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and delayTime values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.delayTime shouldBe (0.125 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delayTime shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.delayTime shouldBe (0.5 plusOrMinus EPSILON)
    }

    "delaytime() with short delay times" {
        // Given short delay times
        val p = sound("bd hh").delaytime("0.01 0.05")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then short delayTime values are applied
        events.size shouldBe 2
        events[0].data.delayTime shouldBe (0.01 plusOrMinus EPSILON)
        events[1].data.delayTime shouldBe (0.05 plusOrMinus EPSILON)
    }

    "delaytime() with long delay times" {
        // Given long delay times
        val p = sound("bd hh").delaytime("1.0 2.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then long delayTime values are applied
        events.size shouldBe 2
        events[0].data.delayTime shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.delayTime shouldBe (2.0 plusOrMinus EPSILON)
    }

    "delaytime() works within compiled code" {
        val p = StrudelPattern.compile("""delaytime("0.1 0.2 0.3 0.4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.delayTime shouldBe (0.1 plusOrMinus EPSILON)
        events[1].data.delayTime shouldBe (0.2 plusOrMinus EPSILON)
        events[2].data.delayTime shouldBe (0.3 plusOrMinus EPSILON)
        events[3].data.delayTime shouldBe (0.4 plusOrMinus EPSILON)
    }

    "delaytime() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").delaytime("0.25 0.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.delayTime shouldBe (0.25 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.delayTime shouldBe (0.5 plusOrMinus EPSILON)
    }
})
