package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangContinuousPatternsSpec : StringSpec({

    "steady pattern" {
        withClue("steady in kotlin") {
            val pattern = steady(0.5)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("steady with range in kotlin") {
            val pattern = steady(0.5).range(10.0, 20.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }

        withClue("steady compiled") {
            val pattern = StrudelPattern.compile("steady(0.75)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.75 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.75 plusOrMinus EPSILON)
        }

        withClue("steady compiled with range") {
            val pattern = StrudelPattern.compile("steady(0.5).range(10, 20)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }
    }

    "signal pattern" {
        withClue("signal in kotlin") {
            val pattern = signal { t -> t * 2.0 }
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        }

        withClue("signal with range in kotlin") {
            val pattern = signal { t -> t }.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }

        withClue("signal compiled") {
            val pattern = StrudelPattern.compile("signal(t => t * 3)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }

        withClue("signal compiled with range") {
            val pattern = StrudelPattern.compile("signal(t => t).range(0, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "time pattern" {
        withClue("time in kotlin") {
            val pattern = time
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(2.5, 2.5 + EPSILON)[0].data.value?.asDouble shouldBe (2.5 plusOrMinus EPSILON)
        }

        withClue("time with range in kotlin") {
            val pattern = time.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }

        withClue("time compiled") {
            val pattern = StrudelPattern.compile("time")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(10.0, 10.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("time compiled with range") {
            val pattern = StrudelPattern.compile("time.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }
    }

    "sine oscillator" {
        withClue("sine in kotlin") {
            val pattern = sine
            pattern.queryArc(0.0, 0.0 + EPSILON).shouldNotBeNull()
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine with range in kotlin") {
            val pattern = sine.range(-0.5, 0.5)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }

        withClue("sine compiled") {
            val pattern = StrudelPattern.compile("sine")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine compiled with range") {
            val pattern = StrudelPattern.compile("sine.range(-0.5, 0.5)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }
    }

    "sine2 oscillator" {
        withClue("sine2 in kotlin") {
            val pattern = sine2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("sine2 with range in kotlin") {
            val pattern = sine2.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine2 compiled") {
            val pattern = StrudelPattern.compile("sine2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("sine2 compiled with range") {
            val pattern = StrudelPattern.compile("sine2.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "cosine oscillator" {
        withClue("cosine in kotlin") {
            val pattern = cosine
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("cosine with range in kotlin") {
            val pattern = cosine.range(-1.0, 1.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("cosine compiled") {
            val pattern = StrudelPattern.compile("cosine")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("cosine compiled with range") {
            val pattern = StrudelPattern.compile("cosine.range(-1, 1)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }
    }

    "cosine2 oscillator" {
        withClue("cosine2 in kotlin") {
            val pattern = cosine2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("cosine2 with range in kotlin") {
            val pattern = cosine2.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("cosine2 compiled") {
            val pattern = StrudelPattern.compile("cosine2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("cosine2 compiled with range") {
            val pattern = StrudelPattern.compile("cosine2.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "saw oscillator" {
        withClue("saw in kotlin") {
            val pattern = saw
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw with range in kotlin") {
            val pattern = saw.range(10.0, 20.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }

        withClue("saw compiled") {
            val pattern = StrudelPattern.compile("saw")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw compiled with range") {
            val pattern = StrudelPattern.compile("saw.range(10, 20)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }
    }

    "saw2 oscillator" {
        withClue("saw2 in kotlin") {
            val pattern = saw2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("saw2 with range in kotlin") {
            val pattern = saw2.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
        }

        withClue("saw2 compiled") {
            val pattern = StrudelPattern.compile("saw2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("saw2 compiled with range") {
            val pattern = StrudelPattern.compile("saw2.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
        }
    }

    "isaw oscillator" {
        withClue("isaw in kotlin") {
            val pattern = isaw
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("isaw with range in kotlin") {
            val pattern = isaw.range(0.0, 10.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
        }

        withClue("isaw compiled") {
            val pattern = StrudelPattern.compile("isaw")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("isaw compiled with range") {
            val pattern = StrudelPattern.compile("isaw.range(0, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "isaw2 oscillator" {
        withClue("isaw2 in kotlin") {
            val pattern = isaw2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("isaw2 with range in kotlin") {
            val pattern = isaw2.range(-10.0, 10.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("isaw2 compiled") {
            val pattern = StrudelPattern.compile("isaw2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("isaw2 compiled with range") {
            val pattern = StrudelPattern.compile("isaw2.range(-10, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "tri oscillator" {
        withClue("tri in kotlin") {
            val pattern = tri
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri with range in kotlin") {
            val pattern = tri.range(-1.0, 1.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri compiled") {
            val pattern = StrudelPattern.compile("tri")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri compiled with range") {
            val pattern = StrudelPattern.compile("tri.range(-1, 1)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "tri2 oscillator" {
        withClue("tri2 in kotlin") {
            val pattern = tri2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("tri2 with range in kotlin") {
            val pattern = tri2.range(-10.0, 10.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("tri2 compiled") {
            val pattern = StrudelPattern.compile("tri2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri2 compiled with range") {
            val pattern = StrudelPattern.compile("tri2.range(-10, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "itri oscillator" {
        withClue("itri in kotlin") {
            val pattern = itri
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("itri with range in kotlin") {
            val pattern = itri.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("itri compiled") {
            val pattern = StrudelPattern.compile("itri")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("itri compiled with range") {
            val pattern = StrudelPattern.compile("itri.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "itri2 oscillator" {
        withClue("itri2 in kotlin") {
            val pattern = itri2
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("itri2 with range in kotlin") {
            val pattern = itri2.range(-10.0, 10.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
        }

        withClue("itri2 compiled") {
            val pattern = StrudelPattern.compile("itri2")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("itri2 compiled with range") {
            val pattern = StrudelPattern.compile("itri2.range(-10, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
        }
    }

    "square oscillator" {
        withClue("square in kotlin") {
            val pattern = square
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square with range in kotlin") {
            val pattern = square.range(0.0, 10.0)
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("square compiled") {
            val pattern = StrudelPattern.compile("square")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square compiled with range") {
            val pattern = StrudelPattern.compile("square.range(0, 10)")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "square2 oscillator" {
        withClue("square2 in kotlin") {
            val pattern = square2
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square2 with range in kotlin") {
            val pattern = square2.range(-10.0, 10.0)
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("square2 compiled") {
            val pattern = StrudelPattern.compile("square2")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square2 compiled with range") {
            val pattern = StrudelPattern.compile("square2.range(-10, 10)")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (-10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
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

    "perlin oscillator: same seed produces same values" {
        val p1 = perlin.seed(42)
        val p2 = perlin.seed(42)

        val val1 = p1.queryArc(0.5, 0.6)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.5, 0.6)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "perlin oscillator: different seeds produce different values" {
        val p1 = perlin.seed(1)
        val p2 = perlin.seed(2)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "perlin oscillator: output range in DSL is 0.0 to 1.0" {
        val p = perlin.seed(55)
        var min = 1.0
        var max = 0.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.5
                // Should be strictly within 0..1 (unipolar)
                v.shouldBeBetween(0.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range (it's not stuck at 0.5)
        min.shouldBeBetween(0.0, 0.35, 0.0)
        max.shouldBeBetween(0.65, 1.0, 0.0)
    }

    "perlin2 oscillator: same seed produces same values" {
        val p1 = perlin2.seed(42)
        val p2 = perlin2.seed(42)

        val val1 = p1.queryArc(0.5, 0.6)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.5, 0.6)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "perlin2 oscillator: different seeds produce different values" {
        val p1 = perlin2.seed(1)
        val p2 = perlin2.seed(2)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "perlin2 oscillator: output range in DSL is -1.0 to 1.0" {
        val p = perlin2.seed(55)
        var min = 1.0
        var max = -1.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.0
                // Should be strictly within -1..1 (bipolar)
                v.shouldBeBetween(-1.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range
        min.shouldBeBetween(-1.0, -0.3, 0.0)
        max.shouldBeBetween(0.3, 1.0, 0.0)
    }

    "berlin oscillator: same seed produces same values" {
        val p1 = berlin.seed(99)
        val p2 = berlin.seed(99)

        val val1 = p1.queryArc(0.7, 0.8)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.7, 0.8)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "berlin oscillator: different seeds produce different values" {
        val p1 = berlin.seed(1)
        val p2 = berlin.seed(2)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "berlin oscillator: stability across multiple queries" {
        val p = berlin.seed(123)

        // Query same point twice in separate query cycles
        val val1 = p.queryArc(1.5, 1.6)[0].data.value?.asDouble
        val val2 = p.queryArc(1.5, 1.6)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "berlin oscillator: output range in DSL is 0.0 to 1.0" {
        val p = berlin.seed(66)
        var min = 1.0
        var max = 0.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.5
                // Should be strictly within 0..1 (unipolar)
                v.shouldBeBetween(0.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range
        min.shouldBeBetween(0.0, 0.35, 0.0)
        max.shouldBeBetween(0.65, 1.0, 0.0)
    }

    "berlin2 oscillator: same seed produces same values" {
        val p1 = berlin2.seed(99)
        val p2 = berlin2.seed(99)

        val val1 = p1.queryArc(0.7, 0.8)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.7, 0.8)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "berlin2 oscillator: different seeds produce different values" {
        val p1 = berlin2.seed(1)
        val p2 = berlin2.seed(2)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "berlin2 oscillator: output range in DSL is -1.0 to 1.0" {
        val p = berlin2.seed(66)
        var min = 1.0
        var max = -1.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.0
                // Should be strictly within -1..1 (bipolar)
                v.shouldBeBetween(-1.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range
        min.shouldBeBetween(-1.0, -0.3, 0.0)
        max.shouldBeBetween(0.3, 1.0, 0.0)
    }

    "rangex() with exponential scaling" {
        withClue("rangex in kotlin") {
            // rangex uses exponential scaling: log(min) to log(max)
            val pattern = sine.rangex(100.0, 1000.0)
            val events = pattern.queryArc(0.0, 0.0 + EPSILON)

            // At phase 0, sine is 0.5, which should map to geometric mean
            val expected = kotlin.math.sqrt(100.0 * 1000.0) // ~316.2
            events[0].data.value?.asDouble shouldBe (expected plusOrMinus 1.0)
        }

        withClue("rangex compiled") {
            val pattern = StrudelPattern.compile("sine.rangex(100, 1000)")!!
            val events = pattern.queryArc(0.0, 0.0 + EPSILON)

            val expected = kotlin.math.sqrt(100.0 * 1000.0)
            events[0].data.value?.asDouble shouldBe (expected plusOrMinus 1.0)
        }
    }

    "range2() with bipolar input" {
        withClue("range2 in kotlin") {
            // sine2 goes from -1 to 1, range2 should scale it to 0-100
            val pattern = sine2.range2(0.0, 100.0)

            // At phase 0, sine2 is 0 (middle), should map to 50
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            // At phase 0.25, sine2 is 1, should map to 100
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            // At phase 0.75, sine2 is -1, should map to 0
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("range2 compiled") {
            val pattern = StrudelPattern.compile("sine2.range2(500, 4000)")!!

            // At phase 0, sine2 is 0, should map to middle value 2250
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (2250.0 plusOrMinus EPSILON)
        }
    }

    "round() rounds values to nearest integer" {
        withClue("round in kotlin") {
            val p = seq("0.5 1.5 2.5").round()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0  // 0.5 rounds to 0 (banker's rounding may vary)
            events[1].data.value?.asInt shouldBe 2  // 1.5 rounds to 2
            events[2].data.value?.asInt shouldBe 2  // 2.5 rounds to 2
        }

        withClue("round with string extension") {
            val p = "0.4 1.6 2.3".round()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 2
            events[2].data.value?.asInt shouldBe 2
        }

        withClue("round compiled") {
            val p = StrudelPattern.compile("""seq("0.3 1.7 2.5").round()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 2
        }
    }

    "floor() floors values to lower integer" {
        withClue("floor in kotlin") {
            val p = seq("0.9 1.1 2.9").floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 1
            events[2].data.value?.asInt shouldBe 2
        }

        withClue("floor with negative numbers") {
            val p = seq("-1.5 -0.5 0.5 1.5").floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 4
            events[0].data.value?.asInt shouldBe -2  // floor(-1.5) = -2
            events[1].data.value?.asInt shouldBe -1  // floor(-0.5) = -1
            events[2].data.value?.asInt shouldBe 0
            events[3].data.value?.asInt shouldBe 1
        }

        withClue("floor with string extension") {
            val p = "42.1 42.5 43".floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 42
            events[1].data.value?.asInt shouldBe 42
            events[2].data.value?.asInt shouldBe 43
        }

        withClue("floor compiled") {
            val p = StrudelPattern.compile("""seq("42.7 43.2").floor()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 2
            events[0].data.value?.asInt shouldBe 42
            events[1].data.value?.asInt shouldBe 43
        }
    }

    "ceil() ceils values to higher integer" {
        withClue("ceil in kotlin") {
            val p = seq("0.1 1.1 2.9").ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 1
            events[1].data.value?.asInt shouldBe 2
            events[2].data.value?.asInt shouldBe 3
        }

        withClue("ceil with negative numbers") {
            val p = seq("-1.5 -0.5 0.5 1.5").ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 4
            events[0].data.value?.asInt shouldBe -1  // ceil(-1.5) = -1
            events[1].data.value?.asInt shouldBe 0   // ceil(-0.5) = 0
            events[2].data.value?.asInt shouldBe 1
            events[3].data.value?.asInt shouldBe 2
        }

        withClue("ceil with string extension") {
            val p = "42.1 42.5 43".ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 43
            events[1].data.value?.asInt shouldBe 43
            events[2].data.value?.asInt shouldBe 43
        }

        withClue("ceil compiled") {
            val p = StrudelPattern.compile("""seq("42.2 43.8").ceil()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 2
            events[0].data.value?.asInt shouldBe 43
            events[1].data.value?.asInt shouldBe 44
        }
    }

})
