package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.animTicker
import io.peekandpoke.ultra.streams.ops.map

class JsAudioAnalyzer(
    override val fftSize: Int = 2048,
    private val node: () -> AnalyserNode?,
) : AudioAnalyzer {

    private val history = AnalyzerBufferHistory(fftSize, HISTORY_CAPACITY)

    companion object {
        private const val HISTORY_CAPACITY = 60
    }

    override val waveform: Stream<AnalyzerBufferHistory> = animTicker().map {
        node()?.getFloatTimeDomainData(history.nextBuffer())

        history
    }

    override fun getFft(out: AnalyzerBuffer) {
        // Zero-copy fill on JS
        node()?.getFloatFrequencyData(out)
    }
}
