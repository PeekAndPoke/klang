package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangLpattackSpec : StringSpec({

    // ---- lpattack ----

    "lpattack dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpattack(ctrl)" to seq(pat).lpattack(ctrl),
            "script pattern.lpattack(ctrl)" to StrudelPattern.compile("""seq("$pat").lpattack("$ctrl")"""),
            "string.lpattack(ctrl)" to pat.lpattack(ctrl),
            "script string.lpattack(ctrl)" to StrudelPattern.compile(""""$pat".lpattack("$ctrl")"""),
            "lpattack(ctrl)" to seq(pat).apply(lpattack(ctrl)),
            "script lpattack(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lpattack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as lpattack | seq(\"0.5 1.0\").lpattack()" {
        val p = seq("0.5 1.0").lpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as lpattack | \"0.5 1.0\".lpattack()" {
        val p = "0.5 1.0".lpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as lpattack | seq(\"0.5 1.0\").apply(lpattack())" {
        val p = seq("0.5 1.0").apply(lpattack())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "lpattack() sets VoiceData.lpattack" {
        val p = note("a b").apply(lpattack("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpattack } shouldBe listOf(0.5, 1.0)
    }

    "lpattack() works as pattern extension" {
        val p = note("c").lpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() works as string extension" {
        val p = "c".lpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() with continuous pattern sets lpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.attack shouldBe 0.05
    }

    // ---- lpa (alias) ----

    "lpa dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpa(ctrl)" to seq(pat).lpa(ctrl),
            "script pattern.lpa(ctrl)" to StrudelPattern.compile("""seq("$pat").lpa("$ctrl")"""),
            "string.lpa(ctrl)" to pat.lpa(ctrl),
            "script string.lpa(ctrl)" to StrudelPattern.compile(""""$pat".lpa("$ctrl")"""),
            "lpa(ctrl)" to seq(pat).apply(lpa(ctrl)),
            "script lpa(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lpa("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as lpattack | seq(\"0.5 1.0\").lpa()" {
        val p = seq("0.5 1.0").lpa()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpattack shouldBe 0.5
            events[1].data.lpattack shouldBe 1.0
        }
    }

    "lpa() sets VoiceData.lpattack" {
        val p = note("a b").apply(lpa("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.lpattack } shouldBe listOf(0.5, 1.0)
    }

    "lpa() works as pattern extension" {
        val p = note("c").lpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.08
    }

    "lpa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.08
    }
})
