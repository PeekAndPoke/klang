/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.infra.KlangSnapshotMap

/**
 * Process-wide identity map for [PipelineDsl] trees — the pipeline-side mirror of
 * [IgnitorDsl.uniqueId].
 *
 * Identity = structural equality on the [PipelineDsl] data class. Two structurally-equal
 * engines collapse to one entry and share one synthetic name like `"pipeline-3"`. The
 * counter is monotonic and never resets — names stay stable across the lifetime of the
 * process regardless of which player or playback first encountered the engine.
 *
 * A player-scoped engine registry uses this map to look up the name and keeps its own
 * bookkeeping of which engines it has already announced to its backend.
 */
private val globalPipelineNames = KlangSnapshotMap<PipelineDsl, String>()
private var nextGlobalPipelineId: Int = 0

/**
 * Return the process-wide unique name for this [PipelineDsl] tree.
 *
 * On first sighting, allocates a fresh monotonic name like `"pipeline-N"`. Subsequent calls
 * with structurally-equal engines return the same name without further allocation.
 *
 * Note: this only allocates a *name*; it does not announce the engine to any audio backend.
 * That side of the round-trip is the player's engine registry responsibility.
 */
fun PipelineDsl.uniqueId(): String = globalPipelineNames.getOrPut(this) {
    "pipeline-${nextGlobalPipelineId++}"
}
