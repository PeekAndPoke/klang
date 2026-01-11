package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangReleaseSpec : StringSpec({

    "release() sets VoiceData.adsr.release" {
        val p = release("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.release } shouldBe listOf(0.1, 0.5)
    }

    "release() works as pattern extension" {
        val p = note("c").release("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.release shouldBe 0.1
    }

    "release() works as string extension" {
        val p = "c".release("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.adsr.release shouldBe 0.1
    }

    "release() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").release("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.adsr.release shouldBe 0.1
    }
})
