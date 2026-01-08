package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCrushSpec : StringSpec({

    "top-level crush() sets VoiceData.crush correctly" {
        // Given a crush pattern with space-delimited values
        val p = crush("1 4 8")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then crush values are set
        events.size shouldBe 3
        events[0].data.crush shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.crush shouldBe (4.0 plusOrMinus EPSILON)
        events[2].data.crush shouldBe (8.0 plusOrMinus EPSILON)
    }

    "control pattern crush() sets crush on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying crush as control pattern with space-delimited values
        val p = base.crush("2 4 8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and crush values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.crush shouldBe (2.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.crush shouldBe (4.0 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.crush shouldBe (8.0 plusOrMinus EPSILON)
    }

    "crush() with low values (high bit reduction)" {
        // Given crush with low values for maximum bit crushing
        val p = sound("bd hh").crush("1 2")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then low crush values are applied
        events.size shouldBe 2
        events[0].data.crush shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.crush shouldBe (2.0 plusOrMinus EPSILON)
    }

    "crush() with high values (minimal bit reduction)" {
        // Given crush with high values
        val p = sound("bd").crush("16")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then high crush value is applied (minimal crushing)
        events.size shouldBe 1
        events[0].data.crush shouldBe (16.0 plusOrMinus EPSILON)
    }

    "crush() works within compiled code" {
        val p = StrudelPattern.compile("""crush("1 4 8 16")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.crush shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.crush shouldBe (4.0 plusOrMinus EPSILON)
        events[2].data.crush shouldBe (8.0 plusOrMinus EPSILON)
        events[3].data.crush shouldBe (16.0 plusOrMinus EPSILON)
    }

    "crush() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").crush("2 8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.crush shouldBe (2.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.crush shouldBe (8.0 plusOrMinus EPSILON)
    }
})
