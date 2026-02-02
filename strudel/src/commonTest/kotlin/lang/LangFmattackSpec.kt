package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFmattackSpec : StringSpec({

    "top-level fmattack() sets VoiceData.fmAttack correctly" {
        val p = fmattack("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmattack() sets VoiceData.fmAttack on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmattack("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmAttack } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmattack() works as string extension" {
        val p = "c3".fmattack("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmAttack shouldBe 0.5
    }

    "fmattack() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmattack("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.5, 1.0)
    }

    "fmatt() alias works as top-level function" {
        val p = fmatt("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.3, 0.7)
    }

    "fmatt() alias works as pattern extension" {
        val p = note("c d").fmatt("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.4, 0.6)
    }

    "fmatt() alias works as string extension" {
        val p = "e3".fmatt("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmAttack shouldBe 0.8
    }

    "fmatt() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmatt("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.2, 0.9)
    }
})
