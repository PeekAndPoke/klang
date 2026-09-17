/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.master.MasterRegistry
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
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
 * [masters]/[katalysts]' sinks (see [AnnounceOnceRegistry]) and the same object serves an
 * in-process renderer.
 *
 * The synthetic names come from the process-wide `uniqueId()` maps in `audio_bridge`; this class
 * only owns the per-playback "have I announced it yet" gate. Both sides free it when the playback
 * stops — it mirrors the backend's per-`PlaybackEngine` registry forks.
 */
internal class InlineDslRegistrar(
    val ignitors: AnnounceOnceRegistry<IgnitorDsl>,
    val pipelines: AnnounceOnceRegistry<PipelineDsl>,
    val masters: AnnounceOnceRegistry<MasterDsl>,
    val katalysts: AnnounceOnceRegistry<KatalystValue.Dsl>,
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
            // Keyed on the VALUE, not the chain: the value memoizes its own `uniqueId()`, so the
            // sweep costs a field read per event instead of a structural hash of the stage list.
            katalysts = AnnounceOnceRegistry(
                uniqueId = { it.name },
                announce = { name, value ->
                    sendControl(
                        KlangCommLink.Cmd.RegisterKatalyst(playbackId = playbackId, name = name, dsl = value.katalyst)
                    )
                },
            ),
        )

        /**
         * The in-process wiring: first sighting of a DSL registers it straight into the backend's
         * PARENT registries, with no `Cmd.Register*` and no wire.
         *
         * This is what the class KDoc means by "swap the sinks": the offline renderer owns a single
         * engine and can write to its registries directly, so it gets the same announce-once
         * bookkeeping and, more to the point, the same [announceAll] sweep. It used to hand-write
         * that sweep, which is how the sweep came to exist in two places in the first place.
         *
         * An ignitor name that is already taken is left alone: the built-in sounds are seeded in
         * the same registry, and a synthetic `osc-N` must never shadow one.
         */
        fun intoRegistries(
            ignitors: IgnitorRegistry,
            pipelines: PipelineRegistry,
            masters: MasterRegistry,
            katalysts: KatalystRegistry,
        ): InlineDslRegistrar = InlineDslRegistrar(
            ignitors = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl ->
                    if (!ignitors.contains(name)) {
                        ignitors.register(name, dsl)
                    }
                },
            ),
            pipelines = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl -> pipelines.register(name, dsl) },
            ),
            masters = AnnounceOnceRegistry(
                uniqueId = { it.uniqueId() },
                announce = { name, dsl -> masters.register(name, dsl) },
            ),
            katalysts = AnnounceOnceRegistry(
                uniqueId = { it.name },
                announce = { name, value -> katalysts.register(name, value.katalyst) },
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
     * again in the offline renderer): two copies that had to be kept in step by memory. Adding a
     * new inline-DSL kind now means touching this method and the two sink factories above, and
     * nothing outside this file. The offline renderer reaches it through [intoRegistries]; before
     * 2026-09-17 it still had its own copy of the sweep, and the claim was only true for the live
     * path.
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

        events.asSequence()
            .map { it.katalyst }
            .filterIsInstance<KatalystValue.Dsl>()
            .forEach { katalysts.registerOrLookup(it) }
    }
}
