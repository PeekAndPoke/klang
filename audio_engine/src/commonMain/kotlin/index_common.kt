package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.KlangAudioBackend
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import io.peekandpoke.klang.audio_engine.KlangPlayer.Options
import io.peekandpoke.klang.audio_fe.KlangEventSource

/**
 * Creates the default platform-specific audio loop backend.
 *
 * On JVM: Instantiates and runs [KlangAudioBackend] directly.
 * On JS:  Proxies commands to the AudioWorklet.
 */
expect fun createDefaultAudioLoop(
    options: KlangPlayer.Options,
): suspend (KlangPlayerState, KlangCommLink.BackendEndpoint) -> Unit

expect fun <T> klangPlayer(
    source: KlangEventSource<T>,
    transform: (T) -> ScheduledVoice,
    options: Options,
): KlangPlayer2<T>
