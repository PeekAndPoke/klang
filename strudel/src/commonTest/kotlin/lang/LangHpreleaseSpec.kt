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

class LangHpreleaseSpec : StringSpec({

    // ---- hprelease ----

    "hprelease dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hprelease(ctrl)" to seq(pat).hprelease(ctrl),
            "script pattern.hprelease(ctrl)" to StrudelPattern.compile("""seq("$pat").hprelease("$ctrl")"""),
            "string.hprelease(ctrl)" to pat.hprelease(ctrl),
            "script string.hprelease(ctrl)" to StrudelPattern.compile(""""$pat".hprelease("$ctrl")"""),
            "hprelease(ctrl)" to seq(pat).apply(hprelease(ctrl)),
            "script hprelease(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hprelease("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as hprelease | seq(\"0.5 1.0\").hprelease()" {
        val p = seq("0.5 1.0").hprelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as hprelease | \"0.5 1.0\".hprelease()" {
        val p = "0.5 1.0".hprelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as hprelease | seq(\"0.5 1.0\").apply(hprelease())" {
        val p = seq("0.5 1.0").apply(hprelease())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "hprelease() sets VoiceData.hprelease" {
        val p = note("a b").apply(hprelease("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hprelease } shouldBe listOf(0.5, 1.0)
    }

    "hprelease() works as pattern extension" {
        val p = note("c").hprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() works as string extension" {
        val p = "c".hprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hprelease("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.4
    }

    "hprelease() with continuous pattern sets hprelease correctly" {
        val p = note("a b c d").hprelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hprelease shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hprelease shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hprelease shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hprelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hprelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hprelease = 0.5
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.release shouldBe 0.5
    }

    // ---- hpr (alias) ----

    "hpr dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpr(ctrl)" to seq(pat).hpr(ctrl),
            "script pattern.hpr(ctrl)" to StrudelPattern.compile("""seq("$pat").hpr("$ctrl")"""),
            "string.hpr(ctrl)" to pat.hpr(ctrl),
            "script string.hpr(ctrl)" to StrudelPattern.compile(""""$pat".hpr("$ctrl")"""),
            "hpr(ctrl)" to seq(pat).apply(hpr(ctrl)),
            "script hpr(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as hprelease | seq(\"0.5 1.0\").hpr()" {
        val p = seq("0.5 1.0").hpr()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hprelease shouldBe 0.5
            events[1].data.hprelease shouldBe 1.0
        }
    }

    "hpr() sets VoiceData.hprelease" {
        val p = note("a b").apply(hpr("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hprelease } shouldBe listOf(0.5, 1.0)
    }

    "hpr() works as pattern extension" {
        val p = note("c").hpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.45
    }

    "hpr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpr("0.45")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hprelease shouldBe 0.45
    }
})