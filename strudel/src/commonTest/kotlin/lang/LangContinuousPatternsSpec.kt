package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangContinuousPatternsSpec : StringSpec({

    "sine oscillator" {
        withClue("sine in kotlin") {
            val pattern = sine
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine with range in kotlin") {
            val pattern = sine.range(-0.5, 0.5)
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }

        withClue("sine compiled") {
            val pattern = StrudelPattern.compile("sine")!!
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine compiled with range") {
            val pattern = StrudelPattern.compile("sine.range(-0.5, 0.5)")!!
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }
    }

    "saw oscillator" {
        withClue("saw in kotlin") {
            val pattern = saw
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw with range in kotlin") {
            val pattern = saw.range(10.0, 20.0)
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }

        withClue("saw compiled with range") {
            val pattern = StrudelPattern.compile("saw.range(10, 20)")!!
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }
    }

    "tri oscillator" {
        withClue("tri in kotlin") {
            val pattern = tri
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri with range in kotlin") {
            val pattern = tri.range(-1.0, 1.0)
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri compiled with range") {
            val pattern = StrudelPattern.compile("tri.range(-1, 1)")!!
            pattern.queryArc(0.0, 0.0)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "square oscillator" {
        withClue("square in kotlin") {
            val pattern = square
            pattern.queryArc(0.1, 0.1)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square compiled with range") {
            val pattern = StrudelPattern.compile("square.range(0, 10)")!!
            pattern.queryArc(0.1, 0.1)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "Transformation order (slow and range)" {
        withClue("slow(2).range vs range.slow(2)") {
            val t = 0.5 // t=0.5 at slow(2) means phase 0.25

            val patA = sine.slow(2.0).range(-0.5, 0.5)
            val patB = sine.range(-0.5, 0.5).slow(2.0)

            val valA = patA.queryArc(t, t + EPSILON)[0].data.value?.asDouble
            val valB = patB.queryArc(t, t + EPSILON)[0].data.value?.asDouble

            // phase 0.25 -> sine is 1.0 -> mapped to range -0.5..0.5 is 0.5
            valA shouldBe (0.5 plusOrMinus EPSILON)
            valB shouldBe (valA!! plusOrMinus EPSILON)
        }

        withClue("compiled: slow(2).range vs range.slow(2)") {
            val t = 1.5 // t=1.5 at slow(2) means phase 0.75

            val patA = StrudelPattern.compile("sine.slow(2).range(0, 100)")!!
            val patB = StrudelPattern.compile("sine.range(0, 100).slow(2)")!!

            val valA = patA.queryArc(t, t + EPSILON)[0].data.value?.asDouble
            val valB = patB.queryArc(t, t + EPSILON)[0].data.value?.asDouble

            // phase 0.75 -> sine is 0.0 -> mapped to range 0..100 is 0.0
            valA shouldBe (0.0 plusOrMinus EPSILON)
            valB shouldBe (valA!! plusOrMinus EPSILON)
        }
    }
})
