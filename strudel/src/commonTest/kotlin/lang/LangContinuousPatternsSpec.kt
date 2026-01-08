package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class LangContinuousPatternsSpec : StringSpec({

    "sine: validates sin(t * 2 * PI)" {
        // Sample at specific phases: 0, PI/2, PI, 3PI/2
        sine.queryArc(0.0, 0.0)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
        sine.queryArc(0.25, 0.25)[0].data.value shouldBe (1.0 plusOrMinus 1e-9)
        sine.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
        sine.queryArc(0.75, 0.75)[0].data.value shouldBe (-1.0 plusOrMinus 1e-9)
    }

    "saw: validates ramp -1 to 1" {
        // saw: (t % 1.0) * 2.0 - 1.0
        saw.queryArc(0.0, 0.0)[0].data.value shouldBe (-1.0 plusOrMinus 1e-9)
        saw.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
        saw.queryArc(0.99, 0.99)[0].data.value shouldBe (0.98 plusOrMinus 1e-9)
    }

    "isaw: validates ramp 1 to -1" {
        // isaw: 1.0 - (t % 1.0) * 2.0
        isaw.queryArc(0.0, 0.0)[0].data.value shouldBe (1.0 plusOrMinus 1e-9)
        isaw.queryArc(0.5, 0.5)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
        isaw.queryArc(0.99, 0.99)[0].data.value shouldBe (-0.98 plusOrMinus 1e-9)
    }

    "tri: validates triangle -1 -> 1 -> -1" {
        // tri: 0.0 -> -1, 0.25 -> 0, 0.5 -> 1, 0.75 -> 0, 1.0 -> -1
        tri.queryArc(0.0, 0.0)[0].data.value shouldBe (-1.0 plusOrMinus 1e-9)
        tri.queryArc(0.25, 0.25)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
        tri.queryArc(0.5, 0.5)[0].data.value shouldBe (1.0 plusOrMinus 1e-9)
        tri.queryArc(0.75, 0.75)[0].data.value shouldBe (0.0 plusOrMinus 1e-9)
    }

    "square: validates square pulse -1 or 1" {
        // First half is 1, second half is -1
        square.queryArc(0.1, 0.1)[0].data.value shouldBe (1.0 plusOrMinus 1e-9)
        square.queryArc(0.6, 0.6)[0].data.value shouldBe (-1.0 plusOrMinus 1e-9)
    }

    "silence: returns no events" {
        silence.queryArc(0.0, 1.0) shouldBe emptyList()
    }

    "rest: alias for silence" {
        rest.queryArc(0.0, 1.0) shouldBe emptyList()
    }
})
