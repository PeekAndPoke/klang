package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangReleaseSpec : StringSpec({

    "top-level release() sets VoiceData.adsr.release correctly" {
        val p = release("0.2 0.8")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.adsr.release } shouldBe listOf(0.2, 0.8)
    }

    "control pattern release() sets VoiceData.adsr.release on existing pattern" {
        val base = note("c3 e3")
        val p = base.release("0.3 0.6")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.adsr.release } shouldBe listOf(0.3, 0.6, 0.3, 0.6)
    }

    "release() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""release("0.2 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.release } shouldBe listOf(0.2, 0.8)
    }

    "release() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").release("0.2 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.adsr.release } shouldBe listOf(0.2, 0.8)
    }
})
