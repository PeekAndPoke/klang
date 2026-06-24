/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [IgnitorDsl] trees.
 *
 * Identity = structural equality on the [IgnitorDsl] data classes. Two structurally-equal
 * trees collapse to one entry and share one synthetic name like `"ignitor-7"`. The counter
 * is monotonic and never resets — names stay stable across the lifetime of the process
 * regardless of which player or playback first encountered the tree.
 *
 * A player-scoped IgnitorRegistry uses this map to look up the name and keeps its own
 * bookkeeping of which DSLs it has already announced to its backend.
 *
 * Reads (the common case during voice scheduling) are lock-free via [KlangSnapshotMap].
 * The counter mutation rides inside the map's lock through the `getOrPut` callback.
 */
private val globalIgnitorNames = KlangSnapshotMap<IgnitorDsl, String>()
private var nextGlobalIgnitorId: Int = 0

/**
 * Return the process-wide unique name for this [IgnitorDsl] tree.
 *
 * On first sighting, allocates a fresh monotonic name like `"ignitor-N"`. Subsequent calls
 * with structurally-equal trees return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the DSL to any audio backend.
 * That side of the round-trip is the player's IgnitorRegistry responsibility.
 */
fun IgnitorDsl.uniqueId(): String = globalIgnitorNames.getOrPut(this) {
    "ignitor-${nextGlobalIgnitorId++}"
}
