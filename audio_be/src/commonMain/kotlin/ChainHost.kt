/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_bridge.KatalystDsl

/**
 * The plumbing a position that runs a [KatalystChain] needs, shared by both of them: an orbit (`Cylinder`) and the
 * output (`MasterBus`). KLANG-00243 (one chain host), 2026-11-04: until then each host carried its own copy.
 *
 * It holds the chain in service with the name it was requested under, the bounded cache of built chains, the parked
 * request, the lookup in the [registry] and the [swap]. What a position does when a chain LANDS stays the position's
 * ([Position.land]): an orbit installs at once while it is idle and fades with the duck handover while it sounds; the
 * output adopts at once before the engine's first block and fades after it. So do the rules of the position around
 * the chain (an orbit's owner and silence reset, the output's tail keep-alive).
 *
 * **A request** ([request]) takes one of these paths, all cheap enough for the scheduler's promotion path:
 *  - **The name is already in service** (the same raw spelling, or the same key): nothing to do, and a parked request
 *    is dropped, because the last expressed intent is the chain already playing. This is what makes a top-level
 *    `katalyst(…)` or `master(…)`, which re-emits its event every cycle, free after the first application, and the raw
 *    compare is what makes it allocation-free.
 *  - **The name is not registered here** (the `RegisterKatalyst` command has not arrived, or was dropped): parked and
 *    retried every block ([pollPending]). Never a silent fall back to the chain the position was born with: an unknown
 *    name must not pin the position to the wrong chain.
 *  - **The swap is busy** (a fade, a drain, a release or a cut holds its one leaving slot): parked, latest wins, and the
 *    swap hears that a request waits ([ChainSwap.requestWaiting]): a ring-out is cut over [ChainSwap.CUT_SECONDS]
 *    instead of waited for (maintainer, 2026-10-17), and a running fade finishes first, since cutting it short would
 *    drop its leaving chain at full weight, the click the swap exists to prevent. About 0.15 to 0.22 s in all. A request
 *    for the chain leaving service parks like any other. An idle orbit's swap is always settled: an orbit deactivates
 *    only on a settled swap, a fade begins only on a sounding orbit, and a new life cuts the swap.
 *  - **Otherwise** the position lands it ([Position.land]).
 *
 * The name is normalized ONCE, past the raw fast path, and every later hop (the lookup, the cache, the retry) takes
 * that key ([KatalystRegistry.findByKey] allocates nothing on a miss): resolve on request or registration, never per
 * block.
 *
 * **A chain is built once, on the request that first lands it, and cached** by key ([chainFor]), bounded by
 * [MAX_CACHED_CHAINS]: names are content-derived (`KatalystDsl.uniqueId()`), so every edit of a chain while live coding
 * mints a new one. Building is on the audio thread (a swap lands from the scheduler's promotion path), and small by
 * construction: stage shells, the units rented at the first configure. A chain out of service holds no units (the
 * position retires it when it leaves), so a cached chain is its stage shells only.
 *
 * **A chain can be built ahead** ([prepare]): its shells go into the cache from the command path, between render
 * blocks, so the request that lands it later is a cache hit and the render block builds nothing. The output does this
 * when a schedule carrying a `master(…)` event arrives (`MasterBus.prepare`; KLANG-00137 (master dsl followups) §3,
 * the warehouse's second customer). Its units are still rented at its first configure, from the shelf, as every
 * chain's are.
 */
