/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [KatalystDsl] chains, the orbit-side mirror of
 * [MasterDsl.uniqueId] / [PipelineDsl.uniqueId] / [IgnitorDsl.uniqueId].
 *
 * Identity = structural equality on the [KatalystDsl] data class. Two structurally-equal chains
 * collapse to one entry and share one synthetic name like `"katalyst-3"`. The counter is monotonic
 * and never resets, so names stay stable across the lifetime of the process.
 *
 * This matters as much as it does for masters: a top-level `katalyst(...)` re-emits its event every
 * cycle, and structural identity is what keeps that from allocating a new name each time.
 */
private val globalKatalystNames = KlangSnapshotMap<KatalystDsl, String>()
private var nextGlobalKatalystId: Int = 0

/**
 * Return the process-wide unique name for this [KatalystDsl] chain.
 *
 * On first sighting, allocates a fresh monotonic name like `"katalyst-N"`. Subsequent calls with
 * structurally-equal chains return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the chain to any audio backend. That
 * side of the round-trip is the playback's Katalyst registry responsibility.
 */
fun KatalystDsl.uniqueId(): String = globalKatalystNames.getOrPut(this) {
    "katalyst-${nextGlobalKatalystId++}"
}
