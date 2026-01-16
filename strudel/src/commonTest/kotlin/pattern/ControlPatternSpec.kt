package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.*

class ControlPatternSpec : StringSpec({

    "ControlPattern: Direct Instantiation" {
        val source = AtomicPattern(VoiceData.empty.copy(note = "c3"))
        val control = AtomicPattern(VoiceData.empty.copy(gain = 0.5))

        val pattern = ControlPattern(
            source = source,
            control = control,
            mapper = { it },
            combiner = { s, c -> s.copy(gain = c.gain) }
        )

        verifyPattern(pattern, 1) { _, note, begin, _ ->
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
        }

        // Weight should be delegated to source
        pattern.weight shouldBe source.weight

        val events = pattern.queryArc(0.0, 1.0)
        events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
    }

    "ControlPattern: Kotlin DSL (gain modifier)" {
        val pattern = note("c3").gain(0.7)

        verifyPattern(pattern, 1) { _, note, begin, _ ->
            note shouldBeEqualIgnoringCase "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
        }

        val events = pattern.queryArc(0.0, 1.0)
        events[0].data.gain shouldBe (0.7 plusOrMinus EPSILON)
    }

    "ControlPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("c3").gain(0.2)""")

        verifyPattern(pattern, 1) { _, note, _, _ ->
            note shouldBeEqualIgnoringCase "c3"
        }

        val events = pattern!!.queryArc(0.0, 1.0)
        events[0].data.gain shouldBe (0.2 plusOrMinus EPSILON)
    }

    "ControlPattern: Weight preservation" {
        // Create a pattern with weight 3
        val source = note("a@3")
        // Apply gain control
        val pattern = source.gain(0.5)

        // The resulting pattern should still have weight 3
        pattern.weight shouldBe (3.0 plusOrMinus EPSILON)
    }

    "ContinuousPattern: Oscillator ranges (0..1 by default)" {
        // Sine is normalized: (sin(0) + 1) / 2 = 0.5
        sine.queryArc(0.0, 1.0)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        // At t=0.25: (sin(π/2) + 1) / 2 = (1 + 1) / 2 = 1.0
        sine.queryArc(0.25, 0.5)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)

        // Saw starts at 0.0
        saw.queryArc(0.0, 1.0)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        // Saw at half cycle is 0.5
        saw.queryArc(0.5, 1.0)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)

        // Range mapping: mapping 0..1 to 10..20
        val rangedSine = sine.range(10.0, 20.0)
        // at t=0, sine is 0.5, so mapped value is 15.0
        rangedSine.queryArc(0.0, 1.0)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        // at t=0.25, sine is 1.0, so mapped value is 20.0
        rangedSine.queryArc(0.25, 0.5)[0].data.value?.asDouble shouldBe (20.0 plusOrMinus EPSILON)
    }
})
