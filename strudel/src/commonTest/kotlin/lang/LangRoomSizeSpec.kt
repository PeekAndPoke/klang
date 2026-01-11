package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRoomSizeSpec : StringSpec({

    "roomsize() sets VoiceData.roomSize" {
        val p = roomsize("2.0 4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(2.0, 4.0)
    }

    "rsize() alias works" {
        val p = rsize("2.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works as pattern extension" {
        val p = note("c").roomsize("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works as string extension" {
        val p = "c".roomsize("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").roomsize("2.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }
})
