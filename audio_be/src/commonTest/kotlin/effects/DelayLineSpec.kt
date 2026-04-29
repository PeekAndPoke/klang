package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.abs

class DelayLineSpec : StringSpec({

    val sampleRate = 44100
    val blockSize = 512

    fun processBlocks(delay: DelayLine, send: StereoBuffer, output: StereoBuffer, blocks: Int) {
        repeat(blocks) {
            // Only send the impulse on the first block; clear send for subsequent blocks
            if (it > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)
        }
    }

    "impulse appears in output after correct delay time" {
        val delaySeconds = 0.01 // 10ms
        val delaySamples = (delaySeconds * sampleRate).toInt() // 441 samples
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = delaySeconds
        delay.feedback = 0.0

        // We need enough blocks to cover the delay
        val totalBlocks = (delaySamples / blockSize) + 2

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        // Place impulse at sample 0 of the first block
        send.left[0] = 1.0
        send.right[0] = 1.0

        var foundLeft = false
        var impulseBlockIndex = -1
        var impulseSampleIndex = -1

        for (block in 0 until totalBlocks) {
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            for (i in 0 until blockSize) {
                if (!foundLeft && abs(output.left[i]) > 0.5) {
                    foundLeft = true
                    impulseBlockIndex = block
                    impulseSampleIndex = i
                }
            }
        }

        foundLeft shouldBe true

        // The impulse should appear at approximately delaySamples offset
        val actualDelaySamples = impulseBlockIndex * blockSize + impulseSampleIndex
        val tolerance = 2 // allow small rounding from interpolation
        (abs(actualDelaySamples - delaySamples) <= tolerance) shouldBe true
    }

    "feedback causes decaying repeats" {
        val delaySeconds = 0.01
        val delaySamples = (delaySeconds * sampleRate).toInt()
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = delaySeconds
        delay.feedback = 0.5

        // Collect peak amplitudes for multiple repeats
        val totalBlocks = ((delaySamples * 5) / blockSize) + 2
        val peaks = mutableListOf<Double>()
        var currentPeak = 0.0
        var samplesSinceLastPeak = 0
        var totalSamples = 0

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        send.left[0] = 1.0
        send.right[0] = 1.0

        for (block in 0 until totalBlocks) {
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            for (i in 0 until blockSize) {
                val sample = abs(output.left[i])
                if (sample > currentPeak) {
                    currentPeak = sample
                }
                samplesSinceLastPeak++
                totalSamples++

                // Check at each delay period
                if (totalSamples % delaySamples == 0 && currentPeak > 0.001) {
                    peaks.add(currentPeak)
                    currentPeak = 0.0
                    samplesSinceLastPeak = 0
                }
            }
        }

        // We should have at least 2 peaks, and each should be smaller than the previous
        (peaks.size >= 2) shouldBe true
        for (i in 1 until peaks.size) {
            peaks[i].toDouble() shouldBeLessThan peaks[i - 1].toDouble()
        }
    }

    "zero feedback produces single repeat only" {
        val delaySeconds = 0.01
        val delaySamples = (delaySeconds * sampleRate).toInt()
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = delaySeconds
        delay.feedback = 0.0

        // Process enough blocks for 3x the delay time
        val totalBlocks = ((delaySamples * 3) / blockSize) + 2
        var peakCount = 0
        var lastNonZeroBlock = -1

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        send.left[0] = 1.0
        send.right[0] = 1.0

        for (block in 0 until totalBlocks) {
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            for (i in 0 until blockSize) {
                if (abs(output.left[i]) > 0.01) {
                    if (lastNonZeroBlock != block) {
                        peakCount++
                        lastNonZeroBlock = block
                    }
                }
            }
        }

        // With zero feedback we expect the impulse to appear only in one region
        (peakCount <= 2) shouldBe true // may span 2 adjacent blocks at the boundary
    }

    "safety clamp prevents values exceeding 2.0 with high feedback" {
        val delaySeconds = 0.005
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = delaySeconds
        delay.feedback = 1.5 // Unstable feedback — would explode without clamping

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        // Send a loud impulse
        send.left[0] = 1.0
        send.right[0] = 1.0

        // Process many blocks so feedback has time to accumulate
        for (block in 0 until 200) {
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            // Check that no output sample exceeds the safety limit of 2.0
            for (i in 0 until blockSize) {
                abs(output.left[i]) shouldBeLessThan 2.01
                abs(output.right[i]) shouldBeLessThan 2.01
            }
        }
    }

    "hasTail returns true when buffer has content" {
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = 0.1
        delay.feedback = 0.5

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        send.left[0] = 1.0
        send.right[0] = 1.0

        delay.process(send, output, blockSize)

        delay.hasTail() shouldBe true
    }

    "hasTail returns false when buffer is silent" {
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = 0.1
        delay.feedback = 0.0

        // Never send any signal
        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        delay.hasTail() shouldBe false
    }

    "very short delay below 1ms works without crash" {
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = 0.0005 // 0.5ms — flanger range
        delay.feedback = 0.3

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        send.left[0] = 1.0
        send.right[0] = 1.0

        // Should not throw
        repeat(10) { block ->
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)
        }

        // Verify some output was produced
        var hasOutput = false
        send.clear()
        output.clear()

        // Re-send impulse and check immediate area
        val freshSend = StereoBuffer(blockSize)
        freshSend.left[0] = 1.0
        val freshOutput = StereoBuffer(blockSize)
        delay.process(freshSend, freshOutput, blockSize)

        for (i in 0 until blockSize) {
            if (abs(freshOutput.left[i]) > 0.001) {
                hasOutput = true
                break
            }
        }
        hasOutput shouldBe true
    }

    "delay time change is smooth - no crash on parameter change" {
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = 0.1
        delay.feedback = 0.4

        val send = StereoBuffer(blockSize)
        val output = StereoBuffer(blockSize)

        send.left[0] = 1.0
        send.right[0] = 1.0

        // Process a few blocks, then change delay time mid-stream
        repeat(5) { block ->
            if (block > 0) send.clear()
            output.clear()
            delay.process(send, output, blockSize)
        }

        // Change delay time dramatically
        delay.delayTimeSeconds = 0.5

        repeat(5) { block ->
            send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            // Verify no NaN or Inf values
            for (i in 0 until blockSize) {
                (output.left[i].isNaN()) shouldBe false
                (output.left[i].isInfinite()) shouldBe false
                (output.right[i].isNaN()) shouldBe false
                (output.right[i].isInfinite()) shouldBe false
            }
        }

        // Change to a very short delay
        delay.delayTimeSeconds = 0.001

        repeat(5) { block ->
            send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            for (i in 0 until blockSize) {
                (output.left[i].isNaN()) shouldBe false
                (output.left[i].isInfinite()) shouldBe false
            }
        }
    }
})
