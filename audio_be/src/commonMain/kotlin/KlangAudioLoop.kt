package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

interface KlangAudioLoop {
    suspend fun runLoop(
        state: KlangPlayerState,
        commLink: KlangCommLink.BackendEndpoint,
        onSchedule: (ScheduledVoice) -> Unit,
        renderBlock: (ByteArray) -> Unit,
    )
}

