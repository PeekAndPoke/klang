package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JsKlangPlayerBackend
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
): KlangPlayer<T> {
    return KlangPlayer(
        source = source,
        transform = transform,
        options = options,
        backendFactory = { config -> JsKlangPlayerBackend(config) },
        fetcherDispatcher = Dispatchers.Default,
        backendDispatcher = Dispatchers.Default,
    )
}
