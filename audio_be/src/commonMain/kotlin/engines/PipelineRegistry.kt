/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.engines

import io.peekandpoke.klang.audio_be.engines.PipelineRegistry.Companion.DEFAULT_PIPELINE
import io.peekandpoke.klang.audio_bridge.PipelineDsl

/**
 * Backend registry: engine name → [PipelineDsl].
 *
 * The built-in `modern` / `pedal` engines are seeded from [PipelinePreset] on the **root**; custom
 * engines arrive at runtime via `KlangCommLink.Cmd.RegisterPipeline` and land on the **per-playback
 * fork** (so they die with that playback's engine), with built-ins inherited through [parent]. Mirror
 * of [io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry] (engines need no per-voice instantiation,
 * so this only resolves the immutable [PipelineDsl]).
 */
class PipelineRegistry(
    /** Parent registry — lookups delegate here when not found locally. */
    private val parent: PipelineRegistry? = null,
) {
    companion object {
        /** Engine used when a voice names none or names an unknown one. */
        const val DEFAULT_PIPELINE = "modern"
    }

    private val defs = mutableMapOf<String, PipelineDsl>()

    init {
        // Only the root seeds the built-ins; forks inherit them via [parent].
        if (parent == null) {
            PipelinePreset.entries.forEach { register(it.pipelineName, it.dsl) }
        }
    }

    fun register(name: String, dsl: PipelineDsl) {
        defs[name.lowercase()] = dsl
    }

    /** Resolve an engine by name. Unknown / null → [DEFAULT_PIPELINE] (always present on the root). Never null. */
    fun get(name: String?): PipelineDsl {
        val key = (name ?: DEFAULT_PIPELINE).lowercase()
        defs[key]?.let { return it }
        parent?.let { return it.get(name) }
        return defs.getValue(DEFAULT_PIPELINE)
    }

    /** Create a child that delegates to this registry for engines not found locally. */
    fun fork(): PipelineRegistry = PipelineRegistry(parent = this)
}
