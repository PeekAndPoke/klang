package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.KlangAudioBackend
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

/**
 * Creates the default platform-specific audio loop backend.
 *
 * On JVM: Instantiates and runs [KlangAudioBackend] directly.
 * On JS:  Proxies commands to the AudioWorklet.
 */
expect fun createDefaultAudioLoop(
    options: KlangPlayer.Options,
): suspend (KlangPlayerState, KlangCommLink.BackendEndpoint) -> Unit
