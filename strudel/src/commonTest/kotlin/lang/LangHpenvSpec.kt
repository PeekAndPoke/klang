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

class LangHpenvSpec : StringSpec({

    // ---- hpenv ----

    "hpenv dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpenv(ctrl)" to seq(pat).hpenv(ctrl),
            "script pattern.hpenv(ctrl)" to StrudelPattern.compile("""seq("$pat").hpenv("$ctrl")"""),
            "string.hpenv(ctrl)" to pat.hpenv(ctrl),
            "script string.hpenv(ctrl)" to StrudelPattern.compile(""""$pat".hpenv("$ctrl")"""),
            "hpenv(ctrl)" to seq(pat).apply(hpenv(ctrl)),
            "script hpenv(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpenv("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | seq(\"0.5 1.0\").hpenv()" {
        val p = seq("0.5 1.0").hpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | \"0.5 1.0\".hpenv()" {
        val p = "0.5 1.0".hpenv()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | seq(\"0.5 1.0\").apply(hpenv())" {
        val p = seq("0.5 1.0").apply(hpenv())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "hpenv() sets VoiceData.hpenv" {
        val p = note("a b").apply(hpenv("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpenv } shouldBe listOf(0.5, 1.0)
    }

    "hpenv() works as pattern extension" {
        val p = note("c").hpenv("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() works as string extension" {
        val p = "c".hpenv("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpenv("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpenv() with continuous pattern sets hpenv correctly" {
        val p = note("a b c d").hpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpenv = 0.7
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.depth shouldBe 0.7
    }

    // ---- hpe (alias) ----

    "hpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpe(ctrl)" to seq(pat).hpe(ctrl),
            "script pattern.hpe(ctrl)" to StrudelPattern.compile("""seq("$pat").hpe("$ctrl")"""),
            "string.hpe(ctrl)" to pat.hpe(ctrl),
            "script string.hpe(ctrl)" to StrudelPattern.compile(""""$pat".hpe("$ctrl")"""),
            "hpe(ctrl)" to seq(pat).apply(hpe(ctrl)),
            "script hpe(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | seq(\"0.5 1.0\").hpe()" {
        val p = seq("0.5 1.0").hpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "hpe() sets VoiceData.hpenv" {
        val p = note("a b").apply(hpe("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpenv } shouldBe listOf(0.5, 1.0)
    }

    "hpe() works as pattern extension" {
        val p = note("c").hpe("0.65")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.65
    }

    "hpe() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpe("0.65")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.65
    }
})