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
 * gain means is decided where a table row becomes a [Band]: [LowPassHighPassFilters.bodyBand]
 * and [LowPassHighPassFilters.vowelBand].
 *
 * This is a **pure wet** filter: its output is the resonance, nothing else. The dry/wet blend
 * and the floor live in the [ParallelMixFilter] that wraps it (`createBody` / `createFormant`).
 *
 * **NaN safety:** a band's `freq` and `q` go to the SVF raw and are guarded there (`bilinearK` /
 * `computeSvfCoeffs`); a band's gain is used as given, and each mapping guards a non-finite dB.
 *
 * **DC:** each SVF bandpass has DC gain 0, so the sum stays 0 at DC.
 *
 * **Output normalization:** none, N coherent peaks are summed without `1/N` scaling. Relies on
 * the master softCap/limiter. Scratch buffers are per instance and grow once if the block does.
 */
class ResonatorBank(
    bands: List<Band>,
    sampleRate: Double,
) : AudioFilter {

    /** One band: centre frequency in Hz, the SVF's q (a pure width control), a linear gain. */
    data class Band(val freq: Double, val q: Double, val gain: Double)

    private val filters = Array(bands.size) { LowPassHighPassFilters.SvfBPF(bands[it].freq, bands[it].q, sampleRate) }
    private val gains = DoubleArray(bands.size) { bands[it].gain }

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
        for (b in filters.indices) {
            val gain = gains[b]
            inputCopy.copyInto(bandBuffer, 0, 0, length)
            filters[b].process(bandBuffer, 0, length)

            for (i in 0 until length) {
                buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * gain
            }
        }
    }
}
