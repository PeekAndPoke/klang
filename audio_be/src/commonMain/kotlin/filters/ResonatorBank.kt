/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Resonator bank: a parallel bank of [SvfBPF][LowPassHighPassFilters.SvfBPF] bandpasses, each
 * band scaled by its own LINEAR gain, the outputs summed. The one core behind `body(...)` and
 * `vowel(...)` (Katalyst step 5c-3, 2026-09-19); it knows nothing about either. What a band's
 * gain means is decided where a table row becomes a band:
 * [LowPassHighPassFilters.bodyGain] and [LowPassHighPassFilters.vowelGain].
 *
 * This is a **pure wet** filter: its output is the resonance, nothing else. The dry/wet blend
 * and the floor live in the [ParallelMixFilter] that wraps it (`createBody` / `createFormant`).
 *
 * **A bank never changes.** Its bands are what it was built with, for its whole life: a material
 * or vowel change builds a NEW bank and
 * [KatalystFilterSwap][io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFilterSwap]
 * crossfades the two outputs. The morph of Katalyst step 5c-10, which travelled the bands of the
 * bank in service instead, was REJECTED by the maintainer on 2026-09-20: a resonance that travels
 * is an audible filter sweep ("an 8-bit laser shot" on `body("<wood glass>")`, the same on the
 * vowel), and the click metric could not see it, because a sweep is not a discontinuity. Do not
 * bring a retune back into this class without that listening being redone.
 *
 * **NaN safety:** a band's `freq` and `q` are guarded HERE, with the SVF's own
 * [clampSvfCutoff] / [clampSvfQ], so the coefficients are the same doubles the raw values would
 * have produced (the SVF's own clamps are then no-ops). A band's gain is used as given, and each
 * mapping guards a non-finite dB.
 *
 * **DC:** each SVF bandpass has DC gain 0, so the sum stays 0 at DC.
 *
 * **Output normalization:** none, N coherent peaks are summed without `1/N` scaling. Relies on
 * the master softCap/limiter. Scratch buffers are per instance and grow once if the block does.
 */
class ResonatorBank(
    bands: List<Band>,
    private val sampleRate: Double,
) : AudioFilter {

    /** One band: centre frequency in Hz, the SVF's q (a pure width control), a linear gain. */
    data class Band(val freq: Double, val q: Double, val gain: Double)

    private val count: Int = bands.size

    private val filters =
        Array(count) { LowPassHighPassFilters.SvfBPF(freqOf(bands, it), qOf(bands, it), sampleRate) }

    private val gains = DoubleArray(count) { bands[it].gain }

    private var inputCopy: AudioBuffer = AudioBuffer(0)
    private var bandBuffer: AudioBuffer = AudioBuffer(0)

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        if (inputCopy.size < length) {
            inputCopy = AudioBuffer(length)
            bandBuffer = AudioBuffer(length)
        }

        // 1. Copy the input to scratch (we overwrite `buffer` with the band sum below).
        buffer.copyInto(inputCopy, 0, offset, offset + length)

        // 2. Clear the output region: the first band sums into zero. No bands: silence.
        buffer.fill(0.0, offset, offset + length)

        // 3. Run each band on its own copy of the input; sum into the output with its gain.
        for (b in 0 until count) {
            inputCopy.copyInto(bandBuffer, 0, 0, length)
            filters[b].process(bandBuffer, 0, length)

            val gain = gains[b]

            for (i in 0 until length) {
                buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * gain
            }
        }
    }

    /** The SVF's own cutoff guard, applied here so a band is built from a finite number. */
    private fun freqOf(bands: List<Band>, index: Int): Double = clampSvfCutoff(bands[index].freq, sampleRate)

    /** The SVF's own q guard, for the same reason as [freqOf]. */
    private fun qOf(bands: List<Band>, index: Int): Double = clampSvfQ(bands[index].q)
}
