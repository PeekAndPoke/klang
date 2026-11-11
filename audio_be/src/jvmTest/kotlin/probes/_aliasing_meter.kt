/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The aliasing meter (KLANG-00420, probe 2): how much energy a source or a shaper puts where no harmonic belongs, read
// off one long windowed FFT of a steady signal. A measurement, never a gate: these functions assert nothing about a
// stage; a spec that guards a fixed finding calls [measureAliasing] with a threshold of its own.

/**
 * The 7-term Blackman-Harris window's cosine coefficients (A. Nuttall's family, the minimum-sidelobe set published by
 * H.-H. Albrecht, "A family of cosine-sum windows for high-resolution measurements", ICASSP 2001): highest sidelobe
 * about -180 dB, main lobe 7 bins each side. [AliasingMeterSpec] measures the sidelobe, so a mistyped digit shows.
 */
val BLACKMAN_HARRIS_7: DoubleArray = doubleArrayOf(
    0.27105140069342,
    0.43329793923448,
    0.21812299954311,
    0.06592544638803,
    0.01081174209837,
    0.00077658482522,
    0.00001388721735,
)

/** The main lobe's half width of [BLACKMAN_HARRIS_7] in bins: its first zero. */
const val BLACKMAN_HARRIS_7_LOBE_BINS: Int = 7

/** The PERIODIC 7-term Blackman-Harris window of [size] points (the form for an FFT of [size]). */
fun blackmanHarris7(size: Int): DoubleArray = DoubleArray(size) { i ->
    var w = 0.0
    var sign = 1.0

    for (k in BLACKMAN_HARRIS_7.indices) {
        w += sign * BLACKMAN_HARRIS_7[k] * cos(2.0 * PI * k * i / size)
        sign = -sign
    }

    w
}

/** True when [n] is a power of two (1 included). */
private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

/**
 * An in-place iterative radix-2 FFT (forward, no scaling) of the complex signal [re] + i [im], both the same power-of-two
 * length. The twiddles come from one cosine and sine table per call, each entry computed directly (no recurrence), so
 * the transform's own error stays near double rounding.
 */
fun fftInPlace(re: DoubleArray, im: DoubleArray) {
    val n = re.size

    require(im.size == n && isPowerOfTwo(n)) { "fft needs two arrays of one power-of-two length" }

    // Bit-reversal permutation.
    var j = 0

    for (i in 1 until n) {
        var bit = n shr 1

        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }

        j = j xor bit

        if (i < j) {
            val tr = re[i]
            re[i] = re[j]
            re[j] = tr

            val ti = im[i]
            im[i] = im[j]
            im[j] = ti
        }
    }

    val half = n / 2
    val cosTable = DoubleArray(half) { cos(2.0 * PI * it / n) }
    val sinTable = DoubleArray(half) { sin(2.0 * PI * it / n) }
    var len = 2

    while (len <= n) {
        val halfLen = len / 2
        val step = n / len
        var start = 0

        while (start < n) {
            for (k in 0 until halfLen) {
                val wr = cosTable[k * step]
                val wi = -sinTable[k * step]
                val a = start + k
                val b = a + halfLen
                val xr = re[b] * wr - im[b] * wi
                val xi = re[b] * wi + im[b] * wr

                re[b] = re[a] - xr
                im[b] = im[a] - xi
                re[a] += xr
                im[a] += xi
            }

            start += len
        }

        len = len shl 1
    }
}

/**
 * The one-sided power spectrum |X[k]|^2, k = 0 .. [window].size / 2, of [samples] from frame [from] on under [window]
 * (the FFT length is the window's).
 */
