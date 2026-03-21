package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBpenvSpec : StringSpec({

    // ---- bpenv ----

    "bpenv dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpenv(ctrl)" to seq(pat).bpenv(ctrl),
            "script pattern.bpenv(ctrl)" to StrudelPattern.compile("""seq("$pat").bpenv("$ctrl")"""),
            "string.bpenv(ctrl)" to pat.bpenv(ctrl),
            "script string.bpenv(ctrl)" to StrudelPattern.compile(""""$pat".bpenv("$ctrl")"""),
            "bpenv(ctrl)" to seq(pat).apply(bpenv(ctrl)),
            "script bpenv(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpenv("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | seq(\"0.5 1.0\").bpenv()" {
        val p = seq("0.5 1.0").bpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | \"0.5 1.0\".bpenv()" {
        val p = "0.5 1.0".bpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | seq(\"0.5 1.0\").apply(bpenv())" {
        val p = seq("0.5 1.0").apply(bpenv())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "bpenv() sets VoiceData.bpenv" {
        val p = note("a b").apply(bpenv("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpenv } shouldBe listOf(0.5, 1.0)
    }

    "bpenv() works as pattern extension" {
        val p = note("c").bpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() works as string extension" {
        val p = "c".bpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpenv("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() with continuous pattern sets bpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpenv shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpenv = 0.5
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.depth shouldBe 0.5
    }

    // ---- bpe (alias) ----

    "bpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpe(ctrl)" to seq(pat).bpe(ctrl),
            "script pattern.bpe(ctrl)" to StrudelPattern.compile("""seq("$pat").bpe("$ctrl")"""),
            "string.bpe(ctrl)" to pat.bpe(ctrl),
            "script string.bpe(ctrl)" to StrudelPattern.compile(""""$pat".bpe("$ctrl")"""),
            "bpe(ctrl)" to seq(pat).apply(bpe(ctrl)),
            "script bpe(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | seq(\"0.5 1.0\").bpe()" {
        val p = seq("0.5 1.0").bpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "bpe() sets VoiceData.bpenv" {
        val p = note("a b").apply(bpe("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpenv } shouldBe listOf(0.5, 1.0)
    }

    "bpe() works as pattern extension" {
        val p = note("c").bpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.6
    }

    "bpe() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpe("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.6
    }
})
