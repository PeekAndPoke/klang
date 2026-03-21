package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangHpfSpec : StringSpec({

    // ---- hpf ----

    "hpf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.hpf(ctrl)" to seq(pat).hpf(ctrl),
            "script pattern.hpf(ctrl)" to StrudelPattern.compile("""seq("$pat").hpf("$ctrl")"""),
            "string.hpf(ctrl)" to pat.hpf(ctrl),
            "script string.hpf(ctrl)" to StrudelPattern.compile(""""$pat".hpf("$ctrl")"""),
            "hpf(ctrl)" to seq(pat).apply(hpf(ctrl)),
            "script hpf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").hpf()" {
        val p = seq("1000 500").hpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | \"1000 500\".hpf()" {
        val p = "1000 500".hpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").apply(hpf())" {
        val p = seq("1000 500").apply(hpf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "hpf() sets VoiceData.hcutoff and adds FilterDef.HighPass" {
        val p = note("a b").apply(hpf("1000 500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 1000.0
        events[1].data.hcutoff shouldBe 500.0
    }

    "hpf() works as pattern extension" {
        val p = note("c").hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works as string extension" {
        val p = "c".hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() with continuous pattern sets cutoffHz correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hcutoff shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hcutoff shouldBe (0.0 plusOrMinus EPSILON)
    }

    // ---- hp (alias) ----

    "hp dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.hp(ctrl)" to seq(pat).hp(ctrl),
            "script pattern.hp(ctrl)" to StrudelPattern.compile("""seq("$pat").hp("$ctrl")"""),
            "string.hp(ctrl)" to pat.hp(ctrl),
            "script string.hp(ctrl)" to StrudelPattern.compile(""""$pat".hp("$ctrl")"""),
            "hp(ctrl)" to seq(pat).apply(hp(ctrl)),
            "script hp(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hp("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").hp()" {
        val p = seq("1000 500").hp()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "hp() sets VoiceData.hcutoff" {
        val p = note("a b").apply(hp("1000 500"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 1000.0
        events[1].data.hcutoff shouldBe 500.0
    }

    "hp() works as pattern extension" {
        val p = note("c").hp("800")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
    }

    "hp() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hp("800")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
    }

    // ---- hcutoff (alias) ----

    "hcutoff dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.hcutoff(ctrl)" to seq(pat).hcutoff(ctrl),
            "script pattern.hcutoff(ctrl)" to StrudelPattern.compile("""seq("$pat").hcutoff("$ctrl")"""),
            "string.hcutoff(ctrl)" to pat.hcutoff(ctrl),
            "script string.hcutoff(ctrl)" to StrudelPattern.compile(""""$pat".hcutoff("$ctrl")"""),
            "hcutoff(ctrl)" to seq(pat).apply(hcutoff(ctrl)),
            "script hcutoff(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hcutoff("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").hcutoff()" {
        val p = seq("1000 500").hcutoff()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "hcutoff() sets VoiceData.hcutoff" {
        val p = note("a b").apply(hcutoff("1000 500"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 1000.0
        events[1].data.hcutoff shouldBe 500.0
    }

    "hcutoff() works as pattern extension" {
        val p = note("c").hcutoff("1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1200.0
    }
})
