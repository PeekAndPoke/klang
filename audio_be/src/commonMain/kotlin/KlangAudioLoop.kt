package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

interface KlangAudioLoop {
    suspend fun runLoop(
        state: KlangPlayerState,
        commLink: KlangCommLink.BackendEndpoint,
        onCommand: (KlangCommLink.Cmd) -> Unit,
        renderBlock: (ByteArray) -> Unit,
    )
}

