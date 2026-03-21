package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangResonanceSpec : StringSpec({

    // ---- resonance ----

    "resonance dsl interface" {
        val pat = "a b"
        val ctrl = "5 10"

        dslInterfaceTests(
            "pattern.resonance(ctrl)" to seq(pat).resonance(ctrl),
            "script pattern.resonance(ctrl)" to StrudelPattern.compile("""seq("$pat").resonance("$ctrl")"""),
            "string.resonance(ctrl)" to pat.resonance(ctrl),
            "script string.resonance(ctrl)" to StrudelPattern.compile(""""$pat".resonance("$ctrl")"""),
            "resonance(ctrl)" to seq(pat).apply(resonance(ctrl)),
            "script resonance(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(resonance("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | seq(\"5 10\").resonance()" {
        val p = seq("5 10").resonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | \"5 10\".resonance()" {
        val p = "5 10".resonance()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | seq(\"5 10\").apply(resonance())" {
        val p = seq("5 10").apply(resonance())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "resonance() sets VoiceData.resonance" {
        val p = note("a b").apply(resonance("5 10"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.resonance shouldBe 5.0
        events[1].data.resonance shouldBe 10.0
    }

    "res() alias works" {
        val p = note("a").apply(res("5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() updates existing filters" {
        // Apply LPF first (default Q=1.0), then update resonance to 5.0
        val p = note("c").lpf("1000").resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() works as string extension" {
        val p = "c".resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").resonance("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() with continuous pattern sets resonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").resonance(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.resonance shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.resonance shouldBe (0.0 plusOrMinus EPSILON)
    }

    // ---- res (alias) ----

    "res dsl interface" {
        val pat = "a b"
        val ctrl = "5 10"

        dslInterfaceTests(
            "pattern.res(ctrl)" to seq(pat).res(ctrl),
            "script pattern.res(ctrl)" to StrudelPattern.compile("""seq("$pat").res("$ctrl")"""),
            "string.res(ctrl)" to pat.res(ctrl),
            "script string.res(ctrl)" to StrudelPattern.compile(""""$pat".res("$ctrl")"""),
            "res(ctrl)" to seq(pat).apply(res(ctrl)),
            "script res(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(res("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | seq(\"5 10\").res()" {
        val p = seq("5 10").res()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    // ---- lpq (alias) ----

    "lpq dsl interface" {
        val pat = "a b"
        val ctrl = "5 10"

        dslInterfaceTests(
            "pattern.lpq(ctrl)" to seq(pat).lpq(ctrl),
            "script pattern.lpq(ctrl)" to StrudelPattern.compile("""seq("$pat").lpq("$ctrl")"""),
            "string.lpq(ctrl)" to pat.lpq(ctrl),
            "script string.lpq(ctrl)" to StrudelPattern.compile(""""$pat".lpq("$ctrl")"""),
            "lpq(ctrl)" to seq(pat).apply(lpq(ctrl)),
            "script lpq(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lpq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | seq(\"5 10\").lpq()" {
        val p = seq("5 10").lpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "lpq() is an alias for resonance()" {
        val p = note("c").apply(lpq("8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpq() works as pattern extension" {
        val p = note("c").lpq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpq() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpq("8")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }
})
