/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.flushState

/**
 * The whole resonator stage on one MONO channel: a parallel bank of TPT-SVF bandpasses, each band scaled by its own
 * LINEAR gain and the outputs summed into the wet signal, blended over the dry input by the shared wet/dry law. The
 * one DSP behind `body(...)` and `vowel(...)` (Katalyst step 5c-3; one class since engine tidy-up step 12 (a), where
 * it absorbed the parallel-mix wrapper and the SVF band classes). What a band's gain means is decided where a
 * catalogue row becomes a band ([ResonatorTable.ofBody], [ResonatorTable.ofVowel]).
 *
 * **The law, per block:**
 * ```
 * wet = 0.0 + band_0 * gain_0 + band_1 * gain_1 + ...   (band_b = k * v1 of band b's SVF, in band order)
 * out = dry * dryGain + wet * wetGain                   (dryGain = max(floor, cos(w*pi/2)^2), wetGain = sin(w*pi/2)^2)
 * ```
 * with `w` the mix clamped to `[0, 1]` (non-finite reads 0) and `floor` likewise. The p = 2 (equal-AMPLITUDE) branch
 * of [WetDryMix] applies because the wet is the dry through the bank, coherent in the passbands. `floor` is the
 * least dry coefficient (the body's is 0.4, the vowel's 0.2); above it the dry stays at the floor while the wet keeps
 * rising. **A mix at or below 0 bypasses**: the buffer is untouched and the bands do not run, so their state does not
 * advance.
 *
 * Each band is the Cytomic TPT SVF with the bandpass tap normalised to a unity peak (`k * v1`, so q is a pure width
 * control): `v3 = v0 - ic2; v1 = a1*ic1 + a2*v3; v2 = ic2 + a2*ic1 + a3*v3; ic1 = 2*v1 - ic1; ic2 = 2*v2 - ic2`, the
 * integrators flushed ([flushState]), the coefficients from [computeSvfCoeffs] at the band's cutoff and q after the
 * SVF's own guards ([clampSvfCutoff], [clampSvfQ]). Each SVF bandpass has DC gain 0, so the wet stays 0 at DC. No
 * output normalisation: N coherent peaks sum without a `1/N`, and the master's soft cap and limiter bound the result.
 *
 * **A SOUNDING bank never retunes.** A material or vowel change does not move the bands of the bank in service: the
 * host installs the new table into a bank NOBODY HEARS and `KatalystFilterSwap` crossfades the two outputs. [install]
 * zeroes every band's state, so a reconfigured bank is bit for bit a bank built new on that table (the orbit EQ's
 * model since Katalyst 5c-11). The morph of Katalyst step 5c-10, which travelled the bands of the bank in service
 * instead, was REJECTED by the maintainer on 2026-09-20: a resonance that travels is an audible filter sweep ("an
 * 8-bit laser shot" on `body(material = "<wood glass>")`, the same on the vowel), and the click metric could not see
 * it, because a sweep is not a discontinuity. Do not bring a retune of a sounding bank back without that listening
 * being redone.
 *
 * **Storage** is flat and sized at construction: per band `a1`, `a2`, `a3`, `k`, the gain and the two integrators, for
 * [capacity] bands, and a wet scratch of `blockFrames`. [install] with more bands than that grows the arrays, and a
 * longer block grows the scratch, each once (a direct caller only: the engine's tables fit the capacity its hosts
 * ask for, and its blocks are `blockFrames` long). Nothing else allocates.
 */
