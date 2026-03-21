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

class LangNresonanceSpec : StringSpec({

    // ---- nresonance ----

    "nresonance dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.nresonance(ctrl)" to seq(pat).nresonance(ctrl),
            "script pattern.nresonance(ctrl)" to SprudelPattern.compile("""seq("$pat").nresonance("$ctrl")"""),
            "string.nresonance(ctrl)" to pat.nresonance(ctrl),
            "script string.nresonance(ctrl)" to SprudelPattern.compile(""""$pat".nresonance("$ctrl")"""),
            "nresonance(ctrl)" to seq(pat).apply(nresonance(ctrl)),
            "script nresonance(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(nresonance("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as nresonance | seq(\"0.5 1.0\").nresonance()" {
        val p = seq("0.5 1.0").nresonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as nresonance | \"0.5 1.0\".nresonance()" {
        val p = "0.5 1.0".nresonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as nresonance | seq(\"0.5 1.0\").apply(nresonance())" {
        val p = seq("0.5 1.0").apply(nresonance())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "nresonance() sets VoiceData.nresonance" {
        val p = note("a b").apply(nresonance("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.5, 1.0)
    }

    "control pattern nresonance() sets VoiceData.nresonance on existing pattern" {
        val base = note("c3 e3")
        val p = base.nresonance("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nresonance } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "nresonance() works as string extension" {
        val p = "c3".nresonance("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nresonance shouldBe 0.5
    }

    "nresonance() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").nresonance("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.5, 1.0)
    }

    // ---- nres (alias) ----

    "notchq dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notchq(ctrl)" to seq(pat).notchq(ctrl),
            "script pattern.notchq(ctrl)" to SprudelPattern.compile("""seq("$pat").notchq("$ctrl")"""),
            "string.notchq(ctrl)" to pat.notchq(ctrl),
            "script string.notchq(ctrl)" to SprudelPattern.compile(""""$pat".notchq("$ctrl")"""),
            "notchq(ctrl)" to seq(pat).apply(notchq(ctrl)),
            "script notchq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notchq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as nresonance | seq(\"0.5 1.0\").notchq()" {
        val p = seq("0.5 1.0").notchq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

    "notchq() alias works as pattern extension" {
        val p = note("c d").notchq("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.4, 0.6)
    }

    "notchq() alias works as string extension" {
        val p = "e3".notchq("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.nresonance shouldBe 0.8
    }

    "notchq() alias works within compiled code" {
        val p = SprudelPattern.compile("""note("c d").notchq("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.2, 0.9)
    }
})
