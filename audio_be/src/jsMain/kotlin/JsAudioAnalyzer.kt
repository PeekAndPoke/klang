package io.peekandpoke.klang.audio_be

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.ops.map
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBuffer
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import kotlin.time.Duration.Companion.milliseconds

class JsAudioAnalyzer(
    override val fftSize: Int = 2048,
    private val node: () -> AnalyserNode?,
) : AudioAnalyzer {

    private val history = AnalyzerBufferHistory(fftSize, 10)

    override val waveform: Stream<AnalyzerBufferHistory> = ticker(16.milliseconds).map {
        node()?.getFloatTimeDomainData(history.nextBuffer())

        history
    }

    override fun getFft(out: AnalyzerBuffer) {
        // Zero-copy fill on JS
        node()?.getFloatFrequencyData(out)
    }
}
