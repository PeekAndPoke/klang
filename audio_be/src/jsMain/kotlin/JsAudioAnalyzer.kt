package io.peekandpoke.klang.audio_be

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.ops.animTicker
import de.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory

class JsAudioAnalyzer(
    override val fftSize: Int = 2048,
    private val node: () -> AnalyserNode?,
) : AudioAnalyzer {

    private val history = AnalyzerBufferHistory(fftSize, 10)

    override val waveform: Stream<AnalyzerBufferHistory> = animTicker().map {
        node()?.getFloatTimeDomainData(history.nextBuffer())

        history
    }

    override fun getFft(out: AnalyzerBuffer) {
        // Zero-copy fill on JS
        node()?.getFloatFrequencyData(out)
    }
}
