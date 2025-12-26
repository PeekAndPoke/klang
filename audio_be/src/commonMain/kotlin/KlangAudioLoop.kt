package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.KlangEventReceiver
import io.peekandpoke.klang.audio_bridge.KlangPlayerState

interface KlangAudioLoop<S> {
    suspend fun runLoop(
        state: KlangPlayerState,
        channel: KlangEventReceiver<S>,
        onSchedule: (S) -> Unit,
        renderBlock: (ByteArray) -> Unit,
    )
}

