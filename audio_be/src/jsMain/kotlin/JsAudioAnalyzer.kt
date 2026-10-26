/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.animTicker
import io.peekandpoke.ultra.streams.ops.map

/**
 * The analysis the visualisations read: the [waveform] (the scope) from [waveformNode], the spectrum ([getFft]) from
 * [fftNode]. Two taps, so each can sit where it belongs in the graph (KLANG-00346: the scope after the master volume,
 * the spectrum before it).
 */
class JsAudioAnalyzer(
    fftSize: Int = 2048,
    private val waveformNode: () -> AnalyserNode?,
    private val fftNode: () -> AnalyserNode?,
) : AudioAnalyzer {

    private val history = AnalyzerBufferHistory(bufferSize = fftSize, capacity = HISTORY_CAPACITY)

    companion object {
        private const val HISTORY_CAPACITY = 50
    }

    override val waveform: Stream<AnalyzerBufferHistory> = animTicker().map {
        waveformNode()?.getFloatTimeDomainData(history.nextBuffer())

        history
    }

    override fun getFft(out: AnalyzerBuffer) {
        // Zero-copy fill on JS
        fftNode()?.getFloatFrequencyData(out)
    }
}