fun windowedPowerSpectrum(samples: DoubleArray, from: Int, window: DoubleArray): DoubleArray {
    val n = window.size
    val re = DoubleArray(n) { samples[from + it] * window[it] }
    val im = DoubleArray(n)

    fftInPlace(re, im)

    return DoubleArray(n / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
}

/**
 * The highest sidelobe of the 7-term Blackman-Harris window, in dB re its main lobe's peak: the window of [size] points
 * zero-padded [pad] times, every padded bin from [BLACKMAN_HARRIS_7_LOBE_BINS] window bins out to Nyquist. The window's
 * own floor, printed above the table.
 */
fun windowPeakSidelobeDb(size: Int = 4096, pad: Int = 16): Double {
    val n = size * pad
    val window = blackmanHarris7(size)
    val re = DoubleArray(n)
    val im = DoubleArray(n)

    window.copyInto(destination = re, destinationOffset = 0, startIndex = 0, endIndex = size)
    fftInPlace(re, im)

    val peak = re[0] * re[0] + im[0] * im[0]
    var worst = 0.0

    for (k in BLACKMAN_HARRIS_7_LOBE_BINS * pad..n / 2) {
        val p = re[k] * re[k] + im[k] * im[k]

        if (p > worst) {
            worst = p
        }
    }

    return 10.0 * log10(worst / peak)
}

/** The bin kinds of [measureAliasing]: outside every band, the DC band, the fundamental's band, a higher harmonic's. */
private const val BIN_OTHER: Int = 0
private const val BIN_DC: Int = 1
private const val BIN_FUNDAMENTAL: Int = 2
private const val BIN_HARMONIC: Int = 3

/**
 * Where a peak sits between the bins: the vertex of the parabola through the log power of bin [k] and its two
 * neighbours, as a fractional bin. [k] itself when a neighbour is missing or a power is 0.
 */
private fun refinedBin(power: DoubleArray, k: Int): Double {
    if (k <= 0 || k >= power.size - 1 || power[k - 1] <= 0.0 || power[k] <= 0.0 || power[k + 1] <= 0.0) {
        return k.toDouble()
    }

    val a = ln(power[k - 1])
    val b = ln(power[k])
    val c = ln(power[k + 1])
    val den = a - 2.0 * b + c

    if (den >= 0.0) {
        return k.toDouble()
    }

    return k + 0.5 * (a - c) / den
}

/** 10 log10 of a power ratio; -Infinity for 0. */
private fun db(power: Double): Double = 10.0 * log10(power)

/**
 * Reads [samples] (mono, at least [AliasingConfig.framesNeeded] frames, expected to hold a steady tone at [nominalHz])
 * with the method of [AliasingConfig]:
 *
 * 1. one window of [AliasingConfig.fftSize] frames after the settle time, 7-term Blackman-Harris, one FFT;
 * 2. each one-sided bin's power converted to mean square (Parseval: twice |X|^2 over N times the window's sum of
 *    squares, once for DC and Nyquist), so a band's sum is the component's mean square;
 * 3. the fundamental f: with [findPitch], the strongest bin within the search range of [nominalHz], refined by a
 *    parabola through the log power (for a source, whose pitch is the thing to check); without it, [nominalHz] itself
 *    (for a shaper fed an exact sine: its harmonics sit on the input's grid even where the fundamental is absent, as
 *    for a full-wave rectifier). Every harmonic h of f with h * f below Nyquist owns the bins within
 *    [AliasingConfig.bandBins] of h * f; DC owns bins 0 .. bandBins;
 * 4. every other bin is NON-harmonic: summed, and its strongest bin found (power within bandBins of it, outside the
 *    bands).
 */
fun measureAliasing(
    samples: DoubleArray,
    nominalHz: Double,
    config: AliasingConfig = AliasingConfig(),
    findPitch: Boolean = true,
): AliasingReading {
    val n = config.fftSize

    require(isPowerOfTwo(n)) { "the FFT size must be a power of two" }
    require(samples.size >= config.framesNeeded) { "the signal is shorter than the settle time plus one window" }

    val window = blackmanHarris7(n)
    var sumSquares = 0.0

    for (w in window) {
        sumSquares += w * w
    }

    val power = windowedPowerSpectrum(samples = samples, from = config.settleFrames, window = window)
    val half = n / 2
    val norm = n * sumSquares
    val meanSquare = DoubleArray(half + 1) { k -> power[k] * (if (k == 0 || k == half) 1.0 else 2.0) / norm }
    val binHz = config.binHz
    val nyquist = config.sampleRate / 2.0
    val band = config.bandBins

    // The fundamental: the strongest bin near the nominal pitch, refined.
    val nominalBin = nominalHz / binHz
    val reach = max(3.0, config.searchFraction * nominalBin)
    val lo = max(1, ceil(nominalBin - reach).toInt())
    val hi = min(half - 1, floor(nominalBin + reach).toInt())
    var peakBin = lo

    for (k in lo..hi) {
        if (power[k] > power[peakBin]) {
            peakBin = k
        }
    }

    val fundamentalHz = if (findPitch) refinedBin(power, peakBin) * binHz else nominalHz

    // Every bin's kind: DC first, then the harmonic bands over it.
    val kind = IntArray(half + 1)

    for (k in 0..min(band, half)) {
        kind[k] = BIN_DC
    }

    var harmonics = 0
    var h = 1

    while (h * fundamentalHz < nyquist) {
        val centre = h * fundamentalHz / binHz
        val from = max(0, ceil(centre - band).toInt())
        val to = min(half, floor(centre + band).toInt())

        for (k in from..to) {
            kind[k] = if (h == 1) BIN_FUNDAMENTAL else BIN_HARMONIC
        }

        harmonics++
        h++
    }

    var fundamental = 0.0
    var harmonic = 0.0
    var other = 0.0
    var otherBelow5k = 0.0
    var dc = 0.0
    var strongest = -1

    for (k in 0..half) {
        when (kind[k]) {
            BIN_FUNDAMENTAL -> fundamental += meanSquare[k]
            BIN_HARMONIC -> harmonic += meanSquare[k]
            BIN_DC -> dc += meanSquare[k]
            else -> {
                other += meanSquare[k]

                if (k * binHz < ALIASING_AUDIBLE_BAND_HZ) {
                    otherBelow5k += meanSquare[k]
                }

                if (strongest < 0 || power[k] > power[strongest]) {
                    strongest = k
                }
            }
        }
    }

    var strongestPower = 0.0

    if (strongest >= 0) {
        for (k in max(0, strongest - band)..min(half, strongest + band)) {
            if (kind[k] == BIN_OTHER) {
                strongestPower += meanSquare[k]
            }
        }
    }

    return AliasingReading(
        nominalHz = nominalHz,
        fundamentalHz = fundamentalHz,
        offsetCents = 1200.0 * log2(fundamentalHz / nominalHz),
        fundamentalDbfs = db(2.0 * fundamental),
        harmonicCount = harmonics,
        harmonicDb = db(harmonic / fundamental),
        harmonicsDbfs = db(2.0 * (fundamental + harmonic)),
        nonHarmonicDb = db(other / fundamental),
        nonHarmonicReHarmonicsDb = db(other / (fundamental + harmonic)),
        nonHarmonicDbfs = db(2.0 * other),
        nonHarmonicBelow5kDb = db(otherBelow5k / fundamental),
        strongestHz = if (strongest >= 0) refinedBin(power, strongest) * binHz else Double.NaN,
        strongestDb = db(strongestPower / fundamental),
        dcDbfs = db(dc),
    )
}

/** The band [AliasingReading.nonHarmonicBelow5kDb] sums: non-harmonic energy below 5 kHz, where an alias is heard. */
const val ALIASING_AUDIBLE_BAND_HZ: Double = 5000.0

/** [frames] rounded up to whole [PROBE_BLOCK_FRAMES] blocks. */
fun wholeProbeBlocks(frames: Int): Int = ((frames + PROBE_BLOCK_FRAMES - 1) / PROBE_BLOCK_FRAMES) * PROBE_BLOCK_FRAMES

/** A sine of [amplitude] at [hz], [frames] long, from phase 0, at [sampleRate]: the shapers' input and the meter's control. */
fun probeSine(hz: Double, amplitude: Double, frames: Int, sampleRate: Int = PROBE_SAMPLE_RATE): DoubleArray =
    DoubleArray(frames) { amplitude * sin(2.0 * PI * hz * it / sampleRate) }

/**
 * A held signal for a source (the input is silence, it only holds the gate open for the whole render): [frames] rounded
 * up to whole blocks.
 */
fun heldSilence(frames: Int): ProbeSignal {
    val total = wholeProbeBlocks(frames)

    return ProbeSignal(name = "held", left = DoubleArray(total), right = DoubleArray(total), inputEndFrame = total)
}

/** A held sine on both channels for a shaper: [frames] rounded up to whole blocks, the gate open for the whole render. */
fun heldSine(hz: Double, amplitude: Double, frames: Int): ProbeSignal {
    val total = wholeProbeBlocks(frames)
    val sine = probeSine(hz = hz, amplitude = amplitude, frames = total)

    return ProbeSignal(name = "sine", left = sine, right = sine.copyOf(), inputEndFrame = total)
}
