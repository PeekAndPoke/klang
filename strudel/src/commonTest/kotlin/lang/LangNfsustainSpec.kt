package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfsustainSpec : StringSpec({

    "top-level nfsustain() sets VoiceData.nfsustain correctly" {
        val p = nfsustain("0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfsustain() sets VoiceData.nfsustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfsustain("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfsustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfsustain() works as string extension" {
        val p = "c3".nfsustain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfsustain shouldBe 0.5
    }

    "nfsustain() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfsustain("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.5, 1.0)
    }

    "nfs() alias works as top-level function" {
        val p = nfs("0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.3, 0.7)
    }

    "nfs() alias works as pattern extension" {
        val p = note("c d").nfs("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.4, 0.6)
    }

    "nfs() alias works as string extension" {
        val p = "e3".nfs("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfsustain shouldBe 0.8
    }

    "nfs() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfs("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.2, 0.9)
    }
})
