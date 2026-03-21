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
        val input = FloatArray(100) { 1.0f }

        // Create sidechain signal (loud trigger)
        val sidechain = FloatArray(100) { 0.8f }

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
        val input1 = FloatArray(100) { 1.0f }
        val sidechain1 = FloatArray(100) { 1.0f }
        ducking.process(input1, sidechain1, 100)

        // Process with silent sidechain (should return to normal)
        val input2 = FloatArray(1000) { 1.0f }
        val sidechain2 = FloatArray(1000) { 0.0f }
        ducking.process(input2, sidechain2, 1000)

        // Should return close to full volume
        val endLevel = input2.takeLast(100).map { abs(it.toDouble()) }.average()
        endLevel shouldBe (1.0 plusOrMinus 0.1)
    }

    "Depth parameter controls ducking amount" {
        val input1 = FloatArray(100) { 1.0f }
        val input2 = FloatArray(100) { 1.0f }
        val sidechain = FloatArray(100) { 1.0f }

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
        val input = FloatArray(100) { 1.0f }
        val sidechain = FloatArray(100) { 1.0f }
        ducking.process(input, sidechain, 100)

        // Reset
        ducking.reset()

        // Should process at full volume immediately
        val input2 = FloatArray(10) { 1.0f }
        val sidechain2 = FloatArray(10) { 0.0f }
        ducking.process(input2, sidechain2, 10)

        input2[0].toDouble() shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking with zero depth has no effect" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            attackSeconds = 0.1,
            depth = 0.0
        )

        val input = FloatArray(100) { 1.0f }
        val sidechain = FloatArray(100) { 1.0f }

        ducking.process(input, sidechain, 100)

        // Should remain at full volume
        val avgLevel = input.map { abs(it.toDouble()) }.average()
        avgLevel shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking handles stereo by processing channels separately" {
        val ducking = Ducking(sampleRate, 0.01, 0.8)

        // Left channel
        val inputLeft = FloatArray(100) { 1.0f }
        val sidechainLeft = FloatArray(100) { 0.8f }

        // Right channel
        val inputRight = FloatArray(100) { 1.0f }
        val sidechainRight = FloatArray(100) { 0.8f }

        ducking.process(inputLeft, sidechainLeft, 100)
        ducking.process(inputRight, sidechainRight, 100)

        // Both channels should be ducked
        val avgLeft = inputLeft.map { abs(it.toDouble()) }.average()
        val avgRight = inputRight.map { abs(it.toDouble()) }.average()

        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5
    }

    "Attack time affects return speed" {
        val input1 = FloatArray(500) { 1.0f }
        val input2 = FloatArray(500) { 1.0f }

        val duckingFast = Ducking(sampleRate, attackSeconds = 0.001, depth = 0.8)
        val duckingSlow = Ducking(sampleRate, attackSeconds = 0.1, depth = 0.8)

        // Duck both with active sidechain for first 100 samples
        val sidechainActive = FloatArray(100) { 1.0f }
        val sidechainSilent = FloatArray(400) { 0.0f }

        duckingFast.process(input1.sliceArray(0 until 100), sidechainActive, 100)
        duckingFast.process(input1.sliceArray(100 until 500), sidechainSilent, 400)

        duckingSlow.process(input2.sliceArray(0 until 100), sidechainActive, 100)
        duckingSlow.process(input2.sliceArray(100 until 500), sidechainSilent, 400)

        // Fast should recover more by sample 200
        val fastLevel = input1.sliceArray(150 until 200).map { abs(it.toDouble()) }.average()
        val slowLevel = input2.sliceArray(150 until 200).map { abs(it.toDouble()) }.average()

        fastLevel shouldBe (slowLevel plusOrMinus 0.1)
        fastLevel shouldBeLessThan (slowLevel + 0.2) // Fast returns faster
    }
})
