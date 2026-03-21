package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangCutSpec : StringSpec({

    "cut dsl interface" {
        val pat = "a b"
        val ctrl = "1 2"
        dslInterfaceTests(
            "pattern.cut(ctrl)" to seq(pat).cut(ctrl),
            "script pattern.cut(ctrl)" to SprudelPattern.compile("""seq("$pat").cut("$ctrl")"""),
            "string.cut(ctrl)" to pat.cut(ctrl),
            "script string.cut(ctrl)" to SprudelPattern.compile(""""$pat".cut("$ctrl")"""),
            "cut(ctrl)" to seq(pat).apply(cut(ctrl)),
            "script cut(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(cut("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cut shouldBe 1
            events[1].data.cut shouldBe 2
        }
    }

    "reinterpret voice data as cut | seq(\"1 2\").cut()" {
        val p = seq("1 2").cut()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cut shouldBe 1
            events[1].data.cut shouldBe 2
        }
    }

    "reinterpret voice data as cut | \"1 2\".cut()" {
        val p = "1 2".cut()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cut shouldBe 1
            events[1].data.cut shouldBe 2
        }
    }

    "reinterpret voice data as cut | seq(\"1 2\").apply(cut())" {
        val p = seq("1 2").apply(cut())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cut shouldBe 1
            events[1].data.cut shouldBe 2
        }
    }

    "cut() sets VoiceData.cut correctly" {
        val p = sound("hh hh").apply(cut("1 2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.cut } shouldBe listOf(1, 2)
    }

    "cut() works as string extension" {
        val p = "c3".cut("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.cut shouldBe 1
    }

    "cut() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").cut("1 2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.cut } shouldBe listOf(1, 2)
    }
})
