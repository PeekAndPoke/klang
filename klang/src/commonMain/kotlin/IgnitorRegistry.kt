package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Per-[KlangPlayer] tracker that announces inline [IgnitorDsl] trees to the audio backend
 * exactly once per unique DSL — gating on the process-wide [uniqueId] so two playbacks on
 * the same player share knowledge, and two distinct players each announce independently to
 * their own backends.
 *
 * Names themselves come from [IgnitorDsl.uniqueId] (process-wide, monotonic, never collide).
 * This class only holds the set of DSLs this player's backend has already heard about so
 * we don't re-send the same RegisterIgnitor command on every voice event.
 *
 * Internal to klang; callers reach it via
 * [KlangPlaybackContext.registerIgnitor] which delegates here.
 */
internal class IgnitorRegistry(
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val playbackId: String = KlangCommLink.SYSTEM_PLAYBACK_ID,
) {
    private val lock = KlangLock()
    private val sentToBackend = mutableSetOf<IgnitorDsl>()

    /** Number of unique DSLs already announced to this player's backend. */
    val size: Int get() = lock.withLock { sentToBackend.size }

    /**
     * Return the synthetic name for [dsl] (via the global [uniqueId] map) and, on first
     * sighting *by this player*, fire a [KlangCommLink.Cmd.RegisterIgnitor] to its backend.
     */
    fun registerOrLookup(dsl: IgnitorDsl): String {
        val name = dsl.uniqueId()
        val firstSighting = lock.withLock { sentToBackend.add(dsl) }
        if (firstSighting) {
            sendControl(
                KlangCommLink.Cmd.RegisterIgnitor(
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
