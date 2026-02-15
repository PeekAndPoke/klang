package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer

fun strudelPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    return klangPlayer(options = options)
}

/**
 * Start continuous Strudel playback that runs indefinitely until stopped.
 * This is the default mode for live coding.
 */
fun KlangPlayer.playStrudel(
    pattern: StrudelPattern,
): StrudelPlayback {
    val playback = ContinuousStrudelPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        onStopped = { unregisterPlayback(it) }
    )
    registerPlayback(playback)
    return playback
}

/**
 * Start one-shot Strudel playback that stops automatically after a specified number of cycles.
 * Useful for sample previews, auditioning, and other finite-length playback scenarios.
 *
 * @param pattern The Strudel pattern to play
 * @param cycles Number of cycles to play before stopping (default: 1)
 */
fun KlangPlayer.playStrudelOnce(
    pattern: StrudelPattern,
    cycles: Int = 1,
): StrudelPlayback {
    val playback = OneShotStrudelPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        onStopped = { unregisterPlayback(it) },
        cyclesToPlay = cycles,
    )
    registerPlayback(playback)
    return playback
}
