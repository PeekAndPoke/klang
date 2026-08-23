/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

/**
 * Formant filter for vowel synthesis. Parallel bandpass cascade — one [SvfBPF][LowPassHighPassFilters.SvfBPF]
 * per formant band, outputs summed.
 *
 * **Per-band gain semantics (legacy Q-peak convention, folded):** since C2 of the filter
 * unification the underlying [SvfBPF][LowPassHighPassFilters.SvfBPF] is UNITY-peak at fc
 * (q is a pure width control). The vowel/body tables however were tuned against the old
 * convention where the peak at fc equalled `Q` and `band.db` was additional gain on top.
 * To keep every table sounding identical (algebraically exact; sub-ulp in floats),
 * the constructor folds the legacy peak back in:
 * `gain = 10^(db/20) * clampedQ`. This is EXACT (the SVF scales by k = 1/clampedQ, and
 * k * q = 1), so `freq = 730, q = 10, db = 0` still peaks at +20 dB here. Flipping the
 * tables to the absolute-peak convention (db = peak at fc) is a deferred follow-up.
 *
 * **Q range**: as of 2026-04-29 the SVF accepts `q ∈ [0.1, 200.0]`; vowel tables use
 * Q=60–130 per band, which now produce the intended sharp resonances. Before 2026-04-29
 * the SVF clamped at Q=50, silently flattening every vowel.
 *
 * **NaN safety**: `band.freq` and `band.q` are guarded inside the SVF (`bilinearK` /
 * `computeSvfCoeffs`). `band.db` is guarded here at construction time — non-finite dB
 * falls back to 0 dB unity gain.
 *
 * **DC behavior**: each BPF tap has DC gain 0 (SVF topology); sum stays 0 at DC.
 * No downstream DC blocker needed for this stage.
 *
 * **Output normalization**: none — N coherent peaks summed without `1/N` scaling.
 * Worst-case peak ≈ N × Q × 10^(maxDb/20). Relies on the master softCap/limiter.
 *
 * Pre-allocated scratch buffers avoid GC pressure in the audio thread.
 */
class FormantFilter(
    bands: List<FilterDef.Formant.Band>,
    sampleRate: Double,
    // Overall output scale applied to every band. Default 1.0 (unchanged) for any standalone
    // use. The vowel path passes a small value to tame the Q-scaled peaks (the BPF peak is
    // `Q·10^(db/20)`, and vowel tables use Q=80–140 → ~+38 dB) down to ~unity, so the bank can
    // be blended with the dry source by `ParallelMixFilter`. A single scale preserves each
    // vowel's tuned relative balance exactly — only the overall level changes.
    gainScale: Double = 1.0,
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { band ->
        // dB → linear, with NaN/Inf guard (matches Round-1+ pattern in `bilinearK`).
        val safeDb = if (band.db.isFinite()) band.db else 0.0
        // Legacy Q-peak fold (see class KDoc): the SVF is unity-peak since C2, the tables
        // are tuned to the old Q-peak convention — multiplying by the SAME clamped q the
        // SVF uses makes the fold exact (k * q = 1).
        val safeQ = if (band.q.isFinite()) band.q.coerceIn(0.1, 200.0) else 0.7071067811865475
        val gain = 10.0.pow(safeDb / 20.0) * safeQ * gainScale
        BandFilter(
            filter = LowPassHighPassFilters.SvfBPF(band.freq, band.q, sampleRate),
            gain = gain
        )
    }

    // Pre-allocated scratch buffers (resized once if block size changes).
    // NOT the same as the per-voice `ScratchBuffers` pool — those are stack-discipline-scoped
    // per `IgniteContext`; this is per-instance, lifetime-spanning, and the strip-pipeline
    // `AudioFilter` surface has no `IgniteContext` plumbed in.
    private var inputCopy: AudioBuffer = AudioBuffer(0)
    private var bandBuffer: AudioBuffer = AudioBuffer(0)

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // Resize scratch buffers if needed (only on first call or block size growth).
        if (inputCopy.size < length) {
            inputCopy = AudioBuffer(length)
            bandBuffer = AudioBuffer(length)
        }

        // 1. Copy input to scratch (because we will overwrite `buffer` per band).
        buffer.copyInto(inputCopy, 0, offset, offset + length)

        // 2. Clear output region — first band will sum into zero.
        // (Empty bands list → output is silence after this fill, no accumulation.
        //  Existing semantic, preserved.)
        buffer.fill(0.0, offset, offset + length)

        // 3. Process each band in parallel; sum into main buffer with gain.
        for (band in filters) {
            inputCopy.copyInto(bandBuffer, 0, 0, length)
            band.filter.process(bandBuffer, 0, length)
            for (i in 0 until length) {
                buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * band.gain
            }
        }
    }
}
