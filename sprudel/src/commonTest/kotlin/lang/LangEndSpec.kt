package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangEndSpec : StringSpec({

    "end dsl interface" {
        val pat = "a b"
        val ctrl = "0.25 0.75"
        dslInterfaceTests(
            "pattern.end(ctrl)" to seq(pat).end(ctrl),
            "script pattern.end(ctrl)" to SprudelPattern.compile("""seq("$pat").end("$ctrl")"""),
            "string.end(ctrl)" to pat.end(ctrl),
            "script string.end(ctrl)" to SprudelPattern.compile(""""$pat".end("$ctrl")"""),
            "end(ctrl)" to seq(pat).apply(end(ctrl)),
            "script end(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(end("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.end shouldBe 0.25
            events[1].data.end shouldBe 0.75
        }
    }

    "reinterpret voice data as end | seq(\"0.25 0.75\").end()" {
        val p = seq("0.25 0.75").end()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.end shouldBe 0.25
            events[1].data.end shouldBe 0.75
        }
    }

    "reinterpret voice data as end | \"0.25 0.75\".end()" {
        val p = "0.25 0.75".end()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.end shouldBe 0.25
            events[1].data.end shouldBe 0.75
        }
    }

    "reinterpret voice data as end | seq(\"0.25 0.75\").apply(end())" {
        val p = seq("0.25 0.75").apply(end())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.end shouldBe 0.25
            events[1].data.end shouldBe 0.75
        }
    }

    "end() sets VoiceData.end correctly" {
        val p = sound("hh hh").apply(end("0.25 0.5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.end } shouldBe listOf(0.25, 0.5)
    }

    "control pattern end() sets VoiceData.end on existing pattern" {
        val base = note("c3 e3")
        val p = base.end("0.6 0.8")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.end } shouldBe listOf(0.6, 0.8, 0.6, 0.8)
    }

    "end() works as string extension" {
        val p = "c3".end("0.75")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.end shouldBe 0.75
    }

    "end() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").end("0.25 0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.end } shouldBe listOf(0.25, 0.75)
    }
})
