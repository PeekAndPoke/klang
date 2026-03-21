package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangSustainSpec : StringSpec({

    "sustain dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.sustain(ctrl)" to
                    seq(pat).sustain(ctrl),
            "script pattern.sustain(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").sustain("$ctrl")"""),
            "string.sustain(ctrl)" to
                    pat.sustain(ctrl),
            "script string.sustain(ctrl)" to
                    StrudelPattern.compile(""""$pat".sustain("$ctrl")"""),
            "sustain(ctrl)" to
                    seq(pat).apply(sustain(ctrl)),
            "script sustain(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(sustain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.sustain shouldBe 0.1
            events[1].data.sustain shouldBe 0.5
        }
    }

    "reinterpret voice data as sustain | seq(\"0 1\").sustain()" {
        val p = seq("0.1 0.5").sustain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.sustain shouldBe 0.1
            events[1].data.sustain shouldBe 0.5
        }
    }

    "reinterpret voice data as sustain | \"0 1\".sustain()" {
        val p = "0.1 0.5".sustain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.sustain shouldBe 0.1
            events[1].data.sustain shouldBe 0.5
        }
    }

    "reinterpret voice data as sustain | seq(\"0 1\").apply(sustain())" {
        val p = seq("0.1 0.5").apply(sustain())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.sustain shouldBe 0.1
            events[1].data.sustain shouldBe 0.5
        }
    }

    "sustain() sets VoiceData.sustain" {
        val p = "0 1".apply(sustain("0.1 0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.sustain } shouldBe listOf(0.1, 0.5)
    }

    "sustain() works as pattern extension" {
        val p = note("c").sustain("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sustain shouldBe 0.1
    }

    "sustain() works as string extension" {
        val p = "c".sustain("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sustain shouldBe 0.1
    }

    "sustain() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").sustain("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.sustain shouldBe 0.1
    }

    "sustain() with continuous pattern sets sustain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").sustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.sustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.sustain shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.sustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.sustain shouldBe (0.0 plusOrMinus EPSILON)
    }
})
