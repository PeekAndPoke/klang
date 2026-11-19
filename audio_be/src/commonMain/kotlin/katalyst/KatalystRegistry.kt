/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.optimize

/**
 * Backend registry: Katalyst name → [KatalystDsl].
 *
 * Custom chains arrive at runtime via `KlangCommLink.Cmd.RegisterKatalyst` and land on the
 * **per-playback fork** (so they die with that playback's engine). ONE registry for both positions
 * (phase 3 step 12 C5): the engine's cylinders resolve a `katalyst(…)` name here, its master bus a
 * `master(…)` name, against the same fork. There is no built-in chain to seed: a cylinder that was
 * never handed a name runs its fixed historical chain, which is what keeps a song without
 * `katalyst(…)` byte-identical to the pre-Katalyst-DSL engine, and a bus that was never handed one
 * runs the empty chain.
 *
 * **What is stored is the OPTIMIZED chain** (KLANG-00468, decision D9 of KLANG-00466): [register] runs the chain's
 * pass ([KatalystDsl.optimize], adjacent `eq` stages of numbers merged into one) once, here, so every lookup, and so every
 * cylinder and the output, builds the merged form. The pass renders the same bits as the written chain, so this changes
 * how many stages run and not one sample. The wire and the chain's name stay the written chain's: the name is
 * `KatalystDsl.uniqueId()` of what the pattern wrote, taken in the frontend before any pass, and the pass is
 * deterministic, so equal written chains share one name and find one optimized chain. A chain the pass changes nothing
 * in is stored as the instance registered. A chain built directly with `KatalystChainBuilder` (specs, the effect
 * benchmark) stays as written; the engine allocation cases (`AllocationCases`) register through here, so they run the
 * optimized form.
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

    /**
     * Stores [dsl] under [name], in its optimized form (see the class KDoc). Overwrites: a name re-registered with an
     * edited chain plays the edit.
     *
     * This function MUST stay total, as `IgnitorRegistry.register` must: it runs on the audio thread's command path
     * (`Cmd.RegisterKatalyst`), where an escaping exception would take the whole playback out
     * (`Feedback.PlaybackFailed`) over a pass that only saves stages. A failure of the pass stores the chain as written
     * (it renders the same samples, only with more stages) and is counted in [optimizerFailures]; it is never
     * rethrown.
     */
    fun register(name: String, dsl: KatalystDsl) {
        defs[name.lowercase()] = try {
            dsl.optimize()
        } catch (_: Throwable) {
            // Never rethrown (see the KDoc), never silent either: the count is what tells "the pass failed" from
            // "nothing to merge". A probe for a debugger or a sweep, as the voice registry's: nothing can make the pass
            // throw today, so the increment is untested by construction.
            optimizerFailures++
            dsl
        }
    }

    /**
     * How many registrations stored the chain as written because the pass threw. Stays 0 in a healthy build. Read it
     * from a debugger or a sweep; `KatalystRegistrySpec` asserts it stays 0 for the chains it registers.
     */
    internal var optimizerFailures: Int = 0
        private set

    /**
     * Resolve a chain by name, or **null when it is not registered here or on any parent**.
     *
     * Deliberately not "fall back to the historical chain": the caller must be able to tell
     * "unknown, try again later" from "known". Silently resolving an unknown name would let a
     * dropped or late `RegisterKatalyst` pin a cylinder to the wrong chain permanently.
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
