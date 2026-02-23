package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangCompressorSpec : StringSpec({

    "compressor dsl interface" {
        val pat = "0 1"
        val ctrl = "0:0:0 1:1:1"

        dslInterfaceTests(
            "pattern.compressor(ctrl)" to
                    seq(pat).compressor(ctrl),
            "script pattern.compressor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").compressor("$ctrl")"""),
            "string.compressor(ctrl)" to
                    pat.compressor(ctrl),
            "script string.compressor(ctrl)" to
                    StrudelPattern.compile(""""$pat".compressor("$ctrl")"""),
            "compressor(ctrl)" to
                    seq(pat).apply(compressor(ctrl)),
            "script compressor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(compressor("$ctrl"))"""),
            // comp alias
            "pattern.comp(ctrl)" to
                    seq(pat).comp(ctrl),
            "script pattern.comp(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").comp("$ctrl")"""),
            "string.comp(ctrl)" to
                    pat.comp(ctrl),
            "script string.comp(ctrl)" to
                    StrudelPattern.compile(""""$pat".comp("$ctrl")"""),
            "comp(ctrl)" to
                    seq(pat).apply(comp(ctrl)),
            "script comp(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(comp("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.compressor shouldBe "0:0:0"
            events[1].data.compressor shouldBe "1:1:1"
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").velocity()" {
        val p = seq("0 1").velocity()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | \"0 1\".velocity()" {
        val p = "0 1".velocity()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").apply(velocity())" {
        val p = seq("0 1").apply(velocity())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "top-level compressor() sets VoiceData.compressor correctly" {
        // Given a simple sequence of compressor values within one cycle
        val p = "0 1".apply(compressor("0.5:2 0.8:4"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the compressor values in order
        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "control pattern compressor() sets VoiceData.compressor on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the compressor per step
        val p = base.compressor("0.5:2:0.1 0.8:4:0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the compressor values in order
        events.map { it.data.compressor } shouldBe listOf("0.5:2:0.1", "0.8:4:0.2", "0.5:2:0.1", "0.8:4:0.2")
    }

    "compressor() works as string extension" {
        val p = "c3".compressor("0.6:3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.compressor shouldBe "0.6:3"
    }

    "compressor() works within compiled code as top-level function" {
        val p = StrudelPattern.compile(""""0 1".apply(compressor("0.5:2 0.8:4"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "compressor() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").compressor("0.5:2 0.8:4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.5:2", "0.8:4")
    }

    "compressor() with full parameters (threshold:ratio:knee:attack:release)" {
        val p = note("c").compressor("0.5:2:0.1:0.01:0.1")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "0.5:2:0.1:0.01:0.1"
    }

    "comp() alias works as top-level function" {
        val p = "0 1".apply(comp("0.7:3 0.9:5"))

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.7:3", "0.9:5")
    }

    "comp() alias works as pattern extension" {
        val p = note("c d").comp("0.4:2 0.6:3")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.4:2", "0.6:3")
    }

    "comp() alias works as string extension" {
        val p = "e3".comp("0.8:4")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.compressor shouldBe "0.8:4"
    }

    "comp() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").comp("0.2:1.5 0.9:6")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.compressor } shouldBe listOf("0.2:1.5", "0.9:6")
    }

    "compressor() can accept numeric values" {
        // Compressor accepts any value and converts to string
        val p = note("c").compressor(2.5)

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "2.5"
    }
})
