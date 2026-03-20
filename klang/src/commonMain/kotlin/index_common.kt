package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_engine.KlangPlayer.Options

/**
 * Creates a platform-specific KlangPlayer instance
 */
expect fun klangPlayer(
    options: Options,
    onReady: (KlangPlayer) -> Unit,
): KlangPlayer

/**
 * Start continuous playback that runs indefinitely until stopped.
 * This is the default mode for live coding.
 */
fun KlangPlayer.play(
    pattern: KlangPattern,
): KlangCyclicPlayback {
    lateinit var playback: KlangCyclicPlayback
    playback = ContinuousPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        onStarted = { registerPlayback(playback) },
        onStopped = { unregisterPlayback(playback) },
    )
    return playback
}

/**
 * Start one-shot playback that stops automatically after a specified number of cycles.
 * Useful for sample previews, auditioning, and other finite-length playback scenarios.
 *
 * @param pattern The pattern to play
 * @param cycles Number of cycles to play before stopping (default: 1)
 */
fun KlangPlayer.playOnce(
    pattern: KlangPattern,
    cycles: Int = 1,
): KlangCyclicPlayback {
    lateinit var playback: KlangCyclicPlayback
    playback = OneShotPlayback(
        playbackId = generatePlaybackId(),
        pattern = pattern,
        context = playbackContext,
        cyclesToPlay = cycles,
        onStarted = { registerPlayback(playback) },
        onStopped = { unregisterPlayback(playback) },
    )
    return playback
}
