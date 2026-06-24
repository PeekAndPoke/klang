/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [EngineDsl] trees — the engine-side mirror of
 * [IgnitorDsl.uniqueId].
 *
 * Identity = structural equality on the [EngineDsl] data class. Two structurally-equal
 * engines collapse to one entry and share one synthetic name like `"engine-3"`. The
 * counter is monotonic and never resets — names stay stable across the lifetime of the
 * process regardless of which player or playback first encountered the engine.
 *
 * A player-scoped engine registry uses this map to look up the name and keeps its own
 * bookkeeping of which engines it has already announced to its backend.
 */
private val globalEngineNames = KlangSnapshotMap<EngineDsl, String>()
private var nextGlobalEngineId: Int = 0

/**
 * Return the process-wide unique name for this [EngineDsl] tree.
 *
 * On first sighting, allocates a fresh monotonic name like `"engine-N"`. Subsequent calls
 * with structurally-equal engines return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the engine to any audio backend.
 * That side of the round-trip is the player's engine registry responsibility.
 */
fun EngineDsl.uniqueId(): String = globalEngineNames.getOrPut(this) {
    "engine-${nextGlobalEngineId++}"
}
