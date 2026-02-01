package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.range
import io.peekandpoke.klang.strudel.lang.sine
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class ContinuousPatternSpec : StringSpec({

    "ContinuousPattern: Direct Instantiation" {
        // Create a simple ramp pattern: 0 to 1 over one cycle
        val pattern = ContinuousPattern { t -> t % 1.0 }

        // Querying a continuous pattern returns exactly one event covering the queried arc
        val events = pattern.queryArc(0.25.toRational(), 0.75.toRational())

        events.size shouldBe 1
        val event = events[0]

        event.part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        event.part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        // The value is sampled at the 'from' time
        event.data.value?.asDouble shouldBe (0.25 plusOrMinus EPSILON)
    }

    "ContinuousPattern: Kotlin DSL (sine)" {
        val pattern = sine

        // sine is (sin(t * 2 * PI) + 1) / 2
        // at t=0.25, (sin(0.5 * PI) + 1) / 2 = (1 + 1) / 2 = 1.0
        val events = pattern.queryArc(0.25.toRational(), 0.5.toRational())

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
    }

    "ContinuousPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("sine")

        pattern.shouldNotBeNull()
        // at t=0.5, (sin(PI) + 1) / 2 = (0 + 1) / 2 = 0.5
        val events = pattern.queryArc(0.5.toRational(), 1.0.toRational())

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
    }

    "ContinuousPattern: range mapping" {
        // base is unipolar 0..1 by default
        val base = ContinuousPattern { 0.5 }

        // mapping 0..1 to 0..100. 0.5 is exactly the middle.
        val mapped = base.range(0.0, 100.0)

        val events = mapped.queryArc(0.0.toRational(), 1.0.toRational())
        events[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
    }
})
