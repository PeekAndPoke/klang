package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangContinuousPatternsSpec : StringSpec({

    "sine: validates sin(t * 2 * PI)" {
        // Sample at specific phases: 0, PI/2, PI, 3PI/2
        sine.queryArc(0.0, 0.0)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        sine.queryArc(0.25, 0.25)[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        sine.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        sine.queryArc(0.75, 0.75)[0].data.value shouldBe (-1.0 plusOrMinus EPSILON)
    }

    "saw: validates ramp -1 to 1" {
        // saw: (t % 1.0) * 2.0 - 1.0
        saw.queryArc(0.0, 0.0)[0].data.value shouldBe (-1.0 plusOrMinus EPSILON)
        saw.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        saw.queryArc(0.99, 0.99)[0].data.value shouldBe (0.98 plusOrMinus EPSILON)
    }

    "isaw: validates ramp 1 to -1" {
        // isaw: 1.0 - (t % 1.0) * 2.0
        isaw.queryArc(0.0, 0.0)[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        isaw.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        isaw.queryArc(0.99, 0.99)[0].data.value shouldBe (-0.98 plusOrMinus EPSILON)
    }

    "tri: validates triangle -1 -> 1 -> -1" {
        // tri: 0.0 -> -1, 0.25 -> 0, 0.5 -> 1, 0.75 -> 0, 1.0 -> -1
        tri.queryArc(0.0, 0.0)[0].data.value shouldBe (-1.0 plusOrMinus EPSILON)
        tri.queryArc(0.25, 0.25)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        tri.queryArc(0.5, 0.5)[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        tri.queryArc(0.75, 0.75)[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "square: validates square pulse -1 or 1" {
        // First half is 1, second half is -1
        square.queryArc(0.1, 0.1)[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        square.queryArc(0.6, 0.6)[0].data.value shouldBe (-1.0 plusOrMinus EPSILON)
    }

    "silence: returns no events" {
        silence.queryArc(0.0, 1.0) shouldBe emptyList()
    }

    "rest: alias for silence" {
        rest.queryArc(0.0, 1.0) shouldBe emptyList()
    }

    "sine works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""sine""")

        val events = p?.queryArc(0.0, 0.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "saw works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""saw""")

        val events = p?.queryArc(0.5, 0.5) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "isaw works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""isaw""")

        val events = p?.queryArc(0.5, 0.5) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "tri works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""tri""")

        val events = p?.queryArc(0.5, 0.5) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "square works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""square""")

        val events = p?.queryArc(0.1, 0.1) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "silence works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""silence""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events shouldBe emptyList()
    }
})
