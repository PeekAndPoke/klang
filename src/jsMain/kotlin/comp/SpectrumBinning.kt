package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import org.khronos.webgl.get
import kotlin.math.*

/**
 * Converts raw linear-spaced FFT data into perceptually balanced spectrum buckets.
 *
 * Applies three corrections that professional spectrum analyzers use:
 * 1. Logarithmic frequency binning — each bucket covers an equal musical interval
 * 2. RMS aggregation — proper energy measurement when combining multiple FFT bins
 * 3. Slope compensation (+3 dB/octave) — counteracts the natural 1/f spectral rolloff
 *
 * The result is a display where typical music looks balanced across the frequency range,
 * rather than showing the misleading bass-heavy downward slope of raw FFT data.
 */
class SpectrumBinning(
    val numBuckets: Int,
    val fftBinCount: Int = 1024,
    val sampleRate: Double = 48000.0,
    val minFreq: Double = 30.0,
    val maxFreq: Double = 20000.0,
    /** Slope compensation in dB per octave. 3.0 flattens typical pink-noise-like music. */
    val slopeDbPerOctave: Double = 3.0,
    /** Reference frequency where slope compensation is 0 dB. */
    val referenceFreq: Double = 1000.0,
) {
    /** Precomputed start/end FFT bin indices for each bucket (log-spaced). */
    private val bucketRanges: Array<IntRange>

    /** Precomputed slope compensation in dB for each bucket's center frequency. */
    private val slopeCompensationDb: DoubleArray

    init {
        val fftBinWidth = sampleRate / (fftBinCount * 2) // Hz per bin
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)

        bucketRanges = Array(numBuckets) { i ->
            val freqLow = exp(logMin + (logMax - logMin) * i / numBuckets)
            val freqHigh = exp(logMin + (logMax - logMin) * (i + 1) / numBuckets)

            val binLow = (freqLow / fftBinWidth).toInt().coerceIn(0, fftBinCount - 1)
            val binHigh = (freqHigh / fftBinWidth).toInt().coerceIn(binLow, fftBinCount - 1)

            binLow..binHigh
        }

        slopeCompensationDb = DoubleArray(numBuckets) { i ->
            val freqCenter = exp(logMin + (logMax - logMin) * (i + 0.5) / numBuckets)
            slopeDbPerOctave * log2(freqCenter / referenceFreq)
        }
    }

    /**
     * Processes raw FFT dB data into [numBuckets] perceptually balanced values.
     *
     * @param fftBuffer Raw FFT data in dB (as returned by AnalyserNode.getFloatFrequencyData)
     * @param out Array of size [numBuckets] that receives normalized values in 0.0..1.0
     * @param dbFloor dB value that maps to 0.0 (silence)
     * @param dbCeil dB value that maps to 1.0 (maximum)
     */
    fun process(fftBuffer: AnalyzerBuffer, out: DoubleArray, dbFloor: Double = -99.0, dbCeil: Double = -10.0) {
        val range = dbCeil - dbFloor

        for (i in 0 until numBuckets) {
            val binRange = bucketRanges[i]
            val binCount = (binRange.last - binRange.first + 1).coerceAtLeast(1)

            // RMS aggregation: convert dB → linear, sum squares, average, back to dB
            var sumSquares = 0.0
            for (bin in binRange) {
                val dbValue = fftBuffer[bin].toDouble()
                val linear = 10.0.pow(dbValue / 20.0)
                sumSquares += linear * linear
            }
            val rmsDb = 20.0 * log10(sqrt(sumSquares / binCount).coerceAtLeast(1e-10))

            // Apply slope compensation, but not to silence — otherwise high-freq
            // buckets get boosted above the floor and show phantom bars.
            val compensatedDb = if (rmsDb < dbFloor) dbFloor else rmsDb + slopeCompensationDb[i]

            // Normalize to 0.0..1.0
            out[i] = ((compensatedDb - dbFloor) / range).coerceIn(0.0, 1.0)
        }
    }
}
