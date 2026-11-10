/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * `reverb.lowpass` is a real cutoff (maintainer, 2026-11-10; audit finding L7b-03, KLANG-00428): the value is the -3 dB
 * corner of the one-pole in every comb's feedback path, the same corner at every rate. Until then it was a linear
 * damping amount on a scale in Hz, and `lowpass = 1000` sounded almost like `lowpass = 0`. The network's arithmetic
 * with the law is pinned bit for bit by `ReverbNetworkLawSpec`; here the law itself, its two bounds, and what it does
 * to a tail.
 */
class ReverbLowpassLawSpec : StringSpec({

    /** The one-pole `y = (1 - p) x + p y'`'s power gain at [hz], from its own transfer function, not from the law. */
    fun powerGain(pole: Double, hz: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * hz / sampleRate

        return (1.0 - pole) * (1.0 - pole) / (1.0 - 2.0 * pole * cos(w) + pole * pole)
    }

    /** The -3 dB corner of a one-pole, in Hz: `cos w = (4p - 1 - p^2) / 2p`. */
    fun cornerOf(pole: Double, sampleRate: Double): Double = acos((4.0 * pole - 1.0 - pole * pole) / (2.0 * pole)) * sampleRate / (2.0 * PI)

    /** (sample rate, region factor): two base rates and a unit inside a 2x region over 48 kHz. */
    val rates = listOf(44100 to 1, 48000 to 1, 96000 to 2)

    "a set lowpass is the -3 dB corner of the combs' one-pole, at 44.1 and 48 kHz and inside a region" {
        for ((sampleRate, factor) in rates) {
            val fs = sampleRate.toDouble()
            val nyquist = fs / (2.0 * factor)

            for (cutoff in listOf(100.0, 1000.0, 4000.0, 8000.0, 16000.0)) {
                val pole = Reverb.cutoffDamping(cutoffHz = cutoff, sampleRate = fs, nyquistHz = nyquist)

                withClue("$sampleRate Hz (factor $factor), cutoff $cutoff: half the power at the cutoff") {
                    abs(powerGain(pole = pole, hz = cutoff, sampleRate = fs) - 0.5) shouldBeLessThan 1e-9
                }
            }
        }
    }

    "a cutoff above the Nyquist frequency is the Nyquist frequency of the rate the room is heard at" {
        val fs = 48000.0
        val atNyquist = Reverb.cutoffDamping(cutoffHz = 30000.0, sampleRate = fs, nyquistHz = 24000.0)

        withClue("at the Nyquist frequency w = pi and the pole is 3 - 2 sqrt(2)") {
            abs(atNyquist - (3.0 - 2.0 * sqrt(2.0))) shouldBeLessThan 1e-12
        }

        val inRegion = Reverb.cutoffDamping(cutoffHz = 30000.0, sampleRate = 96000.0, nyquistHz = 24000.0)

        withClue("inside a 2x region the corner stops at the base rate's Nyquist, 24 kHz: w = pi / 2, the pole 2 - sqrt(3)") {
            abs(inRegion - (2.0 - sqrt(3.0))) shouldBeLessThan 1e-12
        }
    }

    "a cutoff under 5 Hz, the engine's lowest filter cutoff, is 5 Hz: the pole stays under 1, and 0 is the darkest room" {
        for ((sampleRate, factor) in rates) {
            val fs = sampleRate.toDouble()
            val nyquist = fs / (2.0 * factor)
            val atFloor = Reverb.cutoffDamping(cutoffHz = 5.0, sampleRate = fs, nyquistHz = nyquist)

            withClue("$sampleRate Hz: the floor's pole is under 1 and its corner is 5 Hz") {
                atFloor shouldBeLessThan 1.0
                abs(cornerOf(pole = atFloor, sampleRate = fs) - 5.0) shouldBeLessThan 1e-3
            }

            for (cutoff in listOf(1.0, 0.0, -100.0)) {
                withClue("$sampleRate Hz, cutoff $cutoff: the floor") {
                    Reverb.cutoffDamping(cutoffHz = cutoff, sampleRate = fs, nyquistHz = nyquist) shouldBe atFloor
                }
            }
        }
    }

    "a 1000 Hz room is dark and a 20000 Hz room bright, and 1000 is no longer 0: the tail's high band" {
        // An impulse into a size-5 room at 48 kHz; the tail's first difference (a crude highpass, +6 dB per octave)
        // from 0.3 s to 1 s. Under the old law 1000 was within a few percent of 0 here.
        val sampleRate = 48000
        val block = 128
        val frames = 48000

        fun highBandEnergy(lowpass: Double): Double {
            val reverb = Reverb(sampleRate).apply {
                size = 0.5
                this.lowpass = lowpass
            }
            val inBuf = StereoBuffer(block)
            val outBuf = StereoBuffer(block)
            val out = DoubleArray(frames)
            var at = 0

            while (at < frames) {
                inBuf.clear()

                if (at == 0) {
                    inBuf.left[0] = 1.0
                    inBuf.right[0] = 1.0
                }

                outBuf.clear()
                reverb.process(input = inBuf, output = outBuf, length = block)
                outBuf.left.copyInto(out, at)
                at += block
            }

            var energy = 0.0

            for (i in (3 * sampleRate / 10) until frames) {
                val d = out[i] - out[i - 1]
                energy += d * d
            }

            return energy
        }

        val dark = highBandEnergy(lowpass = 1000.0)
        val darkest = highBandEnergy(lowpass = 0.0)
        val bright = highBandEnergy(lowpass = 20000.0)

        withClue("1000 Hz against 20000 Hz") {
            dark shouldBeLessThan bright * 1e-3
        }
        withClue("0 (the 5 Hz floor) against 1000 Hz") {
            darkest shouldBeLessThan dark * 1e-2
        }
        withClue("both rooms ring") {
            darkest shouldBeGreaterThan 0.0
        }
    }

    "an unset room keeps Freeverb's tail accounting after it has run, and a set one leaves it when unset again" {
        // The lag counts only for a SET cutoff, so a song that never sets one drains and holds exactly as before:
        // 68 revolutions of the longest comb (1640 samples at 44.1 kHz) from a peak of 1 at size 0.5
        // (`ReverbStabilitySpec`'s row, here after blocks have run, which is when the lag is computed).
        val reverb = Reverb(44100).apply { size = 0.5 }
        val inBuf = StereoBuffer(128)
        val outBuf = StereoBuffer(128)

        fun runBlock() {
            inBuf.fill(0.1)
            outBuf.clear()
            reverb.process(input = inBuf, output = outBuf, length = 128)
        }

        runBlock()
        reverb.drainSamplesUntilSilent(peak = 1.0) shouldBe (68.0 * 1640.0)

        reverb.lowpass = 1000.0
        runBlock()

        withClue("a set cutoff lengthens the count by its lag") {
            reverb.drainSamplesUntilSilent(peak = 1.0) shouldBeGreaterThan (68.0 * 1640.0)
        }

        reverb.lowpass = Double.NaN
        runBlock()
        reverb.drainSamplesUntilSilent(peak = 1.0) shouldBe (68.0 * 1640.0)
    }

    "at the darkest cutoff the drain countdown still ends with every comb cell under the floor" {
        // The darkest pole lengthens every lap by its lag (about 1500 samples at 48 kHz), and the countdown has to
        // count it: a DC charge, the loop's slowest mode, into the longest room, then exactly the countdown of
        // silence. Without the lag the cells stood at 1.65e-5.
        val sampleRate = 48000
        val block = 128
        val reverb = Reverb(sampleRate).apply {
            size = 1.0
            lowpass = 0.0
        }
        val inBuf = StereoBuffer(block)
        val outBuf = StereoBuffer(block)

        repeat(400) {
            inBuf.fill(0.5)
            outBuf.clear()
            reverb.process(input = inBuf, output = outBuf, length = block)
        }

        val peak = reverb.combPeakAbs()
        val countdown = reverb.drainSamplesUntilSilent(peak = peak)
        var left = countdown.toInt()

        inBuf.clear()

        while (left > 0) {
            val n = minOf(block, left)

            outBuf.clear()
            reverb.process(input = inBuf, output = outBuf, length = n)
            left -= n
        }

        withClue("charged to $peak, drained $countdown samples") {
            reverb.combPeakAbs() shouldBeLessThanOrEqual SILENCE_FLOOR
        }
    }
})
