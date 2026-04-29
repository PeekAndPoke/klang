package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

/**
 * Formant filter for vowel synthesis. Parallel bandpass cascade — one [SvfBPF][LowPassHighPassFilters.SvfBPF]
 * per formant band, outputs summed.
 *
 * **Per-band gain semantics (constant-skirt BPF):** the SVF bandpass tap (`v1`) follows
 * the standard SVF convention where peak gain at fc equals `Q`. The user-facing `band.db`
 * parameter is *additional* gain on top of that intrinsic Q peak. So a band configured
 * with `freq = 730, q = 10, db = 0` produces a peak of **+20 dB** (≈ Q) at 730 Hz, not
 * 0 dB. Existing vowel tables in `SprudelVoiceData` are tuned with this convention —
 * F1 typically has `db = 0` and upper formants use negative dB to compensate.
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
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { band ->
        // dB → linear, with NaN/Inf guard (matches Round-1+ pattern in `bilinearK`).
        val safeDb = if (band.db.isFinite()) band.db else 0.0
        val gain = 10.0.pow(safeDb / 20.0)
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
