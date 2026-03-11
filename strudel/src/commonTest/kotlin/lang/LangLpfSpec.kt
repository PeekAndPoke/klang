package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangLpfSpec : StringSpec({

    // ---- lpf ----

    "lpf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.lpf(ctrl)" to seq(pat).lpf(ctrl),
            "script pattern.lpf(ctrl)" to StrudelPattern.compile("""seq("$pat").lpf("$ctrl")"""),
            "string.lpf(ctrl)" to pat.lpf(ctrl),
            "script string.lpf(ctrl)" to StrudelPattern.compile(""""$pat".lpf("$ctrl")"""),
            "lpf(ctrl)" to seq(pat).apply(lpf(ctrl)),
            "script lpf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lpf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").lpf()" {
        val p = seq("1000 500").lpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | \"1000 500\".lpf()" {
        val p = "1000 500".lpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").apply(lpf())" {
        val p = seq("1000 500").apply(lpf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "lpf() sets VoiceData.cutoff" {
        val p = note("a b").apply(lpf("1000 500"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.cutoff shouldBe 1000.0
        events[1].data.cutoff shouldBe 500.0
    }

    "lpf() works as pattern extension" {
        val p = note("c").lpf("1000")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works as string extension" {
        val p = "c".lpf("1000")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() with continuous pattern sets cutoff correctly" {
        val p = note("a b c d").lpf(sine)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
        events[0].data.cutoff shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.cutoff shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.cutoff shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.cutoff shouldBe (0.0 plusOrMinus EPSILON)
    }

    // ---- cutoff (alias) ----

    "cutoff dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.cutoff(ctrl)" to seq(pat).cutoff(ctrl),
            "script pattern.cutoff(ctrl)" to StrudelPattern.compile("""seq("$pat").cutoff("$ctrl")"""),
            "string.cutoff(ctrl)" to pat.cutoff(ctrl),
            "script string.cutoff(ctrl)" to StrudelPattern.compile(""""$pat".cutoff("$ctrl")"""),
            "cutoff(ctrl)" to seq(pat).apply(cutoff(ctrl)),
            "script cutoff(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(cutoff("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").cutoff()" {
        val p = seq("1000 500").cutoff()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    // ---- ctf (alias) ----

    "ctf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.ctf(ctrl)" to seq(pat).ctf(ctrl),
            "script pattern.ctf(ctrl)" to StrudelPattern.compile("""seq("$pat").ctf("$ctrl")"""),
            "string.ctf(ctrl)" to pat.ctf(ctrl),
            "script string.ctf(ctrl)" to StrudelPattern.compile(""""$pat".ctf("$ctrl")"""),
            "ctf(ctrl)" to seq(pat).apply(ctf(ctrl)),
            "script ctf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(ctf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").ctf()" {
        val p = seq("1000 500").ctf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    // ---- lp (alias) ----

    "lp dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.lp(ctrl)" to seq(pat).lp(ctrl),
            "script pattern.lp(ctrl)" to StrudelPattern.compile("""seq("$pat").lp("$ctrl")"""),
            "string.lp(ctrl)" to pat.lp(ctrl),
            "script string.lp(ctrl)" to StrudelPattern.compile(""""$pat".lp("$ctrl")"""),
            "lp(ctrl)" to seq(pat).apply(lp(ctrl)),
            "script lp(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lp("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").lp()" {
        val p = seq("1000 500").lp()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }
})
