package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPenvSpec : StringSpec({

    "top-level penv() sets VoiceData.pEnv correctly" {
        val p = penv("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(0.5, 1.0)
    }

    "control pattern penv() sets VoiceData.pEnv on existing pattern" {
        val base = note("c3 e3")
        val p = base.penv("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.pEnv } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "penv() works as string extension" {
        val p = "c3".penv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pEnv shouldBe 0.5
    }

    "penv() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").penv("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(0.5, 1.0)
    }

    "pamt() alias works as top-level function" {
        val p = pamt("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(0.3, 0.7)
    }

    "pamt() alias works as pattern extension" {
        val p = note("c d").pamt("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(0.4, 0.6)
    }

    "pamt() alias works as string extension" {
        val p = "e3".pamt("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.pEnv shouldBe 0.8
    }

    "pamt() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").pamt("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(0.2, 0.9)
    }
})
