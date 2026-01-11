package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDelayTimeSpec : StringSpec({

    "delaytime() sets VoiceData.delayTime" {
        val p = delaytime("0.25 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.25, 0.5)
    }

    "delaytime() works as pattern extension" {
        val p = note("c").delaytime("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayTime shouldBe 0.25
    }

    "delaytime() works as string extension" {
        val p = "c".delaytime("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayTime shouldBe 0.25
    }

    "delaytime() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").delaytime("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delayTime shouldBe 0.25
    }
})
