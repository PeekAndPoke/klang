package io.peekandpoke.klang.audio_be

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.ops.map
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.VisualizerBuffer
import kotlin.time.Duration.Companion.milliseconds

class JsAudioAnalyzer(
    override val fftSize: Int = 2048,
    private val node: () -> AnalyserNode?,
) : AudioAnalyzer {

    private val buffer = VisualizerBuffer(fftSize)

    override val waveform: Stream<VisualizerBuffer> = ticker(16.milliseconds).map {
        node()?.getFloatTimeDomainData(buffer)

        buffer
    }

    override fun getFft(out: VisualizerBuffer) {
        // Zero-copy fill on JS
        node()?.getFloatFrequencyData(out)
    }
}
