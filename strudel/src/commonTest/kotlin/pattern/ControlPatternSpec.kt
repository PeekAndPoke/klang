package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.gain
import io.peekandpoke.klang.strudel.lang.note

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
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
        }

        val events = pattern.queryArc(0.0, 1.0)
        events[0].data.gain shouldBe (0.7 plusOrMinus EPSILON)
    }

    "ControlPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("c3").gain(0.2)""")

        verifyPattern(pattern, 1) { _, note, _, _ ->
            note shouldBe "c3"
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
})
