/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock

/**
 * Announces each structurally-unique [T] to a sink **exactly once**, and hands back the synthetic
 * name it is known by.
 *
 * One generic replacement for what used to be three byte-identical classes (`IgnitorRegistry`,
 * `PipelineRegistry`, `MasterRegistry`) — they differed only in the DSL type and which
 * `Cmd.Register*` they built. See [InlineDslRegistrar], which owns one instance per DSL kind.
 *
 * **Why announce-once matters:** a top-level `master(…)` re-emits its control event every single
 * cycle, all structurally equal. Without the gate that would be one `RegisterMaster` per cycle,
 * forever.
 *
 * Dedup is on structural equality of [T] (the DSL data classes), so a re-derived-identical tree
 * is recognised as already-announced. Naming is NOT this class's job: [uniqueId] delegates to the
 * process-wide identity maps in `audio_bridge`.
 *
 * @param uniqueId The process-wide synthetic name for a DSL tree.
 * @param announce Where a first sighting goes. The live path sends a `Cmd.Register*` over the
 *   wire; an in-process path (e.g. the offline renderer) registers straight into a backend
 *   registry. Same contract, different destination.
 */
internal class AnnounceOnceRegistry<T : Any>(
    private val uniqueId: (T) -> String,
    private val announce: (name: String, dsl: T) -> Unit,
) {
    private val lock = KlangLock()
    private val announced = mutableSetOf<T>()

    /** Number of unique DSLs announced so far. */
    val size: Int get() = lock.withLock { announced.size }

    /** The synthetic name for [dsl]; announces it on first sighting. */
    fun registerOrLookup(dsl: T): String {
        val name = uniqueId(dsl)
        val firstSighting = lock.withLock { announced.add(dsl) }

        if (firstSighting) {
            announce(name, dsl)
        }

        return name
    }
}
