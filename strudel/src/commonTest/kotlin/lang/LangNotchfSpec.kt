package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangNotchfSpec : StringSpec({

    // ---- notchf ----

    "notchf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.notchf(ctrl)" to seq(pat).notchf(ctrl),
            "script pattern.notchf(ctrl)" to StrudelPattern.compile("""seq("$pat").notchf("$ctrl")"""),
            "string.notchf(ctrl)" to pat.notchf(ctrl),
            "script string.notchf(ctrl)" to StrudelPattern.compile(""""$pat".notchf("$ctrl")"""),
            "notchf(ctrl)" to seq(pat).apply(notchf(ctrl)),
            "script notchf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(notchf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | seq(\"1000 500\").notchf()" {
        val p = seq("1000 500").notchf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | \"1000 500\".notchf()" {
        val p = "1000 500".notchf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | seq(\"1000 500\").apply(notchf())" {
        val p = seq("1000 500").apply(notchf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "notchf() sets VoiceData.notchf" {
        val p = note("a b").apply(notchf("1000 500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.notchf shouldBe 1000.0
        events[1].data.notchf shouldBe 500.0
    }

    "notchf() works as pattern extension" {
        val p = note("c").notchf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notchf() works as string extension" {
        val p = "c".notchf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notchf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").notchf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notchf() with continuous pattern sets notchf correctly" {
        val p = note("a b c d").notchf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.notchf shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.notchf shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.notchf shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.notchf shouldBe (0.0 plusOrMinus EPSILON)
    }
})