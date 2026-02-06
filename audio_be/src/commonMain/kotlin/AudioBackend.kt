package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope

interface AudioBackend {
    class Config(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockSize: Int,
    )

    /**
     * Access to visualization data.
     * Returns null if backend doesn't support visualization.
     */
    val visualizer: AudioVisualizer? get() = null

    suspend fun run(scope: CoroutineScope)
}
