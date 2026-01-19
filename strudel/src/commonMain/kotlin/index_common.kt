package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer

fun strudelPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    return klangPlayer(options = options)
}

fun KlangPlayer.playStrudel(
    pattern: StrudelPattern,
): StrudelPlayback {
    val playback = StrudelPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        playerOptions = options,
        commLink = commLink,
        scope = playbackScope,
        fetcherDispatcher = playbackFetcherDispatcher,
        callbackDispatcher = playbackCallbackDispatcher,
        onStopped = { unregisterPlayback(it) }
    )
    registerPlayback(playback)
    return playback
}
