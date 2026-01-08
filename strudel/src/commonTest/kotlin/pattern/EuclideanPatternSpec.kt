package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

/**
 * Ok there is something wrong with the implementation of the EuclideanPattern.
 *
 * When i play this pattern in strudel.cc:
 *
 * `note("[a b c d e f g]/8(3,8)")`
 *
 * then I hear each note in the 3/8 rhythm for the full cycle.
 *
 * In our implementation I only ever hear the first note "a" and it repeats.
 */

class EuclideanPatternSpec : StringSpec({

    "EuclideanPattern: Direct Instantiation (3 pulses in 8 steps)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // bd(3,8) should result in beats at indices 0, 3, 6
        val pattern = EuclideanPattern(inner, pulses = 3, steps = 8)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3

        // Step size is 1/8 = 0.125
        // Pulse 1 at step 0
        events[0].begin shouldBe (0.0 plusOrMinus 1e-9)
        events[0].dur shouldBe (0.125 plusOrMinus 1e-9)

        // Pulse 2 at step 3 (0.375)
        events[1].begin shouldBe (0.375 plusOrMinus 1e-9)
        events[1].dur shouldBe (0.125 plusOrMinus 1e-9)

        // Pulse 3 at step 6 (0.75)
        events[2].begin shouldBe (0.75 plusOrMinus 1e-9)
        events[2].dur shouldBe (0.125 plusOrMinus 1e-9)
    }

    "EuclideanPattern: Kotlin DSL / Mini-notation (3,8)" {
        // Mini-notation "bd(3,8)"
        val pattern = note("bd(3,8)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].begin shouldBe (0.0 plusOrMinus 1e-9)
        events[1].begin shouldBe (0.375 plusOrMinus 1e-9)
        events[2].begin shouldBe (0.75 plusOrMinus 1e-9)
    }

    "EuclideanPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("bd(2,4)")""")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        // (2,4) is [1, 0, 1, 0] -> beats at 0.0 and 0.5
        events.size shouldBe 2
        events[0].begin shouldBe (0.0 plusOrMinus 1e-9)
        events[1].begin shouldBe (0.5 plusOrMinus 1e-9)
    }

    "EuclideanPattern: Complex inner pattern [a b](1,2)" {
        // [a b] takes 1 cycle normally.
        // In Euclidean(1,2), it is squeezed into the first 0.5 cycles.
        // a should be 0.0..0.25, b should be 0.25..0.5
        val pattern = note("[a b](1,2)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "a"
        events[0].begin shouldBe (0.0 plusOrMinus 1e-9)
        events[0].dur shouldBe (0.25 plusOrMinus 1e-9)

        events[1].data.note shouldBe "b"
        events[1].begin shouldBe (0.25 plusOrMinus 1e-9)
        events[1].dur shouldBe (0.25 plusOrMinus 1e-9)
    }

    "EuclideanPattern: weight preservation" {
        // Create a pattern with weight 2
        val inner = note("a@2")
        // Wrap in Euclidean
        val pattern = EuclideanPattern(inner, 3, 8)

        // It should still have weight 2
        pattern.weight shouldBe (2.0 plusOrMinus 1e-9)
    }
})