class ResonatorBank(
    /** The number of bands the storage holds before [install] has to grow it. */
    capacity: Int,
    private val sampleRate: Double,
    /** The frames of one render block: sizes the wet scratch. */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : AudioFilter {

    private var a1 = DoubleArray(capacity)
    private var a2 = DoubleArray(capacity)
    private var a3 = DoubleArray(capacity)
    private var k = DoubleArray(capacity)
    private var gain = DoubleArray(capacity)
    private var ic1 = DoubleArray(capacity)
    private var ic2 = DoubleArray(capacity)

    /** The bands the last [install] configured; 0 before the first. */
    var count: Int = 0
        private set

    /** The mix `w`, clamped to `[0, 1]`; at 0 [process] bypasses. */
    private var amount: Double = 0.0
    private var dryGain: Double = 1.0
    private var wetGain: Double = 0.0

    private var wet: AudioBuffer = AudioBuffer(blockFrames)

    /** The coefficient holder [computeSvfCoeffs] writes into, one per bank. */
    private val coeffs = SvfCoeffs()

    /** The number of bands the storage holds now. */
    val capacity: Int get() = a1.size

    /**
     * Makes this bank a FRESH one on [config]: every band's coefficients and gain from its table, every integrator
     * zeroed, the blend's two coefficients from the wet/dry law. Only for a bank nobody hears (see the class KDoc); the
     * result is bit for bit a bank built new on the same values. A null table installs no bands (the wet is silence).
     *
     * The mix and the floor are coerced as the law says (non-finite reads 0, both clamped to `[0, 1]`); a host that
     * has its own rule for an unset knob substitutes it before calling. The bank reads [config] and keeps no reference
     * to it.
     */
    fun install(config: ResonatorConfig) {
        val table = config.table
        val n = table?.count ?: 0

        if (a1.size < n) {
            grow(n)
        }

        if (table != null) {
            for (b in 0 until n) {
                installBand(table = table, band = b)
            }
        }

        count = n
        installBlend(config)
    }

    /**
     * One band's coefficients, gain and zeroed state. Its own small method, and so is [installBlend], with no double
     * crossing either call: inlined into one `install`, the coefficient math used up V8's inlining budget, and the
     * calls left out boxed their doubles, about 140 bytes per change on a body (engine tidy-up step 12 (a)).
     */
    private fun installBand(table: ResonatorTable, band: Int) {
        computeSvfCoeffs(
            cutoffHz = clampSvfCutoff(cutoffHz = table.freq[band], sampleRate = sampleRate),
            q = clampSvfQ(table.q[band]),
            sampleRate = sampleRate,
            out = coeffs,
        )

        a1[band] = coeffs.a1
        a2[band] = coeffs.a2
        a3[band] = coeffs.a3
        k[band] = coeffs.k
        gain[band] = table.gain[band]
        ic1[band] = 0.0
        ic2[band] = 0.0
    }

    /** The blend's amount and its two coefficients, from [config]'s mix and floor by the wet/dry law. */
    private fun installBlend(config: ResonatorConfig) {
        val mix = config.mix

        amount = if (mix.isFinite()) mix.coerceIn(0.0, 1.0) else 0.0
        dryGain = WetDryMix.dryCoeff(w = amount, floor = config.floor, p = 2)
        wetGain = WetDryMix.wetCoeff(amount, p = 2)
    }

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // A mix at 0: out = dry. The buffer stays untouched (bit-identical) and no band runs.
        if (amount <= 0.0) {
            return
        }

        if (wet.size < length) {
            wet = AudioBuffer(length)
        }

        val w = wet

        // The first band sums into zero. No bands: the wet is silence.
        w.fill(0.0, 0, length)

        for (b in 0 until count) {
            runBand(band = b, buffer = buffer, offset = offset, length = length, wet = w)
        }

        val dg = dryGain
        val wg = wetGain

        for (i in 0 until length) {
            buffer[offset + i] = buffer[offset + i] * dg + w[i] * wg
        }
    }

    /**
     * One band over the block, its output added to [wet] with its gain. Its own method so `process` stays small (the
     * JVM inlines what it can see; step 11's lesson), and its state sits in locals for the loop and is written back
     * once.
     */
    private fun runBand(band: Int, buffer: AudioBuffer, offset: Int, length: Int, wet: AudioBuffer) {
        val c1 = a1[band]
        val c2 = a2[band]
        val c3 = a3[band]
        val kb = k[band]
        val g = gain[band]
        var s1 = ic1[band]
        var s2 = ic2[band]

        for (i in 0 until length) {
            val v0 = buffer[offset + i]
            val v3 = v0 - s2
            val v1 = c1 * s1 + c2 * v3
            val v2 = s2 + c2 * s1 + c3 * v3

            s1 = (2.0 * v1 - s1).flushState()
            s2 = (2.0 * v2 - s2).flushState()
            wet[i] = wet[i] + (kb * v1) * g
        }

        ic1[band] = s1
        ic2[band] = s2
    }

    /** Grows the band storage to [n] bands, for a direct caller's larger table; the old contents are dropped. */
    private fun grow(n: Int) {
        a1 = DoubleArray(n)
        a2 = DoubleArray(n)
        a3 = DoubleArray(n)
        k = DoubleArray(n)
        gain = DoubleArray(n)
        ic1 = DoubleArray(n)
        ic2 = DoubleArray(n)
    }
}
