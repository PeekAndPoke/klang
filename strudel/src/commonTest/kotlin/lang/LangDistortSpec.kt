package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDistortSpec : StringSpec({

    "top-level distort() sets VoiceData.distort correctly" {
        // Given a distort pattern with space-delimited values
        val p = distort("0.5 1.0 2.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then distort values are set
        events.size shouldBe 3
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.distort shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.distort shouldBe (2.0 plusOrMinus EPSILON)
    }

    "control pattern distort() sets distort on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying distort as control pattern with space-delimited values
        val p = base.distort("0.5 1.5 3.0")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and distort values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.distort shouldBe (1.5 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.distort shouldBe (3.0 plusOrMinus EPSILON)
    }

    "distort() with zero value" {
        // Given distort with 0
        val p = sound("bd").distort("0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then distort is 0 (no distortion)
        events.size shouldBe 1
        events[0].data.distort shouldBe (0.0 plusOrMinus EPSILON)
    }

    "distort() with high values" {
        // Given distort with high values
        val p = sound("bd hh").distort("10.0 20.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then high distort values are applied
        events.size shouldBe 2
        events[0].data.distort shouldBe (10.0 plusOrMinus EPSILON)
        events[1].data.distort shouldBe (20.0 plusOrMinus EPSILON)
    }

    "distort() works within compiled code" {
        val p = StrudelPattern.compile("""distort("0.5 1.0 2.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 3
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.distort shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.distort shouldBe (2.0 plusOrMinus EPSILON)
    }

    "distort() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").distort("1.5 3.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.distort shouldBe (1.5 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.distort shouldBe (3.0 plusOrMinus EPSILON)
    }
})
