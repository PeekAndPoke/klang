package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangHresonanceSpec : StringSpec({

    // ---- hresonance ----

    "hresonance dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hresonance(ctrl)" to seq(pat).hresonance(ctrl),
            "script pattern.hresonance(ctrl)" to SprudelPattern.compile("""seq("$pat").hresonance("$ctrl")"""),
            "string.hresonance(ctrl)" to pat.hresonance(ctrl),
            "script string.hresonance(ctrl)" to SprudelPattern.compile(""""$pat".hresonance("$ctrl")"""),
            "hresonance(ctrl)" to seq(pat).apply(hresonance(ctrl)),
            "script hresonance(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hresonance("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").hresonance()" {
        val p = seq("0.5 1.0").hresonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | \"0.5 1.0\".hresonance()" {
        val p = "0.5 1.0".hresonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").apply(hresonance())" {
        val p = seq("0.5 1.0").apply(hresonance())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "hresonance() sets VoiceData.hresonance correctly" {
        val p = note("a b").apply(hresonance("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.5, 1.0)
    }

    "control pattern hresonance() sets VoiceData.hresonance on existing pattern" {
        val base = note("c3 e3")
        val p = base.hresonance("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.hresonance } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "hresonance() works as string extension" {
        val p = "c3".hresonance("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.hresonance shouldBe 0.5
    }

    "hresonance() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").hresonance("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.5, 1.0)
    }

    // ---- hres (alias) ----

    "hres dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hres(ctrl)" to seq(pat).hres(ctrl),
            "script pattern.hres(ctrl)" to SprudelPattern.compile("""seq("$pat").hres("$ctrl")"""),
            "string.hres(ctrl)" to pat.hres(ctrl),
            "script string.hres(ctrl)" to SprudelPattern.compile(""""$pat".hres("$ctrl")"""),
            "hres(ctrl)" to seq(pat).apply(hres(ctrl)),
            "script hres(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hres("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").hres()" {
        val p = seq("0.5 1.0").hres()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "hres() sets VoiceData.hresonance correctly" {
        val p = note("a b").apply(hres("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.5, 1.0)
    }

    "hres() alias works as top-level function" {
        val p = note("a b").apply(hres("0.3 0.7"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.3, 0.7)
    }

    "hres() alias works as pattern extension" {
        val p = note("c d").hres("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.4, 0.6)
    }

    "hres() alias works as string extension" {
        val p = "e3".hres("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.hresonance shouldBe 0.8
    }

    "hres() alias works within compiled code" {
        val p = SprudelPattern.compile("""note("c d").hres("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.2, 0.9)
    }

    // ---- hpq (alias) ----

    "hpq dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpq(ctrl)" to seq(pat).hpq(ctrl),
            "script pattern.hpq(ctrl)" to SprudelPattern.compile("""seq("$pat").hpq("$ctrl")"""),
            "string.hpq(ctrl)" to pat.hpq(ctrl),
            "script string.hpq(ctrl)" to SprudelPattern.compile(""""$pat".hpq("$ctrl")"""),
            "hpq(ctrl)" to seq(pat).apply(hpq(ctrl)),
            "script hpq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").hpq()" {
        val p = seq("0.5 1.0").hpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "hpq() sets VoiceData.hresonance correctly" {
        val p = note("a b").apply(hpq("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(0.5, 1.0)
    }
})
