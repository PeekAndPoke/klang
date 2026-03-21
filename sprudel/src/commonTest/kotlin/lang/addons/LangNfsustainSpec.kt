package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seq

class LangNfsustainSpec : StringSpec({

    // ---- nfsustain ----

    "nfsustain dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfsustain(ctrl)" to seq(pat).nfsustain(ctrl),
            "script pattern.nfsustain(ctrl)" to SprudelPattern.compile("""seq("$pat").nfsustain("$ctrl")"""),
            "string.nfsustain(ctrl)" to pat.nfsustain(ctrl),
            "script string.nfsustain(ctrl)" to SprudelPattern.compile(""""$pat".nfsustain("$ctrl")"""),
            "nfsustain(ctrl)" to seq(pat).apply(nfsustain(ctrl)),
            "script nfsustain(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(nfsustain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as nfsustain | seq(\"0.5 1.0\").nfsustain()" {
        val p = seq("0.5 1.0").nfsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as nfsustain | \"0.5 1.0\".nfsustain()" {
        val p = "0.5 1.0".nfsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as nfsustain | seq(\"0.5 1.0\").apply(nfsustain())" {
        val p = seq("0.5 1.0").apply(nfsustain())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

    "nfsustain() sets VoiceData.nfsustain" {
        val p = note("a b").apply(nfsustain("0.5 1.0"))
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
        val p = SprudelPattern.compile("""note("a b").nfsustain("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.5, 1.0)
    }

    // ---- nfs (alias) ----

    "nfs dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfs(ctrl)" to seq(pat).nfs(ctrl),
            "script pattern.nfs(ctrl)" to SprudelPattern.compile("""seq("$pat").nfs("$ctrl")"""),
            "string.nfs(ctrl)" to pat.nfs(ctrl),
            "script string.nfs(ctrl)" to SprudelPattern.compile(""""$pat".nfs("$ctrl")"""),
            "nfs(ctrl)" to seq(pat).apply(nfs(ctrl)),
            "script nfs(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(nfs("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as nfsustain | seq(\"0.5 1.0\").nfs()" {
        val p = seq("0.5 1.0").nfs()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
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
        val p = SprudelPattern.compile("""note("c d").nfs("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.2, 0.9)
    }
})
