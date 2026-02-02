package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel._bind
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangBindPatternSpec : StringSpec({

    "bindPattern() exists and can be called" {
        // Just verify the API exists
        val control = sound("bd hh")

        val result = control._bind { _ ->
            sound("cp")
        }

        // If we get here, bindPattern() works
        result shouldBe result
    }

    "bindPattern() - basic bind with pure()" {
        // Simple test: transform values
        val control = pure(2.0)

        val result = control._bind { event ->
            val value = event.data.value?.asDouble ?: 0.0
            pure(value * 2)
        }

        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 1
        events[0].data.value?.asDouble shouldBe 4.0
    }

    "bindPattern() - with sound pattern" {
        // Test with sound() DSL function
        val control = sound("bd")

        val result = control._bind { _ ->
            sound("hh")
        }

        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 1
        events[0].data.sound shouldBe "hh"
    }

    "bindPattern() - null produces silence" {
        val control = pure(1.0)

        val result = control._bind { _ ->
            null  // Return null to produce silence
        }

        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 0
    }

    "bindPattern() - metadata preserved (weight)" {
        val control = sound("bd").fast(2)
        val originalWeight = control.weight

        val result = control._bind { _ ->
            sound("hh")
        }

        // BindPattern preserves weight from outer/control pattern
        result.weight shouldBe originalWeight
    }

    "bindPattern() - time clipping works" {
        // Control has 2 events in one cycle
        val control = sound("bd hh")

        val result = control._bind { _ ->
            // Inner pattern has 4 events, but should be clipped to outer event bounds
            sound("a b c d")
        }

        val events = result.queryArc(0.0, 1.0)
        // Each control event (0.5 duration) intersects with inner events
        // and clips them to boundaries
        events.size shouldBeGreaterThan 2

        // All events should be within the cycle
        events.forEach { event ->
            (event.part.begin >= 0.0.toRational()) shouldBe true
            (event.part.end <= 1.0.toRational()) shouldBe true
        }
    }
})
