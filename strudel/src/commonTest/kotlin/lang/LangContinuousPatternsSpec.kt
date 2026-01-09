package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangContinuousPatternsSpec : StringSpec({

    "sine: validates sin(t * 2 * PI) normalized to 0..1" {
        // sine: (sin(t * 2 * PI) + 1.0) / 2.0
        sine.queryArc(0.0.toRational(), 0.0.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
        sine.queryArc(0.25.toRational(), 0.25.toRational())[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        sine.queryArc(0.5.toRational(), 0.5.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
        sine.queryArc(0.75.toRational(), 0.75.toRational())[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "saw: validates ramp 0 to 1" {
        // saw: t % 1.0
        saw.queryArc(0.0.toRational(), 0.0.toRational())[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        saw.queryArc(0.5.toRational(), 0.5.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
        saw.queryArc(0.99.toRational(), 0.99.toRational())[0].data.value shouldBe (0.99 plusOrMinus EPSILON)
    }

    "isaw: validates ramp 1 to 0" {
        // isaw: 1.0 - (t % 1.0)
        isaw.queryArc(0.0.toRational(), 0.0.toRational())[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        isaw.queryArc(0.5.toRational(), 0.5.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
        isaw.queryArc(0.99.toRational(), 0.99.toRational())[0].data.value shouldBe (0.01 plusOrMinus EPSILON)
    }

    "tri: validates triangle 0 -> 1 -> 0" {
        // tri: 0.0 -> 0, 0.25 -> 0.5, 0.5 -> 1.0, 0.75 -> 0.5, 1.0 -> 0.0
        tri.queryArc(0.0.toRational(), 0.0.toRational())[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        tri.queryArc(0.25.toRational(), 0.25.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
        tri.queryArc(0.5.toRational(), 0.5.toRational())[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
        tri.queryArc(0.75.toRational(), 0.75.toRational())[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
    }

    "square: validates square pulse 0 or 1" {
        // First half is 1, second half is 0
        square.queryArc(0.1.toRational(), 0.1.toRational())[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
        square.queryArc(0.6.toRational(), 0.6.toRational())[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "silence: returns no events" {
        silence.queryArc(0.0.toRational(), 1.0.toRational()) shouldBe emptyList()
    }

    "rest: alias for silence" {
        rest.queryArc(0.0.toRational(), 1.0.toRational()) shouldBe emptyList()
    }

    "sine works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""sine""")

        val events = p?.queryArc(0.25.toRational(), 0.25.toRational()) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "saw works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""saw""")

        val events = p?.queryArc(0.5.toRational(), 0.5.toRational()) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
    }

    "isaw works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""isaw""")

        val events = p?.queryArc(0.5.toRational(), 0.5.toRational()) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.5 plusOrMinus EPSILON)
    }

    "tri works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""tri""")

        val events = p?.queryArc(0.5.toRational(), 0.5.toRational()) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "square works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""square""")

        val events = p?.queryArc(0.1.toRational(), 0.1.toRational()) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "silence works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""silence""")

        val events = p?.queryArc(0.0.toRational(), 1.0.toRational()) ?: emptyList()

        events.size shouldBe 0
    }

    "rest works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""rest""")

        val events = p?.queryArc(0.0.toRational(), 1.0.toRational()) ?: emptyList()

        events.size shouldBe 0
    }

    "perlin: validates noise signal within 0..1 range" {
        val events = perlin.queryArc(0.0.toRational(), 1.0.toRational())
        events.size shouldBe 1

        // Perlin noise is randomized but the implementation ensures it stays within bounds
        val value = events[0].data.value ?: -1.0
        (value >= 0.0) shouldBe true
        (value <= 1.0) shouldBe true
    }

    "perlin works within compiled code as top-level pattern" {
        val p = StrudelPattern.compile("""perlin""")

        val events = p?.queryArc(0.5.toRational(), 0.5.toRational()) ?: emptyList()

        events.size shouldBe 1
        val value = events[0].data.value ?: -1.0
        (value >= 0.0) shouldBe true
        (value <= 1.0) shouldBe true
    }
})
