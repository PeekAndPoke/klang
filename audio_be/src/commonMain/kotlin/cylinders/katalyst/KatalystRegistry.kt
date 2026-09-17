/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystDsl

/**
 * Backend registry: Katalyst name → [KatalystDsl].
 *
 * Custom orbit chains arrive at runtime via `KlangCommLink.Cmd.RegisterKatalyst` and land on the
 * **per-playback fork** (so they die with that playback's engine). Mirror of
 * [io.peekandpoke.klang.audio_be.master.MasterRegistry], minus the presets: there is no built-in
 * chain to seed here: a cylinder that was never handed a name runs its fixed historical chain,
 * which is what keeps a song without `katalyst(…)` byte-identical to the pre-Katalyst-DSL engine.
 *
 * Katalyst step 1 (2026-09-17) registers chains and nothing reads them. Step 2 made the
 * cylinder build its chain from `KatalystDsl.classic` through `KatalystChainBuilder`, still
 * without consulting this registry; step 3 is where a cylinder looks a NAME up here and swaps.
 */
class KatalystRegistry(
    /** Parent registry; lookups delegate here when not found locally. */
    private val parent: KatalystRegistry? = null,
) {
    private val defs = mutableMapOf<String, KatalystDsl>()

    fun register(name: String, dsl: KatalystDsl) {
        defs[name.lowercase()] = dsl
    }

    /**
     * Resolve a chain by name, or **null when it is not registered here or on any parent**.
     *
     * Deliberately not "fall back to the historical chain": the caller must be able to tell
     * "unknown, try again later" from "known". Silently resolving an unknown name would let a
     * dropped or late `RegisterKatalyst` pin an orbit to the wrong chain permanently.
     */
    fun find(name: String?): KatalystDsl? {
        val key = (name ?: return null).lowercase()
        defs[key]?.let { return it }
        return parent?.find(name)
    }

    /** Create a child that delegates to this registry for chains not found locally. */
    fun fork(): KatalystRegistry = KatalystRegistry(parent = this)
}
