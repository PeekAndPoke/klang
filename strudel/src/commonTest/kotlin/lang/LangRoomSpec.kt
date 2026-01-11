package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRoomSpec : StringSpec({

    "room() sets VoiceData.room" {
        val p = room("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.5, 0.8)
    }

    "room() works as pattern extension" {
        val p = note("c").room("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "room() works as string extension" {
        val p = "c".room("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "room() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").room("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }
})
