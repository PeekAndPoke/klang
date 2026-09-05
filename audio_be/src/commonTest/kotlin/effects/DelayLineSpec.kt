/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.abs

class DelayLineSpec : StringSpec({

    val sampleRate = 44100
    val blockSize = 512

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
            peaks[i] shouldBeLessThan peaks[i - 1]
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

        repeat(5) {
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

        repeat(5) {
            send.clear()
            output.clear()
            delay.process(send, output, blockSize)

            for (i in 0 until blockSize) {
                (output.left[i].isNaN()) shouldBe false
                (output.left[i].isInfinite()) shouldBe false
            }
        }
    }

    "drainSamplesUntilSilent: periods from the measured peak plus one slack period, in samples" {
        val dl = DelayLine(maxDelaySeconds = 10.0, sampleRate = 44100)
        dl.delayTimeSeconds = 0.05 // 2205 samples, no coercion

        // ceil(ln(1e-5 / 1) / ln(0.5)) = ceil(16.61) = 17 periods to cross the threshold, +1 slack.
        dl.feedback = 0.5
        dl.drainSamplesUntilSilent(peak = 1.0) shouldBe (18.0 * 2205.0)

        // Negative feedback decays by |fb| per period — same count.
        dl.feedback = -0.5
        dl.drainSamplesUntilSilent(peak = 1.0) shouldBe (18.0 * 2205.0)

        // A hotter ring drains longer: ceil(ln(1e-5 / 4) / ln(0.5)) = 19, +1.
        dl.feedback = 0.5
        dl.drainSamplesUntilSilent(peak = 4.0) shouldBe (20.0 * 2205.0)

        // A quieter ring drains SHORTER — the point of measuring instead of assuming the worst:
        // ceil(ln(1e-5 / 1e-3) / ln(0.5)) = ceil(6.64) = 7, +1.
        dl.drainSamplesUntilSilent(peak = 0.001) shouldBe (8.0 * 2205.0)

        // At or below the threshold there is nothing to drain.
        dl.drainSamplesUntilSilent(peak = 0.00001) shouldBe 0.0

        // fb = 0 overwrites the whole tap window in one period (+ slack).
        dl.feedback = 0.0
        dl.drainSamplesUntilSilent(peak = 1.0) shouldBe (2.0 * 2205.0)

        // |feedback| >= 1.0 self-oscillates: never drains on its own...
        dl.feedback = 1.0
        dl.drainSamplesUntilSilent(peak = 1.0) shouldBe Double.POSITIVE_INFINITY

        // ...but an already-silent ring outranks the sentinel: an EMPTY self-osc line must not
        // drain forever.
        dl.drainSamplesUntilSilent(peak = 0.0) shouldBe 0.0
    }

    "tapWindowPeakAbs sees only what the drain tap can still reach" {
        // maxDelaySeconds 0.05 -> ring 2205; delayTime 0.01 -> tap window 441 + 2 samples.
        val dl = DelayLine(maxDelaySeconds = 0.05, sampleRate = 44100)
        dl.delayTimeSeconds = 0.01
        dl.feedback = 0.0
        dl.tapWindowPeakAbs() shouldBe 0.0

        // A LOUD sample early, then quiet content: after 1000 written samples the loud one sits
        // outside the 443-sample window and must NOT inflate the drain (review round 4 — the
        // whole-ring scan paid the drain for whatever was loud up to bufferSize samples ago).
        val send1 = StereoBuffer(512)
        send1.left[2] = 0.5
        dl.process(send1, StereoBuffer(512), 512)

        val send2 = StereoBuffer(488)
        send2.right[487] = -0.01
        dl.process(send2, StereoBuffer(488), 488)

        dl.tapWindowPeakAbs() shouldBe 0.01

        dl.reset()
        dl.tapWindowPeakAbs() shouldBe 0.0
    }

    "tapWindowPeakAbs pins BOTH window edges exactly" {
        // delayInt = 441, window = 443: the oldest reachable sample is the s2 interpolation
        // neighbour at distance delayInt + 1 behind the head; distance delayInt + 2 is the one
        // slack sample the scan still covers; delayInt + 3 is provably unreachable. The
        // too-SMALL direction is the dangerous one (review round 5): missing the oldest
        // reachable sample sends configure straight to Off and reset() destroys an echo that
        // was one sample from being emitted.
        val dl = DelayLine(maxDelaySeconds = 0.05, sampleRate = 44100)
        dl.delayTimeSeconds = 0.01
        dl.feedback = 0.0

        // One loud sample at ring position 50 (fb = 0, so every later write is an exact zero).
        val send = StereoBuffer(100)
        send.left[50] = 0.4
        dl.process(send, StereoBuffer(100), 100)

        // Advance the head until the loud sample sits at distance delayInt + 1 = 442: seen.
        dl.process(StereoBuffer(392), StereoBuffer(392), 392)
        dl.tapWindowPeakAbs() shouldBe 0.4

        // Distance delayInt + 2 = 443: the slack sample, still seen.
        dl.process(StereoBuffer(1), StereoBuffer(1), 1)
        dl.tapWindowPeakAbs() shouldBe 0.4

        // Distance delayInt + 3 = 444: out of reach, gone.
        dl.process(StereoBuffer(1), StereoBuffer(1), 1)
        dl.tapWindowPeakAbs() shouldBe 0.0
    }

    "the wrap split survives a block longer than the whole ring" {
        // bufferSize 10 (0.01 s at 1 kHz) processed with length 25: the split must wrap more
        // than once instead of writing past the ring (block size is a tone parameter). The old
        // single-split code walks off the array here.
        val dl = DelayLine(maxDelaySeconds = 0.01, sampleRate = 1000)
        val send = StereoBuffer(25)
        send.left.fill(0.5)
        send.right.fill(0.5)
        val out = StereoBuffer(25)

        dl.process(send, out, 25)

        out.left.all { it.isFinite() } shouldBe true
        out.right.all { it.isFinite() } shouldBe true
    }

    "one NaN sample does not kill the delay for the rest of its life" {
        // Master round, the delay's half of "no state may latch". `softCap` already sterilises
        // +/-Inf (it saturates to +/-1), but `softCap(NaN)` is NaN, and this ring RECIRCULATES —
        // so without the store guard one NaN never scrolls out and every later sample is NaN.
        // `flushState` cannot reach this: the ring is FIR-shaped state, not an IIR carry.
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.delayTimeSeconds = 0.01
        delay.feedback = 0.4

        val poison = StereoBuffer(blockSize)
        val out = StereoBuffer(blockSize)
        poison.left[0] = Double.NaN
        poison.right[0] = Double.NaN
        delay.process(poison, out, blockSize)

        // Now feed clean audio and require real echoes back out.
        var heard = false
        repeat(6) {
            val send = StereoBuffer(blockSize)
            val fresh = StereoBuffer(blockSize)
            for (i in 0 until blockSize) {
                send.left[i] = 0.5
                send.right[i] = 0.5
            }
            delay.process(send, fresh, blockSize)

            fresh.left.all { it.isFinite() } shouldBe true
            fresh.right.all { it.isFinite() } shouldBe true

            if (fresh.left.any { abs(it) > 0.01 }) {
                heard = true
            }
        }

        // Not merely finite — actually still delaying. A guard that zeroed the whole ring
        // would satisfy isFinite and fail this.
        heard shouldBe true
    }
})
