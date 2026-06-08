package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

/**
 * Body resonator — a parallel [SvfBPF][LowPassHighPassFilters.SvfBPF] bank (one per mode), output
 * summed. This is a **pure wet** filter with the same `AudioFilter` API as every other filter:
 * its output is the resonance, nothing else. The dry/wet blend is NOT done here — a separate
 * pipeline component ([ParallelMixFilter]) adds this wet signal on top of the dry source. Same
 * structure as [FormantFilter]; the only difference is the band source ([FilterDef.Body.Mode]).
 *
 * **Fixed Hz centers:** mode frequencies are absolute and do NOT track the played note. That is
 * deliberate — fixed resonances emphasize different harmonics for different notes, breaking the
 * spectral "lockstep" that makes a pure source sound plastic.
 *
 * **Per-band gain semantics:** the SVF bandpass tap (`v1`) peaks at `Q` at fc; the per-band gain
 * divides that out (`10^(db/20) / Q`) so `mode.db` is the *actual* peak emphasis in dB,
 * independent of the mode's sharpness (`Q`). Real bodies emphasize modes by ~3–10 dB, not 20+.
 *
 * **NaN safety:** `mode.freq`/`mode.q` are guarded inside the SVF (`bilinearK` /
 * `computeSvfCoeffs`); `mode.db` is guarded here (non-finite → 0 dB).
 *
 * **Output normalization:** none — N coherent peaks summed without `1/N` scaling, like
 * [FormantFilter]. Relies on the master softCap/limiter. Pre-allocated scratch buffers avoid GC
 * pressure in the audio thread.
 */
class BodyFilter(
    bands: List<FilterDef.Body.Mode>,
    sampleRate: Double,
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { mode ->
        // dB → linear, with NaN/Inf guard. Divide by Q to cancel the constant-skirt BPF's
        // intrinsic peak gain (= Q), so `db` is the actual peak emphasis in dB, independent of
        // sharpness. Match the SVF's own Q clamp so the cancellation is exact.
        val safeDb = if (mode.db.isFinite()) mode.db else 0.0
        val safeQ = if (mode.q.isFinite()) mode.q.coerceIn(0.1, 200.0) else 0.7071067811865475
        BandFilter(
            filter = LowPassHighPassFilters.SvfBPF(mode.freq, mode.q, sampleRate),
            gain = 10.0.pow(safeDb / 20.0) / safeQ,
        )
    }

    // Pre-allocated scratch buffers (resized once if block size grows). Per-instance,
    // lifetime-spanning — the strip-pipeline `AudioFilter` surface has no scratch pool.
    private var inputCopy: AudioBuffer = AudioBuffer(0)
    private var bandBuffer: AudioBuffer = AudioBuffer(0)

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        if (inputCopy.size < length) {
            inputCopy = AudioBuffer(length)
            bandBuffer = AudioBuffer(length)
        }

        // 1. Copy input to scratch (we overwrite `buffer` with the band sum below).
        buffer.copyInto(inputCopy, 0, offset, offset + length)

        // 2. Clear the output region — the first band sums into zero. Empty bands → silence.
        buffer.fill(0.0, offset, offset + length)

        // 3. Process each band in parallel; sum into the output buffer with gain.
        for (band in filters) {
            inputCopy.copyInto(bandBuffer, 0, 0, length)
            band.filter.process(bandBuffer, 0, length)
            for (i in 0 until length) {
                buffer[offset + i] = buffer[offset + i] + bandBuffer[i] * band.gain
            }
        }
    }
}
