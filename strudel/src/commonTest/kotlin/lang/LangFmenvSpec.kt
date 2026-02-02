package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFmenvSpec : StringSpec({

    "top-level fmenv() sets VoiceData.fmEnv correctly" {
        val p = fmenv("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmEnv } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmenv() sets VoiceData.fmEnv on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmenv("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmEnv } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmenv() works as string extension" {
        val p = "c3".fmenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmEnv shouldBe 0.5
    }

    "fmenv() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmenv("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmEnv } shouldBe listOf(0.5, 1.0)
    }

    "fmmod() alias works as top-level function" {
        val p = fmmod("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmEnv } shouldBe listOf(0.3, 0.7)
    }

    "fmmod() alias works as pattern extension" {
        val p = note("c d").fmmod("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmEnv } shouldBe listOf(0.4, 0.6)
    }

    "fmmod() alias works as string extension" {
        val p = "e3".fmmod("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmEnv shouldBe 0.8
    }

    "fmmod() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmmod("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmEnv } shouldBe listOf(0.2, 0.9)
    }
})
