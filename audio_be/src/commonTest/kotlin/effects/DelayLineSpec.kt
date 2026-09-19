/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.abs
import kotlin.math.sin

class DelayLineSpec : StringSpec({

    val sampleRate = 44100
    val blockSize = 512

    "impulse appears in output after correct delay time" {
        val delaySeconds = 0.01 // 10ms
        val delaySamples = (delaySeconds * sampleRate).toInt() // 441 samples
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.time = delaySeconds
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
        delay.time = delaySeconds
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
        delay.time = delaySeconds
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
        delay.time = delaySeconds
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
        delay.time = 0.1
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
        delay.time = 0.1
        delay.feedback = 0.0

        // Never send any signal
        delay.hasTail() shouldBe false
    }

    "very short delay below 1ms works without crash" {
        val delay = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate)
        delay.time = 0.0005 // 0.5ms — flanger range
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
        delay.time = 0.1
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
        delay.time = 0.5

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
        delay.time = 0.001

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
        dl.time = 0.05 // 2205 samples, no coercion

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
        dl.time = 0.01
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
        dl.time = 0.01
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
        delay.time = 0.01
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

    // ── A time change crossfades the taps (Katalyst 5b-2) ─────────────────────────────────────────
    //
    // The oracle: with feedback 0 the ring holds nothing but the input, so the line under test reads
    // exactly what separate bare lines at the old and the new time read, and the crossfade is their
    // straight-line blend over KNOB_GLIDE_SECONDS, 2205 samples at 44.1 kHz, written out by hand.

    val xfBlock = 128
    val fadeSamples = 2205

    /** A deterministic, non-periodic input, so a tap read from the wrong place cannot hide. */
    fun xfInput(block: Int): StereoBuffer = StereoBuffer(xfBlock).apply {
        for (i in 0 until xfBlock) {
            val t = block * xfBlock + i
            left[i] = ((t * 7919) % 1000) / 1000.0 - 0.5
            right[i] = ((t * 104729) % 1000) / 1000.0 - 0.5
        }
    }

    fun line(time: Double) = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate, time = time, feedback = 0.0)

    /** One block of [l] fed block [block]'s input, into a fresh output. */
    fun run(l: DelayLine, block: Int): StereoBuffer =
        StereoBuffer(xfBlock).also { l.process(xfInput(block), it, xfBlock) }

    "a time change crossfades from the old tap to the new one, per sample, and then IS the new tap" {
        val underTest = line(0.01)
        val old = line(0.01)
        val new = line(0.02)
        val change = 20

        for (block in 0 until change) {
            val out = run(underTest, block)
            val ref = run(old, block)
            run(new, block)

            for (i in 0 until xfBlock) {
                out.left[i].toRawBits() shouldBe ref.left[i].toRawBits()
            }
        }

        underTest.time = 0.02

        var jumpGap = 0.0

        for (block in change until change + 30) {
            val out = run(underTest, block)
            val a = run(old, block)
            val b = run(new, block)

            for (i in 0 until xfBlock) {
                val j = (block - change) * xfBlock + i + 1

                if (j >= fadeSamples) {
                    // Landed: the single-tap read, bit for bit.
                    out.left[i].toRawBits() shouldBe b.left[i].toRawBits()
                    out.right[i].toRawBits() shouldBe b.right[i].toRawBits()
                } else {
                    val g = j.toDouble() / fadeSamples

                    (abs(out.left[i] - (a.left[i] * (1.0 - g) + b.left[i] * g)) <= 1e-12) shouldBe true
                    (abs(out.right[i] - (a.right[i] * (1.0 - g) + b.right[i] * g)) <= 1e-12) shouldBe true
                }

                jumpGap = maxOf(jumpGap, abs(out.left[i] - b.left[i]))
            }
        }

        // Engagement: the fade is really there, the output is not the new tap from the first sample.
        (jumpGap > 0.1) shouldBe true
    }

    "a time change that arrives mid-crossfade is PARKED: the running fade finishes, then the next one starts" {
        val underTest = line(0.01)
        val a = line(0.01)
        val b = line(0.02)
        val c = line(0.03)
        val change = 20

        for (block in 0 until change) {
            run(underTest, block)
            run(a, block)
            run(b, block)
            run(c, block)
        }

        underTest.time = 0.02

        // The first fade spans 2205 samples, so it ends inside the 18th block after the change; the
        // next block is where a parked time starts its own fade (fades start on block boundaries).
        val secondFadeBlock = change + (fadeSamples + xfBlock - 1) / xfBlock

        for (block in change until change + 60) {
            if (block == change + 5) {
                underTest.time = 0.03
            }

            val out = run(underTest, block)
            val oa = run(a, block)
            val ob = run(b, block)
            val oc = run(c, block)

            for (i in 0 until xfBlock) {
                val j = (block - change) * xfBlock + i + 1
                val expected = if (block < secondFadeBlock) {
                    // Still the first fade (or its landed tail): old 0.01 to 0.02, untouched by 0.03.
                    val g = if (j >= fadeSamples) 1.0 else j.toDouble() / fadeSamples
                    oa.left[i] * (1.0 - g) + ob.left[i] * g
                } else {
                    val k = (block - secondFadeBlock) * xfBlock + i + 1
                    val g = if (k >= fadeSamples) 1.0 else k.toDouble() / fadeSamples
                    ob.left[i] * (1.0 - g) + oc.left[i] * g
                }

                withClue("block $block sample $i") {
                    (abs(out.left[i] - expected) <= 1e-12) shouldBe true
                }
            }
        }
    }

    "after reset the next time is in force at once: an empty ring has nothing to crossfade from" {
        val underTest = line(0.3)

        for (block in 0 until 10) {
            run(underTest, block)
        }

        underTest.reset()
        underTest.time = 0.01
        underTest.isCrossfading shouldBe false

        // The oracle: a line that never knew another time, fed from the same moment on.
        val fresh = line(0.01)

        for (block in 10 until 20) {
            val out = run(underTest, block)
            val ref = run(fresh, block)

            for (i in 0 until xfBlock) {
                out.left[i].toRawBits() shouldBe ref.left[i].toRawBits()
            }
        }
    }

    /**
     * The feedback ramp against a delay written out by hand. A 1/64 s tap at 48 kHz is exactly 750
     * samples and 1/32 s exactly 1500 (no interpolation), and every level stays below the soft
     * cap's 0.95 knee, where it is the identity. Per block the feedback moves from the value the
     * previous block ended on to the new one, sample j of the block at (j + 1) / 128 of the way.
     * When [changeTimeAt] is set, the time moves from 750 to 1500 samples in that block and the
     * oracle crossfades the two taps over 2400 samples (KNOB_GLIDE_SECONDS at 48 kHz), sample k of
     * the fade at k / 2400. [maxDelaySeconds] decides whether the ring wraps inside a block.
     */
    fun feedbackRampAgainstHand(maxDelaySeconds: Double, changeTimeAt: Int?): Pair<Double, Double> {
        val sr = 48000
        val n = 128
        val fade = 2400
        val line = DelayLine(maxDelaySeconds = maxDelaySeconds, sampleRate = sr, time = 1.0 / 64.0, feedback = 0.1)
        val ring = DoubleArray(200 * n)
        val input = StereoBuffer(n)
        val output = StereoBuffer(n)
        var previous = 0.1
        var worst = 0.0
        var stairGap = 0.0

        for (b in 0 until 200) {
            // The feedback moves on some blocks and holds on others.
            val fb = if (b < 30) 0.1 else if (b < 50) 0.1 + 0.6 * (b - 29) / 20.0 else 0.7

            line.feedback = fb

            if (changeTimeAt != null && b == changeTimeAt) {
                line.time = 1.0 / 32.0
            }

            for (i in 0 until n) {
                val t = b * n + i
                // Continuous, so every chunk of every block carries signal the ramp scales; at
                // feedback 0.7 the ring settles near 0.1 / 0.3, well below the knee.
                input.left[i] = 0.1 * sin(t * 0.3)
                input.right[i] = input.left[i]
            }

            output.clear()
            line.process(input, output, n)

            for (i in 0 until n) {
                val t = b * n + i
                val f = fb - (fb - previous) / n * (n - 1 - i)
                val short = if (t >= 750) ring[t - 750] else 0.0
                val long = if (t >= 1500) ring[t - 1500] else 0.0
                val delayed = if (changeTimeAt == null || b < changeTimeAt) {
                    short
                } else {
                    val k = (b - changeTimeAt) * n + i + 1
                    val g = if (k >= fade) 1.0 else k.toDouble() / fade

                    short * (1.0 - g) + long * g
                }

                ring[t] = input.left[i] + delayed * f
                worst = maxOf(worst, abs(output.left[i] - delayed))
                stairGap = maxOf(stairGap, abs(f - fb))
            }

            previous = fb
        }

        return worst to stairGap
    }

    "a feedback change ramps per SAMPLE across the block, along the straight line from the last block's value" {
        val (worst, stairGap) = feedbackRampAgainstHand(maxDelaySeconds = 1.0, changeTimeAt = null)

        (stairGap > 0.01) shouldBe true
        (worst <= 1e-12) shouldBe true
    }

    "the feedback ramp holds across a ring WRAP inside a block: the second chunk enters the ramp at its offset" {
        // 0.05 s is 2400 cells: the ring wraps every 18.75 blocks, so `process` splits blocks into
        // two chunks and the second one starts mid-ramp.
        val (worst, stairGap) = feedbackRampAgainstHand(maxDelaySeconds = 0.05, changeTimeAt = null)

        (stairGap > 0.01) shouldBe true
        (worst <= 1e-12) shouldBe true
    }

    "the feedback ramps per sample DURING a tap crossfade too, on a wrapping ring" {
        // The time and the feedback move in the same block (block 30): the crossfade loop carries
        // the ramp as the plain loop does.
        val (worst, stairGap) = feedbackRampAgainstHand(maxDelaySeconds = 0.05, changeTimeAt = 30)

        (stairGap > 0.01) shouldBe true
        (worst <= 1e-12) shouldBe true
    }
})
