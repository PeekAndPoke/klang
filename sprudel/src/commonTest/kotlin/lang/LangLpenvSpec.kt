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

class LangLpenvSpec : StringSpec({

    // ---- lpenv ----

    "lpenv dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpenv(ctrl)" to seq(pat).lpenv(ctrl),
            "script pattern.lpenv(ctrl)" to SprudelPattern.compile("""seq("$pat").lpenv("$ctrl")"""),
            "string.lpenv(ctrl)" to pat.lpenv(ctrl),
            "script string.lpenv(ctrl)" to SprudelPattern.compile(""""$pat".lpenv("$ctrl")"""),
            "lpenv(ctrl)" to seq(pat).apply(lpenv(ctrl)),
            "script lpenv(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpenv("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | seq(\"0.5 1.0\").lpenv()" {
        val p = seq("0.5 1.0").lpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | \"0.5 1.0\".lpenv()" {
        val p = "0.5 1.0".lpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | seq(\"0.5 1.0\").apply(lpenv())" {
        val p = seq("0.5 1.0").apply(lpenv())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "lpenv() sets VoiceData.lpenv" {
        val p = note("a b").apply(lpenv("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpenv } shouldBe listOf(0.5, 1.0)
    }

    "lpenv() works as pattern extension" {
        val p = note("c").lpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() works as string extension" {
        val p = "c".lpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpenv("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() with continuous pattern sets lpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpenv shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.SprudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpenv = 0.7
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.depth shouldBe 0.7
    }

    // ---- lpe (alias) ----

    "lpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpe(ctrl)" to seq(pat).lpe(ctrl),
            "script pattern.lpe(ctrl)" to SprudelPattern.compile("""seq("$pat").lpe("$ctrl")"""),
            "string.lpe(ctrl)" to pat.lpe(ctrl),
            "script string.lpe(ctrl)" to SprudelPattern.compile(""""$pat".lpe("$ctrl")"""),
            "lpe(ctrl)" to seq(pat).apply(lpe(ctrl)),
            "script lpe(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | seq(\"0.5 1.0\").lpe()" {
        val p = seq("0.5 1.0").lpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "lpe() sets VoiceData.lpenv" {
        val p = note("a b").apply(lpe("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpenv } shouldBe listOf(0.5, 1.0)
    }

    "lpe() works as pattern extension" {
        val p = note("c").lpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.6
    }

    "lpe() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpe("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.6
    }
})
