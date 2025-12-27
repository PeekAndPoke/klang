package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.CoroutineScope

interface KlangPlayerBackend {
    class Config(
        val state: KlangPlayerState,
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockSize: Int,
    )

    suspend fun run(scope: CoroutineScope)
}
