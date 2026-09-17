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

    /**
     * Lookups that had to normalize a raw name, which is the one place this registry allocates a
     * string. A test seam: the render path must use [findByKey] and leave this number alone, even
     * while it retries a name that never resolves (`Cylinder.pollPendingChain`).
     */
    internal var namesNormalized: Int = 0
        private set

    fun register(name: String, dsl: KatalystDsl) {
        defs[name.lowercase()] = dsl
    }

    /**
     * Resolve a chain by name, or **null when it is not registered here or on any parent**.
     *
     * Deliberately not "fall back to the historical chain": the caller must be able to tell
     * "unknown, try again later" from "known". Silently resolving an unknown name would let a
     * dropped or late `RegisterKatalyst` pin an orbit to the wrong chain permanently.
     *
     * **Normalizes, so it allocates**: for a caller that already holds the key (every caller on a
     * render path should, see `Cylinder.requestChain`) the door is [findByKey].
     */
    fun find(name: String?): KatalystDsl? {
        val key = (name ?: return null).lowercase()

        namesNormalized++

        return findByKey(key)
    }

    /**
     * Resolve a chain from an ALREADY LOWERCASED key: the allocation-free door, and the only one a
     * render path may use.
     *
     * The parent hop passes the same key through untouched, so a MISS costs one map probe per
     * registry in the chain and not a string. A caller that hands in a key with upper case gets a
     * miss, which is why normalizing is [find]'s job and happens once, at request time (the §7
     * rule: resolve on request or registration, never per block).
     */
    fun findByKey(key: String): KatalystDsl? {
        defs[key]?.let { return it }

        return parent?.findByKey(key)
    }

    /** Create a child that delegates to this registry for chains not found locally. */
    fun fork(): KatalystRegistry = KatalystRegistry(parent = this)
}
