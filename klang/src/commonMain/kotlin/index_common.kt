package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlayer.Options
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.stdlib.KlangScriptOsc

/**
 * Creates a platform-specific KlangPlayer instance and suspends until the audio
 * backend has finished its warmup handshake. The returned player is ready to play.
 */
expect suspend fun klangPlayer(
    options: Options,
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

/**
 * Wires [player] into the [KlangScriptEngine.Builder] so a script's
 * `Osc.register("name", dsl)` calls forward custom ignitors to the running backend.
 *
 * ```
 * val engine = klangScript {
 *     setPlayer(player)
 *     registerLibrary(sprudelLib)
 * }
 * ```
 */
fun KlangScriptEngine.Builder.setPlayer(player: KlangPlayer) {
    attrs[KlangScriptOsc.REGISTRAR_KEY] = { name: String, dsl: IgnitorDsl ->
        player.sendControl(
            KlangCommLink.Cmd.RegisterIgnitor(
                playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
                name = name,
                dsl = dsl,
            )
        )
        name
    }
}
