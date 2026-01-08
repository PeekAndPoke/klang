package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRoomSizeSpec : StringSpec({

    "top-level roomsize() sets VoiceData.roomSize correctly" {
        // Given a roomsize pattern with space-delimited values
        val p = roomsize("0.0 0.5 1.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then roomSize values are set
        events.size shouldBe 3
        events[0].data.roomSize shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.roomSize shouldBe (1.0 plusOrMinus EPSILON)
    }

    "control pattern roomsize() sets roomSize on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying roomsize as control pattern with space-delimited values
        val p = base.roomsize("0.3 0.6 0.9")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and roomSize values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.roomSize shouldBe (0.3 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.roomSize shouldBe (0.6 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.roomSize shouldBe (0.9 plusOrMinus EPSILON)
    }

    "rsize() alias works correctly" {
        // Given rsize alias
        val p = rsize("0.2 0.8")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then roomSize values are set
        events.size shouldBe 2
        events[0].data.roomSize shouldBe (0.2 plusOrMinus EPSILON)
        events[1].data.roomSize shouldBe (0.8 plusOrMinus EPSILON)
    }

    "rsize() modifier alias works correctly" {
        // Given sound pattern with rsize modifier
        val p = sound("bd hh").rsize("0.4 0.7")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then roomSize is applied
        events.size shouldBe 2
        events[0].data.roomSize shouldBe (0.4 plusOrMinus EPSILON)
        events[1].data.roomSize shouldBe (0.7 plusOrMinus EPSILON)
    }

    "roomsize() works within compiled code" {
        val p = StrudelPattern.compile("""roomsize("0.0 0.25 0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.roomSize shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.roomSize shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.roomSize shouldBe (0.75 plusOrMinus EPSILON)
    }

    "roomsize() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").roomsize("0.3 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.roomSize shouldBe (0.3 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.roomSize shouldBe (0.8 plusOrMinus EPSILON)
    }

    "rsize() alias works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd").rsize("0.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
    }
})
