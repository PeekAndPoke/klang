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
 * The built-in `modern` / `pedal` engines are seeded from [AudioEngine]; custom engines
 * arrive at runtime via `KlangCommLink.Cmd.RegisterEngine`. Mirror of
 * [io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry] (engines need no per-voice
 * instantiation, so this only resolves the immutable [EngineDsl]).
 */
class EngineRegistry {
    companion object {
        /** Engine used when a voice names none or names an unknown one. */
        const val DEFAULT_ENGINE = "modern"
    }

    private val defs = mutableMapOf<String, EngineDsl>()

    init {
        AudioEngine.entries.forEach { register(it.engineName, it.dsl) }
    }

    fun register(name: String, dsl: EngineDsl) {
        defs[name.lowercase()] = dsl
    }

    /** Resolve an engine by name. Unknown / null → [DEFAULT_ENGINE]. Never null. */
    fun get(name: String?): EngineDsl =
        defs[(name ?: DEFAULT_ENGINE).lowercase()] ?: defs.getValue(DEFAULT_ENGINE)
}
