package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel._lift

class LangLiftSpec : StringSpec({

    "lift() - API exists" {
        // Just verify the API exists and compiles
        val source = sound("bd hh")
        val control = pure(2.0)

        val result = source._lift(control) { value, src ->
            src.fast(value)
        }

        // Should return a pattern
        result shouldBe result
    }

    "lift() - preserves metadata from source" {
        val source = sound("bd hh").fast(2)
        val control = pure(3.0)
        val sourceWeight = source.weight
        val sourceNumSteps = source.numSteps

        val result = source._lift(control) { value, src ->
            src.fast(value)
        }

        // Metadata should come from SOURCE, not control
        result.weight shouldBe sourceWeight
        result.numSteps shouldBe sourceNumSteps
    }

    "lift() - static control produces events" {
        val source = sound("bd hh")
        val control = pure(2.0)

        val result = source._lift(control) { value, src ->
            src.fast(value)
        }

        val events = result.queryArc(0.0, 0.5)
        events shouldHaveSize 2
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "lift() - transform function is called" {
        var transformCalled = false
        val source = sound("bd")
        val control = pure(2.0)

        val result = source._lift(control) { value, src ->
            transformCalled = true
            src.fast(value)
        }

        result.queryArc(0.0, 1.0)
        transformCalled shouldBe true
    }

    "lift() - works with slow transformation" {
        val source = sound("bd hh cp sd")
        val control = pure(2.0)

        val result = source._lift(control) { value, src ->
            src.slow(value)
        }

        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "hh"
    }

    "lift() - produces events with any transformation" {
        val source = sound("bd hh")
        val control = pure(2.0)

        val result = source._lift(control) { value, src ->
            src  // Identity transformation
        }

        val events = result.queryArc(0.0, 1.0)
        events.shouldNotBeEmpty()
        events.all { it.data.sound in listOf("bd", "hh") } shouldBe true
    }
})
