/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.engines

import io.peekandpoke.klang.audio_be.engines.EngineRegistry.Companion.DEFAULT_ENGINE
import io.peekandpoke.klang.audio_bridge.EngineDsl

/**
 * Backend registry: engine name → [EngineDsl].
 *
 * The built-in `modern` / `pedal` engines are seeded from [AudioEngine] on the **root**; custom
 * engines arrive at runtime via `KlangCommLink.Cmd.RegisterEngine` and land on the **per-playback
 * fork** (so they die with that playback's engine), with built-ins inherited through [parent]. Mirror
 * of [io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry] (engines need no per-voice instantiation,
 * so this only resolves the immutable [EngineDsl]).
 */
class EngineRegistry(
    /** Parent registry — lookups delegate here when not found locally. */
    private val parent: EngineRegistry? = null,
) {
    companion object {
        /** Engine used when a voice names none or names an unknown one. */
        const val DEFAULT_ENGINE = "modern"
    }

    private val defs = mutableMapOf<String, EngineDsl>()

    init {
        // Only the root seeds the built-ins; forks inherit them via [parent].
        if (parent == null) {
            AudioEngine.entries.forEach { register(it.engineName, it.dsl) }
        }
    }

    fun register(name: String, dsl: EngineDsl) {
        defs[name.lowercase()] = dsl
    }

    /** Resolve an engine by name. Unknown / null → [DEFAULT_ENGINE] (always present on the root). Never null. */
    fun get(name: String?): EngineDsl {
        val key = (name ?: DEFAULT_ENGINE).lowercase()
        defs[key]?.let { return it }
        parent?.let { return it.get(name) }
        return defs.getValue(DEFAULT_ENGINE)
    }

    /** Create a child that delegates to this registry for engines not found locally. */
    fun fork(): EngineRegistry = EngineRegistry(parent = this)
}
