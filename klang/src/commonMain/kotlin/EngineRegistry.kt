package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Per-[KlangPlayer] tracker that announces inline [EngineDsl] trees to the audio backend
 * exactly once per unique engine — the engine-side mirror of [IgnitorRegistry].
 *
 * Names come from [EngineDsl.uniqueId] (process-wide, monotonic, never collide). This class
 * only holds the set of engines this player's backend has already heard about, so we don't
 * re-send the same RegisterEngine command on every voice event.
 *
 * Internal to klang; callers reach it via [KlangPlaybackContext.registerEngine].
 */
internal class EngineRegistry(
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val playbackId: String = KlangCommLink.SYSTEM_PLAYBACK_ID,
) {
    private val lock = KlangLock()
    private val sentToBackend = mutableSetOf<EngineDsl>()

    /** Number of unique engines already announced to this player's backend. */
    val size: Int get() = lock.withLock { sentToBackend.size }

    /**
     * Return the synthetic name for [dsl] (via the global [uniqueId] map) and, on first
     * sighting *by this player*, fire a [KlangCommLink.Cmd.RegisterEngine] to its backend.
     */
    fun registerOrLookup(dsl: EngineDsl): String {
        val name = dsl.uniqueId()
        val firstSighting = lock.withLock { sentToBackend.add(dsl) }
        if (firstSighting) {
            sendControl(
                KlangCommLink.Cmd.RegisterEngine(
                    playbackId = playbackId,
                    name = name,
                    dsl = dsl,
                )
            )
        }
        return name
    }

    /** Clear the per-player sent set. Does not affect the global [uniqueId] map. */
    fun clear(): Unit = lock.withLock {
        sentToBackend.clear()
    }
}
