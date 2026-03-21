package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBandqSpec : StringSpec({

    // ---- bandq ----

    "bandq dsl interface" {
        val pat = "a b"
        val ctrl = "1.2 1.8"
        dslInterfaceTests(
            "pattern.bandq(ctrl)" to seq(pat).bandq(ctrl),
            "script pattern.bandq(ctrl)" to SprudelPattern.compile("""seq("$pat").bandq("$ctrl")"""),
            "string.bandq(ctrl)" to pat.bandq(ctrl),
            "script string.bandq(ctrl)" to SprudelPattern.compile(""""$pat".bandq("$ctrl")"""),
            "bandq(ctrl)" to seq(pat).apply(bandq(ctrl)),
            "script bandq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bandq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "reinterpret voice data as bandq | seq(\"1.2 1.8\").bandq()" {
        val p = seq("1.2 1.8").bandq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "reinterpret voice data as bandq | \"1.2 1.8\".bandq()" {
        val p = "1.2 1.8".bandq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "reinterpret voice data as bandq | seq(\"1.2 1.8\").apply(bandq())" {
        val p = seq("1.2 1.8").apply(bandq())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "bandq() sets SprudelVoiceData.bandq" {
        val p = note("a b").apply(bandq("1.2 1.8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bandq shouldBe 1.2
        events[1].data.bandq shouldBe 1.8
    }

    "bandq() sets BPF Q specifically" {
        // Apply BPF first, then update bandq to 1.5
        val p = note("c").bandf("1000").bandq("1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
        events[0].data.bandq shouldBe 1.5

        // Verify conversion to VoiceData
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.BandPass).q shouldBe 1.5
    }

    "bandq() works as pattern extension" {
        val p = note("c").bandq("1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bandq() works as string extension" {
        val p = "c".bandq("1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bandq() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bandq("1.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bandq() with continuous pattern sets bandq correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bandq(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bandq shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bandq shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bandq shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bandq shouldBe (0.0 plusOrMinus EPSILON)
    }

    // ---- bpq (alias) ----

    "bpq dsl interface" {
        val pat = "a b"
        val ctrl = "1.2 1.8"
        dslInterfaceTests(
            "pattern.bpq(ctrl)" to seq(pat).bpq(ctrl),
            "script pattern.bpq(ctrl)" to SprudelPattern.compile("""seq("$pat").bpq("$ctrl")"""),
            "string.bpq(ctrl)" to pat.bpq(ctrl),
            "script string.bpq(ctrl)" to SprudelPattern.compile(""""$pat".bpq("$ctrl")"""),
            "bpq(ctrl)" to seq(pat).apply(bpq(ctrl)),
            "script bpq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bpq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "reinterpret voice data as bandq | seq(\"1.2 1.8\").bpq()" {
        val p = seq("1.2 1.8").bpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "bpq() is an alias for bandq()" {
        val p = note("a").apply(bpq("2.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 2.5
    }

    "bpq() works as pattern extension" {
        val p = note("c").bpq("2.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 2.5
    }

    "bpq() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bpq("2.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bandq shouldBe 2.5
    }
})
