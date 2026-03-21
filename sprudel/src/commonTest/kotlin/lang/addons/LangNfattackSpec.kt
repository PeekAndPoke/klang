package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seq

class LangNfattackSpec : StringSpec({

    // ---- nfattack ----

    "nfattack dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfattack(ctrl)" to seq(pat).nfattack(ctrl),
            "script pattern.nfattack(ctrl)" to StrudelPattern.compile("""seq("$pat").nfattack("$ctrl")"""),
            "string.nfattack(ctrl)" to pat.nfattack(ctrl),
            "script string.nfattack(ctrl)" to StrudelPattern.compile(""""$pat".nfattack("$ctrl")"""),
            "nfattack(ctrl)" to seq(pat).apply(nfattack(ctrl)),
            "script nfattack(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfattack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "reinterpret voice data as nfattack | seq(\"0.5 1.0\").nfattack()" {
        val p = seq("0.5 1.0").nfattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "reinterpret voice data as nfattack | \"0.5 1.0\".nfattack()" {
        val p = "0.5 1.0".nfattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "reinterpret voice data as nfattack | seq(\"0.5 1.0\").apply(nfattack())" {
        val p = seq("0.5 1.0").apply(nfattack())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "nfattack() sets VoiceData.nfattack" {
        val p = note("a b").apply(nfattack("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nfattack() sets VoiceData.nfattack on existing pattern" {
        val base = note("c3 e3")
        val p = base.nfattack("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfattack } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nfattack() works as string extension" {
        val p = "c3".nfattack("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfattack shouldBe 0.5
    }

    "nfattack() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").nfattack("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    // ---- nfa (alias) ----

    "nfa dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nfa(ctrl)" to seq(pat).nfa(ctrl),
            "script pattern.nfa(ctrl)" to StrudelPattern.compile("""seq("$pat").nfa("$ctrl")"""),
            "string.nfa(ctrl)" to pat.nfa(ctrl),
            "script string.nfa(ctrl)" to StrudelPattern.compile(""""$pat".nfa("$ctrl")"""),
            "nfa(ctrl)" to seq(pat).apply(nfa(ctrl)),
            "script nfa(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(nfa("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "reinterpret voice data as nfattack | seq(\"0.5 1.0\").nfa()" {
        val p = seq("0.5 1.0").nfa()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

    "nfa() alias works as pattern extension" {
        val p = note("c d").nfa("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.4, 0.6)
    }

    "nfa() alias works as string extension" {
        val p = "e3".nfa("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nfattack shouldBe 0.8
    }

    "nfa() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").nfa("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.2, 0.9)
    }
})
