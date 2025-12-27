package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JvmKlangPlayerBackend2
import io.peekandpoke.klang.audio_be.KlangAudioBackend
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import io.peekandpoke.klang.audio_fe.KlangEventSource
import kotlinx.coroutines.Dispatchers

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

actual fun <T> klangPlayer(
    source: KlangEventSource<T>,
    transform: (T) -> ScheduledVoice,
    options: KlangPlayer.Options,
): KlangPlayer2<T> {
    return KlangPlayer2(
        source = source,
        transform = transform,
        options = options,
        backendFactory = { config -> JvmKlangPlayerBackend2(config) },
        fetcherDispatcher = Dispatchers.IO,
        backendDispatcher = Dispatchers.IO,
    )
}
