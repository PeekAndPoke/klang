package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangDecaySpec : StringSpec({

    "decay dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.decay(ctrl)" to
                    seq(pat).decay(ctrl),
            "script pattern.decay(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").decay("$ctrl")"""),
            "string.decay(ctrl)" to
                    pat.decay(ctrl),
            "script string.decay(ctrl)" to
                    StrudelPattern.compile(""""$pat".decay("$ctrl")"""),
            "decay(ctrl)" to
                    seq(pat).apply(decay(ctrl)),
            "script decay(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(decay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.decay shouldBe 0.1
            events[1].data.decay shouldBe 0.5
        }
    }

    "reinterpret voice data as decay | seq(\"0 1\").decay()" {
        val p = seq("0.1 0.5").decay()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.decay shouldBe 0.1
            events[1].data.decay shouldBe 0.5
        }
    }

    "reinterpret voice data as decay | \"0 1\".decay()" {
        val p = "0.1 0.5".decay()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.decay shouldBe 0.1
            events[1].data.decay shouldBe 0.5
        }
    }

    "reinterpret voice data as decay | seq(\"0 1\").apply(decay())" {
        val p = seq("0.1 0.5").apply(decay())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.decay shouldBe 0.1
            events[1].data.decay shouldBe 0.5
        }
    }

    "decay() sets VoiceData.decay" {
        val p = "0 1".apply(decay("0.1 0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.decay } shouldBe listOf(0.1, 0.5)
    }

    "decay() works as pattern extension" {
        val p = note("c").decay("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.decay shouldBe 0.1
    }

    "decay() works as string extension" {
        val p = "c".decay("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.decay shouldBe 0.1
    }

    "decay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").decay("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.decay shouldBe 0.1
    }

    "decay() with continuous pattern sets decay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").decay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.decay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.decay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.decay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.decay shouldBe (0.0 plusOrMinus EPSILON)
    }
})
