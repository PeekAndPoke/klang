package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangPostGainSpec : StringSpec({

    "postgain dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.postgain(ctrl)" to
                    seq(pat).postgain(ctrl),
            "script pattern.postgain(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").postgain("$ctrl")"""),
            "string.postgain(ctrl)" to
                    pat.postgain(ctrl),
            "script string.postgain(ctrl)" to
                    StrudelPattern.compile(""""$pat".postgain("$ctrl")"""),
            "postgain(ctrl)" to
                    seq(pat).apply(postgain(ctrl)),
            "script postgain(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(postgain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.postGain shouldBe 0.0
            events[1].data.postGain shouldBe 0.25
        }
    }

    "reinterpret voice data as postgain | seq(\"0 1\").postgain()" {
        val p = seq("0 1").postgain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.postGain shouldBe 0.0
            events[1].data.postGain shouldBe 1.0
        }
    }

    "reinterpret voice data as postgain | \"0 1\".postgain()" {
        val p = "0 1".postgain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.postGain shouldBe 0.0
            events[1].data.postGain shouldBe 1.0
        }
    }

    "reinterpret voice data as postgain | seq(\"0 1\").apply(postgain())" {
        val p = seq("0 1").apply(postgain())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.postGain shouldBe 0.0
            events[1].data.postGain shouldBe 1.0
        }
    }

    "top-level postgain() sets VoiceData.postgain correctly" {
        // Given a simple sequence of postgain values within one cycle
        val p = sound("hh hh").apply(postgain("0.5 1.0"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the postgain values in order
        events.size shouldBe 2
        events.map { it.data.postGain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern postgain() sets VoiceData.postgain on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the postgain per step
        val p = base.postgain("0.1 0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the postgain values in order
        events.map { it.data.postGain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "postgain() works as string extension" {
        val p = "c3".postgain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.postGain shouldBe 0.5
    }

    "postgain() works within compiled code as top-level function" {
        val p = StrudelPattern.compile(""""a b".apply(postgain("0.5 1.0"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.postGain } shouldBe listOf(0.5, 1.0)
    }

    "postgain() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").postgain("0.5 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.postGain } shouldBe listOf(0.5, 1.0)
    }

    "postgain() with continuous pattern sets postgain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").postgain(sine)
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 4
            // t=0.0: sine(0) = 0.5
            events[0].data.postGain shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.25: sine(0.25) = 1.0
            events[1].data.postGain shouldBe (1.0 plusOrMinus EPSILON)
            // t=0.5: sine(0.5) = 0.5
            events[2].data.postGain shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.75: sine(0.75) = 0.0
            events[3].data.postGain shouldBe (0.0 plusOrMinus EPSILON)
        }
    }
})
