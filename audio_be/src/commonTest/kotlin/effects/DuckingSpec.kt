package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs

class DuckingSpec : StringSpec({
    val sampleRate = 44100

    "Ducking reduces volume when sidechain is active" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.01,
            depth = 0.8
        )

        // Create input signal (constant level)
        val input = AudioBuffer(100) { 1.0 }

        // Create sidechain signal (loud trigger)
        val sidechain = AudioBuffer(100) { 0.8 }

        ducking.process(input, sidechain, 100)

        // Input should be reduced significantly
        val avgLevel = input.map { abs(it.toDouble()) }.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Ducking returns to normal when sidechain stops" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.001, // Very fast return
            depth = 1.0
        )

        // Process with active sidechain
        val input1 = AudioBuffer(100) { 1.0 }
        val sidechain1 = AudioBuffer(100) { 1.0 }
        ducking.process(input1, sidechain1, 100)

        // Process with silent sidechain (should return to normal)
        val input2 = AudioBuffer(1000) { 1.0 }
        val sidechain2 = AudioBuffer(1000) { 0.0 }
        ducking.process(input2, sidechain2, 1000)

        // Should return close to full volume
        val endLevel = input2.takeLast(100).map { abs(it.toDouble()) }.average()
        endLevel shouldBe (1.0 plusOrMinus 0.1)
    }

    "Depth parameter controls ducking amount" {
        val input1 = AudioBuffer(100) { 1.0 }
        val input2 = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }

        val duckingLight = Ducking(sampleRate, 0.01, depth = 0.3)
        val duckingHeavy = Ducking(sampleRate, 0.01, depth = 0.9)

        duckingLight.process(input1, sidechain, 100)
        duckingHeavy.process(input2, sidechain, 100)

        val avgLight = input1.map { abs(it.toDouble()) }.average()
        val avgHeavy = input2.map { abs(it.toDouble()) }.average()

        // Heavy ducking should reduce more
        avgHeavy shouldBeLessThan avgLight
    }

    "Reset clears internal state" {
        val ducking = Ducking(sampleRate, 0.01, 1.0)

        // Duck the signal
        val input = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }
        ducking.process(input, sidechain, 100)

        // Reset
        ducking.reset()

        // Should process at full volume immediately
        val input2 = AudioBuffer(10) { 1.0 }
        val sidechain2 = AudioBuffer(10) { 0.0 }
        ducking.process(input2, sidechain2, 10)

        input2[0].toDouble() shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking with zero depth has no effect" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.1,
            depth = 0.0
        )

        val input = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }

        ducking.process(input, sidechain, 100)

        // Should remain at full volume
        val avgLevel = input.map { abs(it.toDouble()) }.average()
        avgLevel shouldBe (1.0 plusOrMinus 0.01)
    }

    "processStereo ducks both channels equally (linked stereo)" {
        val ducking = Ducking(sampleRate, 0.01, 0.8)

        val inputLeft = AudioBuffer(100) { 1.0 }
        val inputRight = AudioBuffer(100) { 1.0 }
        val sidechainLeft = AudioBuffer(100) { 0.8 }
        val sidechainRight = AudioBuffer(100) { 0.8 }

        ducking.processStereo(inputLeft, inputRight, sidechainLeft, sidechainRight, 100)

        val avgLeft = inputLeft.map { abs(it.toDouble()) }.average()
        val avgRight = inputRight.map { abs(it.toDouble()) }.average()

        // Both channels should be ducked
        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5

        // Both channels should have identical gain reduction (linked stereo)
        avgLeft shouldBe (avgRight plusOrMinus 0.001)
    }

    "processStereo preserves stereo image with asymmetric sidechain" {
        val ducking = Ducking(sampleRate, 0.01, 0.8)

        val inputLeft = AudioBuffer(100) { 1.0 }
        val inputRight = AudioBuffer(100) { 1.0 }
        // Sidechain is louder on left — but linked detection should apply equal ducking
        val sidechainLeft = AudioBuffer(100) { 0.9 }
        val sidechainRight = AudioBuffer(100) { 0.1 }

        ducking.processStereo(inputLeft, inputRight, sidechainLeft, sidechainRight, 100)

        val avgLeft = inputLeft.map { abs(it.toDouble()) }.average()
        val avgRight = inputRight.map { abs(it.toDouble()) }.average()

        // Both channels should have identical gain reduction despite asymmetric sidechain
        avgLeft shouldBe (avgRight plusOrMinus 0.001)
    }

    "Recovery time affects return speed" {
        // Use separate buffers for active/silent phases, process in-place
        val duckingFast = Ducking(sampleRate, attackSeconds = 0.001, depth = 0.8)
        val duckingSlow = Ducking(sampleRate, attackSeconds = 0.1, depth = 0.8)

        // Phase 1: Duck both with active sidechain
        val active1 = AudioBuffer(100) { 1.0 }
        val active2 = AudioBuffer(100) { 1.0 }
        val sidechainActive = AudioBuffer(100) { 1.0 }
        duckingFast.process(active1, sidechainActive, 100)
        duckingSlow.process(active2, sidechainActive, 100)

        // Phase 2: Release with silent sidechain
        val recover1 = AudioBuffer(400) { 1.0 }
        val recover2 = AudioBuffer(400) { 1.0 }
        val sidechainSilent = AudioBuffer(400) { 0.0 }
        duckingFast.process(recover1, sidechainSilent, 400)
        duckingSlow.process(recover2, sidechainSilent, 400)

        // Fast should recover more by sample 50-100 of the recovery phase
        val fastLevel = recover1.sliceArray(50 until 100).map { abs(it.toDouble()) }.average()
        val slowLevel = recover2.sliceArray(50 until 100).map { abs(it.toDouble()) }.average()

        // Fast recovery should be closer to 1.0 than slow recovery
        (fastLevel > slowLevel) shouldBe true
    }
})
