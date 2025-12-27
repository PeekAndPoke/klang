package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.KlangAudioBackend
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

actual fun createDefaultAudioLoop(
    options: KlangPlayer.Options,
): suspend (KlangPlayerState, KlangCommLink.BackendEndpoint) -> Unit = { state, commLink ->

    // Use the generic backend from audio_be
    val backend = KlangAudioBackend(
        sampleRate = options.sampleRate,
        blockFrames = options.blockSize
    )

    backend.run(state, commLink)
}
