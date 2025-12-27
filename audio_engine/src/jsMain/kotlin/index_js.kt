package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

actual fun createDefaultAudioLoop(
    options: KlangPlayer.Options,
): suspend (KlangPlayerState, KlangCommLink.BackendEndpoint) -> Unit = { state, commLink ->

    TODO("Not yet implemented")
}
