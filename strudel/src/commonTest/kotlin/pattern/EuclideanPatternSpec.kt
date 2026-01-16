package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

/**
 * Tests for EuclideanPattern ensuring compatibility with Strudel JS logic.
 */
class EuclideanPatternSpec : StringSpec({

    "EuclideanPattern: Direct Instantiation (3 pulses in 8 steps)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // bd(3,8) should result in beats at indices 0, 3, 6 (steps: 1, 0, 0, 1, 0, 0, 1, 0)
        val pattern = EuclideanPattern.create(inner, pulses = 3, steps = 8, rotation = 0)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3

        // Step size is 1/8 = 0.125
        // Pulse 1 at step 0
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // Pulse 2 at step 3 (3 * 0.125 = 0.375)
        events[1].begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // Pulse 3 at step 6 (6 * 0.125 = 0.75)
        events[2].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[2].dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation 1 (3,8,1)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (3,8) base is [1, 0, 0, 1, 0, 0, 1, 0]
        // Rotation logic in JS: rotate(b, -1) -> JS slice logic
        // slice(-1) gets last element [0], slice(0, -1) gets everything but last.
        // Result: [0, 1, 0, 0, 1, 0, 0, 1] -> beats at 1, 4, 7
        // Wait, checking JS output manually:
        // bjorklund(3,8) -> [1,0,0,1,0,0,1,0]
        // rotate(b, -1) -> b.slice(-1).concat(b.slice(0,-1)) -> [0].concat([1,0,0,1,0,0,1]) -> [0,1,0,0,1,0,0,1]
        // Indices: 1, 4, 7

        val pattern = EuclideanPattern.create(inner, pulses = 3, steps = 8, rotation = 1)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        // Pulse 1 at step 1: 0.125
        events[0].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        // Pulse 2 at step 4: 0.5
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        // Pulse 3 at step 7: 0.875
        events[2].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation equal to steps (1,2,2)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (1,2) base is [1, 0]
        // rotate(b, -2) -> slice(-2) is full array [1,0], slice(0, -2) is empty [].
        // Result [1, 0] -> Index 0

        val pattern = EuclideanPattern.create(inner, pulses = 1, steps = 2, rotation = 2)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation greater than steps (1,2,3)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (1,2) base is [1, 0] (length 2)
        // rotate(b, -3) -> slice(-3).concat(slice(0, -3))
        // slice(-3): -3 < -2, clamps to 0. Returns slice(0) -> [1, 0]
        // slice(0, -3): -3 clamps to 0. Returns slice(0, 0) -> []
        // Result: [1, 0]. Matches Graal output from issue report.

        val pattern = EuclideanPattern.create(inner, pulses = 1, steps = 2, rotation = 3)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        // Should start at 0.0, NOT 0.5
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Rotation large odd number (1,2,7)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // (1,2) base is [1, 0]
        // rotate(b, -7)
        // slice(-7) -> clamps to 0 -> [1, 0]
        // slice(0, -7) -> clamps to 0 -> []
        // Result: [1, 0]

        val pattern = EuclideanPattern.create(inner, pulses = 1, steps = 2, rotation = 7)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Negative Rotation (3,8,-1)" {
        // (3,8) base is [1, 0, 0, 1, 0, 0, 1, 0]
        // rotate(b, --1) -> rotate(b, 1)
        // slice(1) -> [0, 0, 1, 0, 0, 1, 0]
        // slice(0, 1) -> [1]
        // Result: [0, 0, 1, 0, 0, 1, 0, 1]
        // Indices: 2, 5, 7 (Same as rotation=1 test earlier? No wait)
        // Earlier rotation 1 was rotate(b, -1).
        // This is rotate(b, 1).

        // Strudel _euclidRot calls rotate(b, -rotation).
        // So rotation=-1 calls rotate(b, 1).
        // Result: [0,0,1,0,0,1,0] + [1] -> [0, 0, 1, 0, 0, 1, 0, 1]
        // Indices: 2, 5, 7.

        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        val pattern = EuclideanPattern.create(inner, pulses = 3, steps = 8, rotation = -1)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Negative Pulses (Inversion) (-3, 8)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        // bjorklund(3,8) -> [1, 0, 0, 1, 0, 0, 1, 0]
        // Inverted: [0, 1, 1, 0, 1, 1, 0, 1]
        // Indices: 1, 2, 4, 5, 7

        val pattern = EuclideanPattern.create(inner, pulses = -3, steps = 8, rotation = 0)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 5
        events[0].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[3].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        events[4].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Zero Pulses (0, 8)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        val pattern = EuclideanPattern.create(inner, pulses = 0, steps = 8, rotation = 0)
        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "EuclideanPattern: Full Pulses (8, 8)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        val pattern = EuclideanPattern.create(inner, pulses = 8, steps = 8, rotation = 0)
        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 8
    }

    "EuclideanPattern: Mini-notation with rotation (3,8,1)" {
        // Test that the parser correctly picks up the rotation
        val pattern = note("bd(3,8,1)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Same result as Rotation 1 test above: Indices 1, 4, 7
        // (3,8) -> [1,0,0,1,0,0,1,0]
        // rot 1 (calls rotate(-1)) -> [0,1,0,0,1,0,0,1]

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Compiled Code with rotation" {
        val pattern = StrudelPattern.compile("""note("bd(2,4,1)")""")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        // (2,4) is [1, 0, 1, 0]
        // rot 1 (calls rotate(-1)) -> [0].concat([1,0,1]) -> [0, 1, 0, 1]
        // Beats at 1 (0.25) and 3 (0.75)
        events.size shouldBe 2
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Overlapping inner pattern - only first event will be played [a b](1,2)" {
        val pattern = note("[a b](1,2)")

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "EuclideanPattern: weight preservation" {
        val inner = note("a@2")
        val pattern = EuclideanPattern.create(inner, 3, 8, 0)

        pattern.weight shouldBe (2.0 plusOrMinus EPSILON)
    }

    "EuclideanPattern: Invalid Steps (3, 0)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "bd"))
        val pattern = EuclideanPattern.create(inner, pulses = 3, steps = 0, rotation = 0)

        // Should fall back to inner pattern
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "bd"
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }
})
