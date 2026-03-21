package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLpdecaySpec : StringSpec({

    // ---- lpdecay ----

    "lpdecay dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpdecay(ctrl)" to seq(pat).lpdecay(ctrl),
            "script pattern.lpdecay(ctrl)" to SprudelPattern.compile("""seq("$pat").lpdecay("$ctrl")"""),
            "string.lpdecay(ctrl)" to pat.lpdecay(ctrl),
            "script string.lpdecay(ctrl)" to SprudelPattern.compile(""""$pat".lpdecay("$ctrl")"""),
            "lpdecay(ctrl)" to seq(pat).apply(lpdecay(ctrl)),
            "script lpdecay(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpdecay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as lpdecay | seq(\"0.5 1.0\").lpdecay()" {
        val p = seq("0.5 1.0").lpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as lpdecay | \"0.5 1.0\".lpdecay()" {
        val p = "0.5 1.0".lpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as lpdecay | seq(\"0.5 1.0\").apply(lpdecay())" {
        val p = seq("0.5 1.0").apply(lpdecay())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "lpdecay() sets VoiceData.lpdecay" {
        val p = note("a b").apply(lpdecay("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpdecay } shouldBe listOf(0.5, 1.0)
    }

    "lpdecay() works as pattern extension" {
        val p = note("c").lpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() works as string extension" {
        val p = "c".lpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpdecay("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() with continuous pattern sets lpdecay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpdecay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpdecay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpdecay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpdecay() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.SprudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.decay shouldBe 0.2
    }

    // ---- lpd (alias) ----

    "lpd dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpd(ctrl)" to seq(pat).lpd(ctrl),
            "script pattern.lpd(ctrl)" to SprudelPattern.compile("""seq("$pat").lpd("$ctrl")"""),
            "string.lpd(ctrl)" to pat.lpd(ctrl),
            "script string.lpd(ctrl)" to SprudelPattern.compile(""""$pat".lpd("$ctrl")"""),
            "lpd(ctrl)" to seq(pat).apply(lpd(ctrl)),
            "script lpd(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as lpdecay | seq(\"0.5 1.0\").lpd()" {
        val p = seq("0.5 1.0").lpd()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpdecay shouldBe 0.5
            events[1].data.lpdecay shouldBe 1.0
        }
    }

    "lpd() sets VoiceData.lpdecay" {
        val p = note("a b").apply(lpd("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpdecay } shouldBe listOf(0.5, 1.0)
    }

    "lpd() works as pattern extension" {
        val p = note("c").lpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.25
    }

    "lpd() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpd("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.25
    }
})
