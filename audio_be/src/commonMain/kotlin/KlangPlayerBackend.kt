package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope

interface KlangPlayerBackend {
    class Config(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockSize: Int,
    )

    suspend fun run(scope: CoroutineScope)
}
