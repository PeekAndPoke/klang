/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [MasterDsl] chains — the master-side mirror of
 * [PipelineDsl.uniqueId] / [IgnitorDsl.uniqueId].
 *
 * Identity = structural equality on the [MasterDsl] data class. Two structurally-equal masters
 * collapse to one entry and share one synthetic name like `"master-3"`. The counter is monotonic
 * and never resets — names stay stable across the lifetime of the process.
 *
 * This matters more for masters than for pipelines: a top-level `master(...)` re-emits its event
 * every cycle, and structural identity is what keeps that from allocating a new name each time.
 */
private val globalMasterNames = KlangSnapshotMap<MasterDsl, String>()
private var nextGlobalMasterId: Int = 0

/**
 * Return the process-wide unique name for this [MasterDsl] chain.
 *
 * On first sighting, allocates a fresh monotonic name like `"master-N"`. Subsequent calls with
 * structurally-equal chains return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the master to any audio backend. That
 * side of the round-trip is the player's master registry responsibility.
 */
fun MasterDsl.uniqueId(): String = globalMasterNames.getOrPut(this) {
    "master-${nextGlobalMasterId++}"
}
