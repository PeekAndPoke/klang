package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCoarseSpec : StringSpec({

    "top-level coarse() sets VoiceData.coarse correctly" {
        // Given a coarse pattern with space-delimited values
        val p = coarse("1 2 4")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then coarse values are set
        events.size shouldBe 3
        events[0].data.coarse shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.coarse shouldBe (2.0 plusOrMinus EPSILON)
        events[2].data.coarse shouldBe (4.0 plusOrMinus EPSILON)
    }

    "control pattern coarse() sets coarse on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying coarse as control pattern with space-delimited values
        val p = base.coarse("1 2 3")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and coarse values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.coarse shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.coarse shouldBe (2.0 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.coarse shouldBe (3.0 plusOrMinus EPSILON)
    }

    "coarse() with value 1 (normal rate)" {
        // Given coarse with 1 (no downsampling)
        val p = sound("bd").coarse("1")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then coarse is 1 (normal sample rate)
        events.size shouldBe 1
        events[0].data.coarse shouldBe (1.0 plusOrMinus EPSILON)
    }

    "coarse() with high values (heavy downsampling)" {
        // Given coarse with high values
        val p = sound("bd hh").coarse("8 16")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then high coarse values are applied
        events.size shouldBe 2
        events[0].data.coarse shouldBe (8.0 plusOrMinus EPSILON)
        events[1].data.coarse shouldBe (16.0 plusOrMinus EPSILON)
    }

    "coarse() works within compiled code" {
        val p = StrudelPattern.compile("""coarse("1 2 4 8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.coarse shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.coarse shouldBe (2.0 plusOrMinus EPSILON)
        events[2].data.coarse shouldBe (4.0 plusOrMinus EPSILON)
        events[3].data.coarse shouldBe (8.0 plusOrMinus EPSILON)
    }

    "coarse() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").coarse("2 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.coarse shouldBe (2.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.coarse shouldBe (4.0 plusOrMinus EPSILON)
    }
})
