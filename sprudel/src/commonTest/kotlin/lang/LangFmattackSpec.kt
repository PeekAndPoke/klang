package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangFmattackSpec : StringSpec({

    "fmattack dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fmattack(ctrl)" to s(pat).fmattack(ctrl),
            "script pattern.fmattack(ctrl)" to SprudelPattern.compile("""s("$pat").fmattack("$ctrl")"""),
            "string.fmattack(ctrl)" to pat.fmattack(ctrl),
            "script string.fmattack(ctrl)" to SprudelPattern.compile(""""$pat".fmattack("$ctrl")"""),
            "fmattack(ctrl)" to s(pat).apply(fmattack(ctrl)),
            "script fmattack(ctrl)" to SprudelPattern.compile("""s("$pat").apply(fmattack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "fmatt dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fmatt(ctrl)" to s(pat).fmatt(ctrl),
            "script pattern.fmatt(ctrl)" to SprudelPattern.compile("""s("$pat").fmatt("$ctrl")"""),
            "string.fmatt(ctrl)" to pat.fmatt(ctrl),
            "script string.fmatt(ctrl)" to SprudelPattern.compile(""""$pat".fmatt("$ctrl")"""),
            "fmatt(ctrl)" to s(pat).apply(fmatt(ctrl)),
            "script fmatt(ctrl)" to SprudelPattern.compile("""s("$pat").apply(fmatt("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "reinterpret voice data as fmAttack | seq(\"0.1 0.5\").fmattack()" {
        val p = seq("0.1 0.5").fmattack()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "reinterpret voice data as fmAttack | \"0.1 0.5\".fmattack()" {
        val p = "0.1 0.5".fmattack()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "reinterpret voice data as fmAttack | seq(\"0.1 0.5\").apply(fmattack())" {
        val p = seq("0.1 0.5").apply(fmattack())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "top-level fmattack() sets VoiceData.fmAttack correctly" {
        val p = s("hh hh").apply(fmattack("0.5 1.0"))
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
        val p = SprudelPattern.compile("""note("a b").fmattack("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.5, 1.0)
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
        val p = SprudelPattern.compile("""note("c d").fmatt("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.2, 0.9)
    }
})
