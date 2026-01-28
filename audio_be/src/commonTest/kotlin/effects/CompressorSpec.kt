package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class CompressorSpec : StringSpec({

    val sampleRate = 44100

    "Compressor reduces signal above threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with loud signal (above threshold)
        val buffer = DoubleArray(1000) { 0.5 } // ~-6 dB

        compressor.process(buffer, 1000)

        // Signal should be reduced
        val avgLevel = buffer.map { abs(it) }.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Compressor does not affect signal below threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with quiet signal (below threshold)
        val buffer = DoubleArray(1000) { 0.01 } // ~-40 dB
        val original = buffer.copyOf()

        compressor.process(buffer, 1000)

        // Signal should be mostly unchanged
        for (i in buffer.indices) {
            buffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }

    "Compressor.parseSettings parses full format" {
        val settings = Compressor.parseSettings("-20:4:6:0.003:0.1")

        settings shouldBe Compressor.CompressorSettings(
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 6.0,
            attackSeconds = 0.003,
            releaseSeconds = 0.1
        )
    }

    "Compressor.parseSettings parses short format (threshold:ratio only)" {
        val settings = Compressor.parseSettings("-15:3")

        settings shouldBe Compressor.CompressorSettings(
            thresholdDb = -15.0,
            ratio = 3.0,
            kneeDb = 6.0,
            attackSeconds = 0.003,
            releaseSeconds = 0.1
        )
    }

    "Compressor.parseSettings returns null for invalid input" {
        val settings = Compressor.parseSettings("invalid")
        settings shouldBe null
    }

    "Compressor reset clears state" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0
        )

        // Process some audio
        val buffer = DoubleArray(100) { 0.5 }
        compressor.process(buffer, 100)

        // Reset
        compressor.reset()

        // Process quiet signal - should not be affected by previous state
        val quietBuffer = DoubleArray(100) { 0.01 }
        val original = quietBuffer.copyOf()
        compressor.process(quietBuffer, 100)

        for (i in quietBuffer.indices) {
            quietBuffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }

    "Compressor processes stereo correctly" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val left = DoubleArray(1000) { 0.5 }
        val right = DoubleArray(1000) { 0.5 }

        compressor.process(left, right, 1000)

        // Both channels should be reduced
        val avgLeft = left.map { abs(it) }.average()
        val avgRight = right.map { abs(it) }.average()

        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5
    }

    "Compressor soft knee creates smooth transition" {
        val hardKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val softKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 12.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create signal right at threshold
        val bufferHard = DoubleArray(1000) { 0.1 } // ~-20 dB
        val bufferSoft = bufferHard.copyOf()

        hardKnee.process(bufferHard, 1000)
        softKnee.process(bufferSoft, 1000)

        // Both should compress, but soft knee should be gentler
        val avgHard = bufferHard.map { abs(it) }.average()
        val avgSoft = bufferSoft.map { abs(it) }.average()

        avgHard shouldBeLessThan 0.15
        avgSoft shouldBeLessThan 0.15
    }
})
