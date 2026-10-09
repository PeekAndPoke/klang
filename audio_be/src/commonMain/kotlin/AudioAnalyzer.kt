/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.ultra.streams.Stream

/**
 * Audio visualization interface.
 * Provides real-time access to waveform and frequency data.
 */
interface AudioAnalyzer {
    /**
     * Stream of time-domain waveform data, emitted at a regular interval.
     * Values range from -1.0 to 1.0.
     * Buffer size equals the analyser's FFT size.
     */
    val waveform: Stream<AnalyzerBufferHistory>

    /**
     * Fills [out] buffer with frequency-domain FFT data.
     * Values are magnitudes in dB (typically -100 to 0).
     * Buffer size should equal half the analyser's FFT size (its frequency bin count).
     */
    fun getFft(out: AnalyzerBuffer)
}