internal class ChainHost(
    sampleRate: Int,
    blockFrames: Int,
    /** Where a request lands, and how a chain is built: the position's own rules. */
    private val position: Position,
    /**
     * The chain the position is born with and falls back to on a new life: an orbit's classic chain, the output's empty
     * one. Never cached by name, so never evicted.
     */
    bornWithDsl: KatalystDsl,
    /**
     * Whether a declaration whose CONTENT equals [bornWithDsl] resolves to the born-with instance (the orbit, step 5b-1,
     * 2026-09-19: a `Katalyst.classic()` declaration is then a no-op instead of a crossfade). The output builds such a
     * declaration as a chain of its own.
     */
    private val bornWithAnswersItsContent: Boolean,
    /** Where a chain NAME is resolved; null resolves no name (a shelved orbit). Re-pointed by [registry]'s setter. */
    registry: KatalystRegistry?,
) {
    companion object {
        /**
         * How many built chains one position keeps. Names are content-derived, so every EDIT mints a new one; without a
         * bound each edit's stage instances would be kept for the life of the position. The chain in service and the one
         * leaving it are never evicted.
         */
        internal const val MAX_CACHED_CHAINS: Int = 8
    }

    /** The position's own rules, handed to the host once. */
    interface Position {
        /** Builds the chain for [dsl]: its stage shells, renting nothing (a stage rents its unit at its first configure). */
        fun build(dsl: KatalystDsl): KatalystChain

        /**
         * Puts the chain for [key] in service, through [ChainSwap] or at once. Called only while the swap is settled. Reads
         * the chain through [chainFor] and puts it in through [enter]. Returns what the position's own caller needs (the
         * orbit: whether the chain changed).
         */
        fun land(key: String, rawName: String, dsl: KatalystDsl): Boolean
    }

    /**
     * The chain swap: the chain LEAVING service, fading out and then draining. Its one leaving slot is why a request
     * arriving while it is taken waits in [pendingKey].
     */
    val swap = ChainSwap(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The born-with chain (see the constructor). */
    private val bornWith: KatalystChain = position.build(bornWithDsl)

    private val bornWithStages = bornWithDsl.stages

    /** Built chains by key (the lowercased name, the registry's own key, so two spellings share one entry). */
    private val chains = mutableMapOf<String, KatalystChain>()

    /** The chain in service. */
    var current: KatalystChain = bornWith
        private set

    /** The key [current] was requested under; null while it is [bornWith] under no name. */
    private var currentKey: String? = null

    /** The RAW spelling of the last request that landed on [current], for [request]'s allocation-free fast path. */
    private var currentRawName: String? = null

    /** A request that could not land yet, as its registry KEY (normalized once, at request time). Latest wins. */
    private var pendingKey: String? = null

    /** Where a chain name is resolved (see the constructor). */
    var registry: KatalystRegistry? = registry

    /** Built chains held, for the specs asserting the cache stays bounded. */
    internal val cachedChainCount: Int get() = chains.size

    /** Requests the chain registered as [name] (the paths: see the class KDoc). */
    fun request(name: String) {
        if (name == currentRawName) {
            pendingKey = null

            return
        }

        // The ONE normalization: the key the registry and the cache are both keyed by.
        val key = name.lowercase()

        if (key == currentKey) {
            // The chain in service, under another spelling: adopt the spelling, so the next re-emission takes the raw
            // path above.
            currentRawName = name
            pendingKey = null

            return
        }

        val dsl = registry?.findByKey(key)

        if (dsl == null) {
            pendingKey = key

            return
        }

        if (!swap.settled) {
            pendingKey = key
            swap.requestWaiting()

            return
        }

        // The last expressed intent wins: an earlier request still parked must not land after this one.
        pendingKey = null

        position.land(key = key, rawName = name, dsl = dsl)
    }

    /**
     * Lands the parked request once it can: its name has been registered and the swap is settled. Polled once per block
     * by the position's owner, so a late registration lands without a further request (a one-shot `.katalyst(…)` emits
     * once), and a request parked behind a swap lands once the swap is done. One field read when nothing is parked; one
     * map probe per registry in the fork chain while a name stays unknown, and no allocation.
     *
     * Returns false when nothing landed, else what [Position.land] returned.
     */
    fun pollPending(): Boolean {
        val key = pendingKey ?: return false

        if (!swap.settled) {
            // A name registered meanwhile could land: the swap hears that a request waits (idempotent).
            if (registry?.findByKey(key) != null) {
                swap.requestWaiting()
            }

            return false
        }

        val dsl = registry?.findByKey(key) ?: return false

        pendingKey = null

        return position.land(key = key, rawName = key, dsl = dsl)
    }

    /**
     * Builds the chain registered as [name] ahead of its request, into the cache, so the request that lands it later
     * finds it built. Called from the command path, between render blocks. Nothing happens when the chain is already
     * built (the one in service is cached too) or not registered yet. Evicts like a build on landing, but unlike that one
     * it can run while a swap fades or drains, so the leaving chain's guard in [evictIfNeeded] holds. The output's door
     * only: an orbit, whose born-with chain may answer a declaration ([chainFor]), builds on landing.
     */
    fun prepare(name: String) {
        val key = name.lowercase()

        if (chains.containsKey(key)) {
            return
        }

        val dsl = registry?.findByKey(key) ?: return

        evictIfNeeded()
        chains[key] = position.build(dsl)
    }

    /**
     * The built chain for [key] and its [dsl]: the born-with instance for a declaration of its content (when
     * [bornWithAnswersItsContent]), a cache hit, or a fresh build. The content compare runs before the cache lookup, so
     * every actual switch pays one list equality of at most a dozen data classes, off the per-block path.
     */
    fun chainFor(key: String, dsl: KatalystDsl): KatalystChain {
        if (bornWithAnswersItsContent && dsl.stages == bornWithStages) {
            return bornWith
        }

        chains[key]?.let { return it }

        evictIfNeeded()

        return position.build(dsl).also { chains[key] = it }
    }

    /** Puts [chain] in service under [key], requested as [rawName]. The position decides what happens to the chain leaving. */
    fun enter(chain: KatalystChain, key: String, rawName: String) {
        current = chain
        currentKey = key
        currentRawName = rawName
    }

    /**
     * The clean slate of a new life (an orbit shelved or re-labelled, an engine disposed): every chain retired, so its
     * rented units go back to their shelves (dirty: the warehouse's housekeeping zeroes them), the swap cut, the cache
     * dropped, the born-with chain in service under no name, nothing parked. Retire is idempotent, so a chain met twice
     * costs nothing.
     */
    fun retireAll() {
        current.retire()
        bornWith.retire()
        // Any fade or drain ends here, at whatever weight it had reached; the cut retires the leaving chain and starts
        // the denied-rents count of the new life over.
        swap.hardCut()

        for (cached in chains.values) {
            cached.retire()
        }

        chains.clear()
        current = bornWith
        currentKey = null
        currentRawName = null
        pendingKey = null
    }

    /**
     * Drops cached chains until there is room for one more. The chain in service is never evicted. Nor is one leaving
     * it: a chain lands only on a settled swap, but [prepare] builds ahead while a swap fades or drains, and retiring a
     * leaving chain that is still audible would hand its units to another position mid-tail. An evicted chain is retired, a no-op for its
     * rents (it had none) and the clean slate for its shells.
     */
    private fun evictIfNeeded() {
        while (chains.size >= MAX_CACHED_CHAINS) {
            // Insertion order (the map's own): the oldest declaration a live-coding session has moved past.
            val victim = chains.entries.firstOrNull { (_, cached) ->
                cached !== current && cached !== swap.leaving
            } ?: return

            // Copy the entry out BEFORE the removal: Kotlin/JS refuses to read a map entry once the backing map has
            // changed (ConcurrentModificationException, found by the JS suite).
            val victimName = victim.key
            val victimChain = victim.value

            chains.remove(victimName)
            victimChain.retire()
        }
    }
}
