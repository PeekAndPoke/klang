package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPanchorSpec : StringSpec({

    "top-level panchor() sets VoiceData.pAnchor correctly" {
        val p = panchor("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.5, 1.0)
    }

    "control pattern panchor() sets VoiceData.pAnchor on existing pattern" {
        val base = note("c3 e3")
        val p = base.panchor("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.pAnchor } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "panchor() works as string extension" {
        val p = "c3".panchor("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pAnchor shouldBe 0.5
    }

    "panchor() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").panchor("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.5, 1.0)
    }

    "panc() alias works as top-level function" {
        val p = panc("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.3, 0.7)
    }

    "panc() alias works as pattern extension" {
        val p = note("c d").panc("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.4, 0.6)
    }

    "panc() alias works as string extension" {
        val p = "e3".panc("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.pAnchor shouldBe 0.8
    }

    "panc() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").panc("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.2, 0.9)
    }
})
