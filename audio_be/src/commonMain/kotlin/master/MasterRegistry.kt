/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.peekandpoke.klang.audio_bridge.MasterDsl

/**
 * Backend registry: master name → [MasterDsl].
 *
 * Custom masters arrive at runtime via `KlangCommLink.Cmd.RegisterMaster` and land on the
 * **per-playback fork** (so they die with that playback's engine). Mirror of
 * [io.peekandpoke.klang.audio_be.engines.PipelineRegistry], minus the presets — there is no
 * built-in master chain to seed: an unknown or absent name resolves to [MasterDsl.default] (unity),
 * which is what keeps a song without `master(…)` byte-identical to the pre-MasterDsl engine.
 */
class MasterRegistry(
    /** Parent registry — lookups delegate here when not found locally. */
    private val parent: MasterRegistry? = null,
) {
    private val defs = mutableMapOf<String, MasterDsl>()

    fun register(name: String, dsl: MasterDsl) {
        defs[name.lowercase()] = dsl
    }

    /**
     * Resolve a master by name, or **null when it is not registered here or on any parent**.
     *
     * Deliberately not "fall back to unity": the caller ([MasterBus.requestSwap]) must be able to
     * tell "unknown, try again later" from "known and empty". Silently resolving an unknown name to
     * unity would let a dropped/late `RegisterMaster` pin a playback to unity permanently.
     */
    fun find(name: String?): MasterDsl? {
        val key = (name ?: return null).lowercase()
        defs[key]?.let { return it }
        return parent?.find(name)
    }

    /** Create a child that delegates to this registry for masters not found locally. */
    fun fork(): MasterRegistry = MasterRegistry(parent = this)
}
