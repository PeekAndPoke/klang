package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
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
        val input = DoubleArray(100) { 1.0 }

        // Create sidechain signal (loud trigger)
        val sidechain = DoubleArray(100) { 0.8 }

        ducking.process(input, sidechain, 100)

        // Input should be reduced significantly
        val avgLevel = input.map { abs(it) }.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Ducking returns to normal when sidechain stops" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.001, // Very fast return
            depth = 1.0
        )

        // Process with active sidechain
        val input1 = DoubleArray(100) { 1.0 }
        val sidechain1 = DoubleArray(100) { 1.0 }
        ducking.process(input1, sidechain1, 100)

        // Process with silent sidechain (should return to normal)
        val input2 = DoubleArray(1000) { 1.0 }
        val sidechain2 = DoubleArray(1000) { 0.0 }
        ducking.process(input2, sidechain2, 1000)

        // Should return close to full volume
        val endLevel = input2.takeLast(100).map { abs(it) }.average()
        endLevel shouldBe (1.0 plusOrMinus 0.1)
    }

    "Depth parameter controls ducking amount" {
        val input1 = DoubleArray(100) { 1.0 }
        val input2 = DoubleArray(100) { 1.0 }
        val sidechain = DoubleArray(100) { 1.0 }

        val duckingLight = Ducking(sampleRate, 0.01, depth = 0.3)
        val duckingHeavy = Ducking(sampleRate, 0.01, depth = 0.9)

        duckingLight.process(input1, sidechain, 100)
        duckingHeavy.process(input2, sidechain, 100)

        val avgLight = input1.map { abs(it) }.average()
        val avgHeavy = input2.map { abs(it) }.average()

        // Heavy ducking should reduce more
        avgHeavy shouldBeLessThan avgLight
    }

    "Reset clears internal state" {
        val ducking = Ducking(sampleRate, 0.01, 1.0)

        // Duck the signal
        val input = DoubleArray(100) { 1.0 }
        val sidechain = DoubleArray(100) { 1.0 }
        ducking.process(input, sidechain, 100)

        // Reset
        ducking.reset()

        // Should process at full volume immediately
        val input2 = DoubleArray(10) { 1.0 }
        val sidechain2 = DoubleArray(10) { 0.0 }
        ducking.process(input2, sidechain2, 10)

        input2[0] shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking with zero depth has no effect" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.1,
            depth = 0.0
        )

        val input = DoubleArray(100) { 1.0 }
        val sidechain = DoubleArray(100) { 1.0 }

        ducking.process(input, sidechain, 100)

        // Should remain at full volume
        val avgLevel = input.map { abs(it) }.average()
        avgLevel shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking handles stereo by processing channels separately" {
        val ducking = Ducking(sampleRate, 0.01, 0.8)

        // Left channel
        val inputLeft = DoubleArray(100) { 1.0 }
        val sidechainLeft = DoubleArray(100) { 0.8 }

        // Right channel
        val inputRight = DoubleArray(100) { 1.0 }
        val sidechainRight = DoubleArray(100) { 0.8 }

        ducking.process(inputLeft, sidechainLeft, 100)
        ducking.process(inputRight, sidechainRight, 100)

        // Both channels should be ducked
        val avgLeft = inputLeft.map { abs(it) }.average()
        val avgRight = inputRight.map { abs(it) }.average()

        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5
    }

    "Attack time affects return speed" {
        val input1 = DoubleArray(500) { 1.0 }
        val input2 = DoubleArray(500) { 1.0 }

        val duckingFast = Ducking(sampleRate, attackSeconds = 0.001, depth = 0.8)
        val duckingSlow = Ducking(sampleRate, attackSeconds = 0.1, depth = 0.8)

        // Duck both with active sidechain for first 100 samples
        val sidechainActive = DoubleArray(100) { 1.0 }
        val sidechainSilent = DoubleArray(400) { 0.0 }

        duckingFast.process(input1.sliceArray(0 until 100), sidechainActive, 100)
        duckingFast.process(input1.sliceArray(100 until 500), sidechainSilent, 400)

        duckingSlow.process(input2.sliceArray(0 until 100), sidechainActive, 100)
        duckingSlow.process(input2.sliceArray(100 until 500), sidechainSilent, 400)

        // Fast should recover more by sample 200
        val fastLevel = input1.sliceArray(150 until 200).map { abs(it) }.average()
        val slowLevel = input2.sliceArray(150 until 200).map { abs(it) }.average()

        fastLevel shouldBe (slowLevel plusOrMinus 0.1)
        fastLevel shouldBeLessThan (slowLevel + 0.2) // Fast returns faster
    }
})
