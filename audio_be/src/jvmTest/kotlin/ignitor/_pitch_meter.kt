/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.blackmanHarris7
import io.peekandpoke.klang.audio_be.probes.fftInPlace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pitch of a (decaying) tone, by two independent methods: [fftPeakHz] (method A, the frequency domain) and
 * [heterodyneHz] (method B, the time domain). Built for the engine audit's disproof pass (KLANG-00420, where the two
 * agreed within 0.001 cent on the pluck and read 0.0000 cents on a sine and a decaying sine) and moved here from the
 * audit's kit when the pluck was tuned (KLANG-00426): `PluckPitchSpec` guards the tuning with it. Test code only.
 */
internal object PitchMeter {

    /** Cents of [hz] re [nominalHz]. */
    fun cents(hz: Double, nominalHz: Double): Double = 1200.0 * log2(hz / nominalHz)

    /**
     * The fundamental of [x] (a note at nominal [nominalHz] that starts at frame 0), by both methods: the FFT peak over
     * the frames from four periods in to where the note has fallen 140 dB (at most 65536), searched from 0.70 to 1.15
     * times the note, and the heterodyne phase slope over the same span. Returns (method A Hz, method B Hz).
     */
    fun pitchOf(x: DoubleArray, nominalHz: Double, sampleRate: Int = PROBE_SAMPLE_RATE): Pair<Double, Double> {
        val start = (4.0 * sampleRate / nominalHz).toInt()
        val end = aliveEnd(x = x, from = start, floorRel = 1e-7)
        val fft = fftPeakHz(x = x, from = start, length = end - start, loHz = nominalHz * 0.70, hiHz = nominalHz * 1.15, sampleRate = sampleRate)
        val het = heterodyneHz(x = x, from = 0, to = end, refHz = nominalHz, sampleRate = sampleRate)

        return fft to het
    }

    /** The largest power of two at most [n] (1 for n below 2). */
    fun powerOfTwoAtMost(n: Int): Int {
        var p = 1

        while (p * 2 <= n) {
            p *= 2
        }

        return p
    }

    /** The last frame from [from] on whose |x| exceeds [floorRel] times the largest |x| from [from] on; [from] when none. */
    fun aliveEnd(x: DoubleArray, from: Int, floorRel: Double): Int {
        var peak = 0.0

        for (i in from until x.size) {
            peak = max(peak, abs(x[i]))
        }

        var last = from

        for (i in from until x.size) {
            if (abs(x[i]) > floorRel * peak) {
                last = i
            }
        }

        return last
    }

    /**
     * Method A, the frequency domain: the strongest bin between [loHz] and [hiHz] of ONE 7-term Blackman-Harris window
     * (the aliasing meter's window) over `[from, from + n)`, n the largest power of two that fits in [length] (at most
     * 65536), zero-padded 16 times (at most 2^20 points), refined by a parabola through the log power of the bin and its
     * two neighbours. The magnitude of any positive envelope (a decay, the window) times a sinusoid is symmetric about
     * the sinusoid's frequency, so a decay does not move the peak; the padding makes the parabola's own error negligible.
     * NaN when fewer than 256 frames fit.
     */
    fun fftPeakHz(x: DoubleArray, from: Int, length: Int, loHz: Double, hiHz: Double, sampleRate: Int = PROBE_SAMPLE_RATE): Double {
        val n = min(65536, powerOfTwoAtMost(min(length, x.size - from)))

        if (n < 256) {
            return Double.NaN
        }

        val padded = min(1 shl 20, n * 16)
        val window = blackmanHarris7(n)
        val re = DoubleArray(padded)
        val im = DoubleArray(padded)

        for (i in 0 until n) {
            re[i] = x[from + i] * window[i]
        }

        fftInPlace(re, im)

        val binHz = sampleRate.toDouble() / padded
        val lo = max(1, ceil(loHz / binHz).toInt())
        val hi = min(padded / 2 - 2, floor(hiHz / binHz).toInt())
        var k = lo

        for (j in lo..hi) {
            if (re[j] * re[j] + im[j] * im[j] > re[k] * re[k] + im[k] * im[k]) {
                k = j
            }
        }

        val a = ln(re[k - 1] * re[k - 1] + im[k - 1] * im[k - 1])
        val b = ln(re[k] * re[k] + im[k] * im[k])
        val c = ln(re[k + 1] * re[k + 1] + im[k + 1] * im[k + 1])
        val den = a - 2.0 * b + c
        val delta = if (den < 0.0) 0.5 * (a - c) / den else 0.0

        return (k + delta) * binHz
    }

    /** A causal moving average of length [l] (the first l - 1 outputs average fewer samples; the caller skips them). */
    private fun movingAverage(x: DoubleArray, l: Int): DoubleArray {
        val out = DoubleArray(x.size)
        var sum = 0.0

        for (i in x.indices) {
            sum += x[i]

            if (i >= l) {
                sum -= x[i - l]
            }

            out[i] = sum / l
        }

        return out
    }

    /**
     * Method B, the time domain: complex demodulation. `x * e^(-i w0 n)` at [refHz], four cascaded moving averages of
     * two reference periods each (they null the reference's own harmonics and its image, and leave the fundamental's
     * small offset from [refHz] at more than half its level for an offset up to 15 percent), then the unwrapped phase,
     * and a least-squares line through it over the frames from four reference periods after the filters' warm-up to
     * the last frame whose amplitude is above [floorRel] of the largest. The slope is the fundamental's offset from
     * [refHz]. Independent of method A: no FFT, no window, no peak search.
     */
    fun heterodyneHz(
        x: DoubleArray,
        from: Int,
        to: Int,
        refHz: Double,
        sampleRate: Int = PROBE_SAMPLE_RATE,
        floorRel: Double = 1e-8,
    ): Double {
        val w0 = 2.0 * PI * refHz / sampleRate
        val len = to - from
        var re = DoubleArray(len) { x[from + it] * cos(w0 * (from + it)) }
        var im = DoubleArray(len) { -x[from + it] * sin(w0 * (from + it)) }
        val l = max(2, (2.0 * sampleRate / refHz).roundToInt())

        repeat(4) {
            re = movingAverage(re, l)
            im = movingAverage(im, l)
        }

        val start = 4 * (l - 1) + (4.0 * sampleRate / refHz).roundToInt()

        if (start >= len - 16) {
            return Double.NaN
        }

        var peak = 0.0

        for (i in start until len) {
            peak = max(peak, hypot(re[i], im[i]))
        }

        var end = start

        for (i in start until len) {
            if (hypot(re[i], im[i]) > floorRel * peak) {
                end = i
            }
        }

        if (end - start < 64) {
            return Double.NaN
        }

        // Unwrapped phase and a least-squares slope over [start, end].
        var prev = atan2(im[start], re[start])
        var unwrapped = prev
        var sumN = 0.0
        var sumP = 0.0
        var sumNN = 0.0
        var sumNP = 0.0
        var count = 0.0

        for (i in start..end) {
            val p = atan2(im[i], re[i])
            var d = p - prev

            while (d > PI) {
                d -= 2.0 * PI
            }

            while (d < -PI) {
                d += 2.0 * PI
            }

            unwrapped += d
            prev = p

            val n = (i - start).toDouble()

            sumN += n
            sumP += unwrapped
            sumNN += n * n
            sumNP += n * unwrapped
            count += 1.0
        }

        val slope = (count * sumNP - sumN * sumP) / (count * sumNN - sumN * sumN)

        return refHz + slope * sampleRate / (2.0 * PI)
    }
}
