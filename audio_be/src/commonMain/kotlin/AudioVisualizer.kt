package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.VisualizerBuffer

/**
 * Audio visualization interface.
 * Provides real-time access to waveform and frequency data.
 */
interface AudioVisualizer {
    /**
     * FFT size for analysis (e.g., 2048)
     */
    val fftSize: Int

    /**
     * Fills [out] buffer with time-domain waveform data.
     * Values range from -1.0 to 1.0.
     * Buffer size should equal fftSize.
     */
    fun getWaveform(out: VisualizerBuffer)

    /**
     * Fills [out] buffer with frequency-domain FFT data.
     * Values are magnitudes in dB (typically -100 to 0).
     * Buffer size should equal fftSize / 2 (frequencyBinCount).
     */
    fun getFft(out: VisualizerBuffer)
}