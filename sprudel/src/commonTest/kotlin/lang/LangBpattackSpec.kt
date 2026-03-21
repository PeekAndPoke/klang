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

class LangBpattackSpec : StringSpec({

    // ---- bpattack ----

    "bpattack dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpattack(ctrl)" to seq(pat).bpattack(ctrl),
            "script pattern.bpattack(ctrl)" to StrudelPattern.compile("""seq("$pat").bpattack("$ctrl")"""),
            "string.bpattack(ctrl)" to pat.bpattack(ctrl),
            "script string.bpattack(ctrl)" to StrudelPattern.compile(""""$pat".bpattack("$ctrl")"""),
            "bpattack(ctrl)" to seq(pat).apply(bpattack(ctrl)),
            "script bpattack(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpattack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as bpattack | seq(\"0.5 1.0\").bpattack()" {
        val p = seq("0.5 1.0").bpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as bpattack | \"0.5 1.0\".bpattack()" {
        val p = "0.5 1.0".bpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as bpattack | seq(\"0.5 1.0\").apply(bpattack())" {
        val p = seq("0.5 1.0").apply(bpattack())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "bpattack() sets VoiceData.bpattack" {
        val p = note("a b").apply(bpattack("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpattack } shouldBe listOf(0.5, 1.0)
    }

    "bpattack() works as pattern extension" {
        val p = note("c").bpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() works as string extension" {
        val p = "c".bpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() with continuous pattern sets bpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.attack shouldBe 0.05
    }

    // ---- bpa (alias) ----

    "bpa dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpa(ctrl)" to seq(pat).bpa(ctrl),
            "script pattern.bpa(ctrl)" to StrudelPattern.compile("""seq("$pat").bpa("$ctrl")"""),
            "string.bpa(ctrl)" to pat.bpa(ctrl),
            "script string.bpa(ctrl)" to StrudelPattern.compile(""""$pat".bpa("$ctrl")"""),
            "bpa(ctrl)" to seq(pat).apply(bpa(ctrl)),
            "script bpa(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpa("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as bpattack | seq(\"0.5 1.0\").bpa()" {
        val p = seq("0.5 1.0").bpa()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpattack shouldBe 0.5
            events[1].data.bpattack shouldBe 1.0
        }
    }

    "bpa() sets VoiceData.bpattack" {
        val p = note("a b").apply(bpa("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpattack } shouldBe listOf(0.5, 1.0)
    }

    "bpa() works as pattern extension" {
        val p = note("c").bpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.08
    }

    "bpa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.08
    }
})
