/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
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
 * Shared by **composition, not inheritance**: a realtime playback needs exactly this too, and the
 * offline renderer, on the real playback path since KLANG-00399 step 4, sends over the wire like any playback. Swap
 * [ignitors]/[katalysts]' sinks (see [AnnounceOnceRegistry]) and the same object serves a spec that drives an engine
 * in process.
 *
 * The synthetic names come from the process-wide `uniqueId()` maps in `audio_bridge`; this class
 * only owns the per-playback "have I announced it yet" gate. Both sides free it when the playback
 * stops — it mirrors the backend's per-`PlaybackEngine` registry forks.
 */
internal class InlineDslRegistrar(
    val ignitors: AnnounceOnceRegistry<IgnitorDsl>,
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
         * This is what the class KDoc means by "swap the sinks": a spec that drives a single engine
         * (`KlangAudioRenderer`) writes to its registries directly, with the same announce-once
         * bookkeeping and the same [announceAll] sweep. The offline renderer used it until
         * KLANG-00399 step 4; it now goes over the wire like a live playback.
         *
         * An ignitor name that is already taken is left alone: the built-in sounds are seeded in
         * the same registry, and a synthetic `ignitor-N` must never shadow one.
         */
        fun intoRegistries(
            ignitors: IgnitorRegistry,
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
     * nothing outside this file. The offline renderer reaches it over the wire like a live playback
     * (KLANG-00399 step 4); before 2026-09-17 it still had its own copy of the sweep.
     */
    fun announceAll(events: List<KlangPatternEvent>) {
        events.asSequence()
            .map { it.sound }
            .filterIsInstance<SoundValue.Dsl>()
            .forEach { ignitors.registerOrLookup(it.ignitor) }

        // Both positions carry the same chain type into the ONE registry: an output chain
        // (`master(...)`) and an orbit chain (`katalyst(...)`) are announced alike, and a chain
        // written at both positions is announced once.
        events.asSequence()
            .map { it.master }
            .filterIsInstance<KatalystValue.Dsl>()
            .forEach { katalysts.registerOrLookup(it) }

        events.asSequence()
            .map { it.katalyst }
            .filterIsInstance<KatalystValue.Dsl>()
            .forEach { katalysts.registerOrLookup(it) }
    }
}
