package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
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
        val pattern = EuclideanPattern(inner, pulses = 3, steps = 8, rotation = 0)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3

        // Step size is 1/8 = 0.125
        // Pulse 1 at step 0
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // Pulse 2 at step 3 (0.375)
        events[1].begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // Pulse 3 at step 6 (0.75)
        events[2].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[2].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation 1 (3,8,1)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (3,8) is [1, 0, 0, 1, 0, 0, 1, 0]
        // Rotated 1 (Shift left): [0, 0, 1, 0, 0, 1, 0, 1]
        // Pulses at steps 2, 5, 7
        val pattern = EuclideanPattern(inner, pulses = 3, steps = 8, rotation = 1)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        // Pulse 1: 2 * 0.125 = 0.25
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        // Pulse 2: 5 * 0.125 = 0.625
        events[1].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        // Pulse 3: 7 * 0.125 = 0.875
        events[2].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation 2 (3,8,2)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (3,8) is [1, 0, 0, 1, 0, 0, 1, 0]
        // Rotated 2 (Shift left): [0, 1, 0, 0, 1, 0, 1, 0]
        // Pulses at steps 1, 4, 6
        val pattern = EuclideanPattern(inner, pulses = 3, steps = 8, rotation = 2)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Mini-notation with rotation (3,8,1)" {
        // Test that the parser correctly picks up the rotation
        val pattern = note("bd(3,8,1)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Compiled Code with rotation" {
        val pattern = StrudelPattern.compile("""note("bd(2,4,1)")""")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        // (2,4) is [1, 0, 1, 0]
        // Rotated 1: [0, 1, 0, 1] -> beats at 0.25 and 0.75
        events.size shouldBe 2
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Overlapping inner pattern - only first event will be played [a b](1,2)" {
        val pattern = note("[a b](1,2)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "EuclideanPattern: weight preservation" {
        val inner = note("a@2")
        val pattern = EuclideanPattern(inner, 3, 8, 0)

        pattern.weight shouldBe (2.0 plusOrMinus EPSILON)
    }
})
