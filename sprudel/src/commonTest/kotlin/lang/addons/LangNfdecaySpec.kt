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

class LangNfdecaySpec : StringSpec({

    // ---- nfdecay ----

    "nfdecay dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfdecay(ctrl)" to seq(pat).nfdecay(ctrl),
            "script pattern.nfdecay(ctrl)" to SprudelPattern.compile("""seq("$pat").nfdecay("$ctrl")"""),
            "string.nfdecay(ctrl)" to pat.nfdecay(ctrl),
            "script string.nfdecay(ctrl)" to SprudelPattern.compile(""""$pat".nfdecay("$ctrl")"""),
            "nfdecay(ctrl)" to seq(pat).apply(nfdecay(ctrl)),
            "script nfdecay(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(nfdecay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as nfdecay | seq(\"0.5 1.0\").nfdecay()" {
        val p = seq("0.5 1.0").nfdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as nfdecay | \"0.5 1.0\".nfdecay()" {
        val p = "0.5 1.0".nfdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as nfdecay | seq(\"0.5 1.0\").apply(nfdecay())" {
        val p = seq("0.5 1.0").apply(nfdecay())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "nfdecay() sets VoiceData.nfdecay" {
        val p = note("a b").apply(nfdecay("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfdecay() sets VoiceData.nfdecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfdecay("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfdecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfdecay() works as string extension" {
        val p = "c3".nfdecay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfdecay shouldBe 0.5
    }

    "nfdecay() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").nfdecay("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    // ---- nfd (alias) ----

    "nfd dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfd(ctrl)" to seq(pat).nfd(ctrl),
            "script pattern.nfd(ctrl)" to SprudelPattern.compile("""seq("$pat").nfd("$ctrl")"""),
            "string.nfd(ctrl)" to pat.nfd(ctrl),
            "script string.nfd(ctrl)" to SprudelPattern.compile(""""$pat".nfd("$ctrl")"""),
            "nfd(ctrl)" to seq(pat).apply(nfd(ctrl)),
            "script nfd(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(nfd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as nfdecay | seq(\"0.5 1.0\").nfd()" {
        val p = seq("0.5 1.0").nfd()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

    "nfd() alias works as pattern extension" {
        val p = note("c d").nfd("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.4, 0.6)
    }

    "nfd() alias works as string extension" {
        val p = "e3".nfd("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfdecay shouldBe 0.8
    }

    "nfd() alias works within compiled code" {
        val p = SprudelPattern.compile("""note("c d").nfd("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.2, 0.9)
    }
})
