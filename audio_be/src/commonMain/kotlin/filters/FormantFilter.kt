package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.pow

/**
 * Formant filter for vowel synthesis.
 *
 * Implements multiple parallel bandpass filters tuned to vowel formant frequencies.
 * Each band has a center frequency, gain (in dB), and Q factor.
 */
class FormantFilter(
    bands: List<FilterDef.Formant.Band>,
    sampleRate: Double,
) : AudioFilter {

    private data class BandFilter(val filter: LowPassHighPassFilters.SvfBPF, val gain: Double)

    private val filters = bands.map { band ->
        // Convert dB to linear gain: 10^(db/20)
        val gain = 10.0.pow(band.db / 20.0)
        BandFilter(
            filter = LowPassHighPassFilters.SvfBPF(band.freq, band.q, sampleRate),
            gain = gain
        )
    }

    // Scratch buffer to avoid allocation in process loop
    private var scratchBuffer: DoubleArray = DoubleArray(0)

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Resize scratch buffer if needed
        if (scratchBuffer.size < length) {
            scratchBuffer = DoubleArray(length)
        }

        // 1. Copy input to scratch buffer (because we will overwrite 'buffer')
        buffer.copyInto(scratchBuffer, 0, offset, offset + length)

        // 2. Clear output buffer
        buffer.fill(0.0, offset, offset + length)

        // 3. Process each band in parallel
        for (band in filters) {
            // We need a clean copy of input for each parallel filter
            // Create temporary buffer for this band
            val bandBuffer = DoubleArray(length)
            scratchBuffer.copyInto(bandBuffer, 0, 0, length)

            // Process through bandpass filter
            band.filter.process(bandBuffer, 0, length)

            // Sum into main buffer with gain
            for (i in 0 until length) {
                buffer[offset + i] += bandBuffer[i] * band.gain
            }
        }
    }
}
