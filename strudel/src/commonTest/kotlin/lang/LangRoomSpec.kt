package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRoomSpec : StringSpec({

    "top-level room() sets VoiceData.room correctly" {
        // Given a room pattern with space-delimited values
        val p = room("0.0 0.5 1.0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then room values are set
        events.size shouldBe 3
        events[0].data.room shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.room shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.room shouldBe (1.0 plusOrMinus EPSILON)
    }

    "control pattern room() sets room on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying room as control pattern with space-delimited values
        val p = base.room("0.2 0.5 0.8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and room values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.room shouldBe (0.2 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.room shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "sn"
        events[2].data.room shouldBe (0.8 plusOrMinus EPSILON)
    }

    "room() with zero value (dry signal)" {
        // Given room with 0 (no reverb)
        val p = sound("bd").room("0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then room is 0 (dry)
        events.size shouldBe 1
        events[0].data.room shouldBe (0.0 plusOrMinus EPSILON)
    }

    "room() with value 1 (fully wet)" {
        // Given room with 1 (full reverb)
        val p = sound("bd").room("1")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then room is 1 (fully wet)
        events.size shouldBe 1
        events[0].data.room shouldBe (1.0 plusOrMinus EPSILON)
    }

    "room() works within compiled code" {
        val p = StrudelPattern.compile("""room("0.0 0.25 0.5 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.room shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.room shouldBe (0.25 plusOrMinus EPSILON)
        events[2].data.room shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.room shouldBe (0.75 plusOrMinus EPSILON)
    }

    "room() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").room("0.3 0.7")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.room shouldBe (0.3 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].data.room shouldBe (0.7 plusOrMinus EPSILON)
    }
})
