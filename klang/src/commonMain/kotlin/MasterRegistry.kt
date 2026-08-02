/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Per-playback (playbackId-bound) tracker that announces inline [MasterDsl] chains to the audio
 * backend exactly once per unique chain — the master-side mirror of [PipelineRegistry]. The
 * [playbackId] stamps the command so the backend registers it on THAT playback's engine fork.
 *
 * Names come from [MasterDsl.uniqueId] (process-wide, monotonic, never collide).
 *
 * The send-once behaviour matters more here than for pipelines: a top-level `master(…)` re-emits
 * its control event **every cycle**, and all of those events carry the same structurally-equal
 * chain — so exactly one `RegisterMaster` is ever sent.
 *
 * Internal to klang; the owning `KlangPlaybackController` calls [registerOrLookup] directly.
 */
internal class MasterRegistry(
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val playbackId: String = KlangCommLink.SYSTEM_PLAYBACK_ID,
) {
    private val lock = KlangLock()
    private val sentToBackend = mutableSetOf<MasterDsl>()

    /** Number of unique masters already announced to this player's backend. */
    val size: Int get() = lock.withLock { sentToBackend.size }

    /**
     * Return the synthetic name for [dsl] (via the global [uniqueId] map) and, on first
     * sighting *by this player*, fire a [KlangCommLink.Cmd.RegisterMaster] to its backend.
     */
    fun registerOrLookup(dsl: MasterDsl): String {
        val name = dsl.uniqueId()
        val firstSighting = lock.withLock { sentToBackend.add(dsl) }
        if (firstSighting) {
            sendControl(
                KlangCommLink.Cmd.RegisterMaster(
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
