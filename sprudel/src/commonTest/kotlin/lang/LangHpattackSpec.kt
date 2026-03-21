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

class LangHpattackSpec : StringSpec({

    // ---- hpattack ----

    "hpattack dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpattack(ctrl)" to seq(pat).hpattack(ctrl),
            "script pattern.hpattack(ctrl)" to SprudelPattern.compile("""seq("$pat").hpattack("$ctrl")"""),
            "string.hpattack(ctrl)" to pat.hpattack(ctrl),
            "script string.hpattack(ctrl)" to SprudelPattern.compile(""""$pat".hpattack("$ctrl")"""),
            "hpattack(ctrl)" to seq(pat).apply(hpattack(ctrl)),
            "script hpattack(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpattack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as hpattack | seq(\"0.5 1.0\").hpattack()" {
        val p = seq("0.5 1.0").hpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as hpattack | \"0.5 1.0\".hpattack()" {
        val p = "0.5 1.0".hpattack()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as hpattack | seq(\"0.5 1.0\").apply(hpattack())" {
        val p = seq("0.5 1.0").apply(hpattack())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "hpattack() sets VoiceData.hpattack" {
        val p = note("a b").apply(hpattack("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpattack } shouldBe listOf(0.5, 1.0)
    }

    "hpattack() works as pattern extension" {
        val p = note("c").hpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() works as string extension" {
        val p = "c".hpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() with continuous pattern sets hpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.SprudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.attack shouldBe 0.05
    }

    // ---- hpa (alias) ----

    "hpa dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpa(ctrl)" to seq(pat).hpa(ctrl),
            "script pattern.hpa(ctrl)" to SprudelPattern.compile("""seq("$pat").hpa("$ctrl")"""),
            "string.hpa(ctrl)" to pat.hpa(ctrl),
            "script string.hpa(ctrl)" to SprudelPattern.compile(""""$pat".hpa("$ctrl")"""),
            "hpa(ctrl)" to seq(pat).apply(hpa(ctrl)),
            "script hpa(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpa("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "reinterpret voice data as hpattack | seq(\"0.5 1.0\").hpa()" {
        val p = seq("0.5 1.0").hpa()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpattack shouldBe 0.5
            events[1].data.hpattack shouldBe 1.0
        }
    }

    "hpa() sets VoiceData.hpattack" {
        val p = note("a b").apply(hpa("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpattack } shouldBe listOf(0.5, 1.0)
    }

    "hpa() works as pattern extension" {
        val p = note("c").hpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.08
    }

    "hpa() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.08
    }
})
