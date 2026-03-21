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

class LangBpreleaseSpec : StringSpec({

    // ---- bprelease ----

    "bprelease dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bprelease(ctrl)" to seq(pat).bprelease(ctrl),
            "script pattern.bprelease(ctrl)" to SprudelPattern.compile("""seq("$pat").bprelease("$ctrl")"""),
            "string.bprelease(ctrl)" to pat.bprelease(ctrl),
            "script string.bprelease(ctrl)" to SprudelPattern.compile(""""$pat".bprelease("$ctrl")"""),
            "bprelease(ctrl)" to seq(pat).apply(bprelease(ctrl)),
            "script bprelease(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bprelease("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as bprelease | seq(\"0.5 1.0\").bprelease()" {
        val p = seq("0.5 1.0").bprelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as bprelease | \"0.5 1.0\".bprelease()" {
        val p = "0.5 1.0".bprelease()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as bprelease | seq(\"0.5 1.0\").apply(bprelease())" {
        val p = seq("0.5 1.0").apply(bprelease())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "bprelease() sets VoiceData.bprelease" {
        val p = note("a b").apply(bprelease("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bprelease } shouldBe listOf(0.5, 1.0)
    }

    "bprelease() works as pattern extension" {
        val p = note("c").bprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() works as string extension" {
        val p = "c".bprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bprelease("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() with continuous pattern sets bprelease correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bprelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bprelease shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bprelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bprelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.SprudelVoiceData.empty.copy(
            bandf = 1000.0,
            bprelease = 0.4
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.release shouldBe 0.4
    }

    // ---- bpr (alias) ----

    "bpr dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpr(ctrl)" to seq(pat).bpr(ctrl),
            "script pattern.bpr(ctrl)" to SprudelPattern.compile("""seq("$pat").bpr("$ctrl")"""),
            "string.bpr(ctrl)" to pat.bpr(ctrl),
            "script string.bpr(ctrl)" to SprudelPattern.compile(""""$pat".bpr("$ctrl")"""),
            "bpr(ctrl)" to seq(pat).apply(bpr(ctrl)),
            "script bpr(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bpr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "reinterpret voice data as bprelease | seq(\"0.5 1.0\").bpr()" {
        val p = seq("0.5 1.0").bpr()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bprelease shouldBe 0.5
            events[1].data.bprelease shouldBe 1.0
        }
    }

    "bpr() sets VoiceData.bprelease" {
        val p = note("a b").apply(bpr("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bprelease } shouldBe listOf(0.5, 1.0)
    }

    "bpr() works as pattern extension" {
        val p = note("c").bpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.45
    }

    "bpr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bpr("0.45")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.45
    }
})
