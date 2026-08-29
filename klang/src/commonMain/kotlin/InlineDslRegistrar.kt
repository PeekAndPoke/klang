/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId

/**
 * Per-playback bookkeeping: makes sure every inline DSL a playback references is known to the
 * backend under its synthetic name, announced exactly once.
 *
 * **This belongs to the PLAYBACK, not to the scheduler.** Every kind of playback needs it —
 * cyclic patterns discover inline DSLs while querying, and a realtime playback needs the same to
 * play a user-authored ignitor (the MIDI playground's editor). It used to live inside
 * `KlangPatternScheduler`, which meant the realtime path silently could not use inline DSLs at
 * all.
 *
 * Shared by **composition, not inheritance**: the offline renderer needs exactly this too and is
 * not a `KlangPlayback`, so a base class could never cover it. Swap [ignitors]/[pipelines]/
 * [masters]' sinks (see [AnnounceOnceRegistry]) and the same object serves an in-process
 * renderer.
 *
 * The synthetic names come from the process-wide `uniqueId()` maps in `audio_bridge`; this class
 * only owns the per-playback "have I announced it yet" gate. Both sides free it when the playback
 * stops — it mirrors the backend's per-`PlaybackEngine` registry forks.
 */
internal class InlineDslRegistrar(
    val ignitors: AnnounceOnceRegistry<IgnitorDsl>,
    val pipelines: AnnounceOnceRegistry<PipelineDsl>,
    val masters: AnnounceOnceRegistry<MasterDsl>,
) {
    companion object {
        /**
         * The live wiring: first sighting of a DSL fires the matching `Cmd.Register*`, stamped
         * with [playbackId] so the backend registers it on THAT playback's engine fork.
         */
        fun overWire(
            playbackId: String,
            sendControl: (KlangCommLink.Cmd) -> Unit,
        ): InlineDslRegistrar = InlineDslRegistrar(
            ignitors = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl ->
                    sendControl(KlangCommLink.Cmd.RegisterIgnitor(playbackId = playbackId, name = name, dsl = dsl))
                },
            ),
            pipelines = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl ->
                    sendControl(KlangCommLink.Cmd.RegisterPipeline(playbackId = playbackId, name = name, dsl = dsl))
                },
            ),
            masters = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl ->
                    sendControl(KlangCommLink.Cmd.RegisterMaster(playbackId = playbackId, name = name, dsl = dsl))
                },
            ),
        )
    }

    /**
     * Announces every inline DSL referenced by [events].
     *
     * **Ordering is load-bearing:** callers must run this BEFORE scheduling those events, so the
     * synthetic names resolve on the backend by the time the voices referencing them arrive.
     *
     * This sweep is the one that used to be hand-written twice (in the scheduler's query path and
     * again in the offline renderer) — two copies that had to be kept in step by memory. Adding a
     * new inline-DSL kind now means touching this method only.
     */
    fun announceAll(events: List<KlangPatternEvent>) {
        events.asSequence()
            .map { it.sound }
            .filterIsInstance<SoundValue.Osc>()
            .forEach { ignitors.registerOrLookup(it.osc) }

        events.asSequence()
            .map { it.pipeline }
            .filterIsInstance<PipelineValue.Dsl>()
            .forEach { pipelines.registerOrLookup(it.pipeline) }

        events.asSequence()
            .map { it.master }
            .filterIsInstance<MasterValue.Dsl>()
            .forEach { masters.registerOrLookup(it.master) }
    }
}
