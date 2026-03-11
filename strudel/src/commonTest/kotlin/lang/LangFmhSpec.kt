package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangFmhSpec : StringSpec({

    "fmh dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.fmh(ctrl)" to s(pat).fmh(ctrl),
            "script pattern.fmh(ctrl)" to StrudelPattern.compile("""s("$pat").fmh("$ctrl")"""),
            "string.fmh(ctrl)" to pat.fmh(ctrl),
            "script string.fmh(ctrl)" to StrudelPattern.compile(""""$pat".fmh("$ctrl")"""),
            "fmh(ctrl)" to s(pat).apply(fmh(ctrl)),
            "script fmh(ctrl)" to StrudelPattern.compile("""s("$pat").apply(fmh("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmh shouldBe 0.5
            events[1].data.fmh shouldBe 1.0
        }
    }

    "reinterpret voice data as fmh | seq(\"0.5 1.0\").fmh()" {
        val p = seq("0.5 1.0").fmh()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmh shouldBe 0.5
            events[1].data.fmh shouldBe 1.0
        }
    }

    "reinterpret voice data as fmh | \"0.5 1.0\".fmh()" {
        val p = "0.5 1.0".fmh()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmh shouldBe 0.5
            events[1].data.fmh shouldBe 1.0
        }
    }

    "reinterpret voice data as fmh | seq(\"0.5 1.0\").apply(fmh())" {
        val p = seq("0.5 1.0").apply(fmh())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmh shouldBe 0.5
            events[1].data.fmh shouldBe 1.0
        }
    }

    "top-level fmh() sets VoiceData.fmh correctly" {
        val p = s("hh hh").apply(fmh("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmh() sets VoiceData.fmh on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmh("0.5 1.0")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0, 0.5, 1.0)
    }

    "fmh() works as string extension" {
        val p = "c3".fmh("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmh shouldBe 2.0
    }

    "fmh() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmh("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0)
    }
})
