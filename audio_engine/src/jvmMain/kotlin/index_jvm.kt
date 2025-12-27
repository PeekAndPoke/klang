package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JvmKlangPlayerBackend2
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_fe.KlangEventSource
import kotlinx.coroutines.Dispatchers

/**
 * Create a KlangPlayer for the JVM
 */
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
