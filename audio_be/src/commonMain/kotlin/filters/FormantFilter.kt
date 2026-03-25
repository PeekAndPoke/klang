package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

/**
 * Formant filter for vowel synthesis.
 *
 * Implements multiple parallel bandpass filters tuned to vowel formant frequencies.
 * Each band has a center frequency, gain (in dB), and Q factor.
 *
 * Band buffers are pre-allocated to avoid GC pressure in the audio thread.
 */
class FormantFilter(
    bands: List<FilterDef.Formant.Band>,
    sampleRate: Double,
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { band ->
        val gain = 10.0.pow(band.db / 20.0)
        BandFilter(
            filter = LowPassHighPassFilters.SvfBPF(band.freq, band.q, sampleRate),
            gain = gain
        )
    }

    // Pre-allocated scratch buffers (resized once if block size changes)
    private var scratchBuffer: FloatArray = FloatArray(0)
    private var bandBuffer: FloatArray = FloatArray(0)

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        // Resize scratch buffers if needed (only on first call or block size change)
        if (scratchBuffer.size < length) {
            scratchBuffer = FloatArray(length)
            bandBuffer = FloatArray(length)
        }

        // 1. Copy input to scratch buffer (because we will overwrite 'buffer')
        buffer.copyInto(scratchBuffer, 0, offset, offset + length)

        // 2. Clear output buffer
        buffer.fill(0.0f, offset, offset + length)

        // 3. Process each band in parallel
        for (band in filters) {
            // Copy input for this band (reuse pre-allocated buffer)
            scratchBuffer.copyInto(bandBuffer, 0, 0, length)

            // Process through bandpass filter
            band.filter.process(bandBuffer, 0, length)

            // Sum into main buffer with gain
            for (i in 0 until length) {
                buffer[offset + i] = (buffer[offset + i] + bandBuffer[i] * band.gain).toFloat()
            }
        }
    }
}
