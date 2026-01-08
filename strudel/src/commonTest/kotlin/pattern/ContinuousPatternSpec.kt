package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.sine

class ContinuousPatternSpec : StringSpec({

    "ContinuousPattern: Direct Instantiation" {
        // Create a simple ramp pattern: 0 to 1 over one cycle
        val pattern = ContinuousPattern { t -> t % 1.0 }

        // Querying a continuous pattern returns exactly one event covering the queried arc
        val events = pattern.queryArc(0.25, 0.75)

        events.size shouldBe 1
        val event = events[0]

        event.begin shouldBe (0.25 plusOrMinus EPSILON)
        event.end shouldBe (0.75 plusOrMinus EPSILON)
        // The value is sampled at the 'from' time
        event.data.value shouldBe (0.25 plusOrMinus EPSILON)
    }

    "ContinuousPattern: Kotlin DSL (sine)" {
        val pattern = sine

        // sine is sin(t * 2 * PI)
        // at t=0.25, sin(0.5 * PI) = 1.0
        val events = pattern.queryArc(0.25, 0.5)

        events.size shouldBe 1
        events[0].data.value shouldBe (1.0 plusOrMinus EPSILON)
    }

    "ContinuousPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("sine")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.5, 1.0)

        events.size shouldBe 1
        // at t=0.5, sin(PI) = 0.0
        events[0].data.value shouldBe (0.0 plusOrMinus EPSILON)
    }

    "ContinuousPattern: range mapping" {
        val base = ContinuousPattern { 0.5 } // Constant signal
        // range(min, max) maps bipoloar -1..1 to min..max
        // 0.5 is unipolar 0.75 ( since (0.5 + 1) / 2 = 0.75 )
        // range(0, 100) -> 0 + 0.75 * 100 = 75
        val mapped = base.range(0.0, 100.0)

        val events = mapped.queryArc(0.0, 1.0)
        events[0].data.value shouldBe (75.0 plusOrMinus EPSILON)
    }
})
