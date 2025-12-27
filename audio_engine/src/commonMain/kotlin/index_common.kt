package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_engine.KlangPlayer.Options
import io.peekandpoke.klang.audio_fe.KlangEventSource

/**
 * Create a platform specific KlangPlayer instance
 */
expect fun <T> klangPlayer(
    source: KlangEventSource<T>,
    transform: (T) -> ScheduledVoice,
    options: Options,
): KlangPlayer2<T>
