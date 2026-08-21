/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Single source of truth for all oscillator lookups.
 *
 * Each [createExciter] call produces a fresh [Ignitor] with independent mutable state
 * (phase accumulators, filter memory, etc.) — two voices never share a Ignitor instance.
 */
class IgnitorRegistry(
    /** Parent registry — lookups delegate here when not found locally. */
    private val parent: IgnitorRegistry? = null,
) {
    companion object {
        /** Default sound when none is specified */
        const val DEFAULT_SOUND = "triangle"
    }

    private val defs = mutableMapOf<String, IgnitorDsl>()

    /**
     * Optimized twin of [defs] — what voices actually render. Kept separate so [get] can keep
     * returning the AUTHORED tree, which `VoiceFactory` reads for `maxReleaseSec` (voice
     * lifetime must follow what the user wrote) and which keeps the two trees comparable for
     * debugging. The by-ear A/B does NOT go through here: `.optimizer(0)` travels in the tree
     * itself, so `optimized()` simply returns it untouched.
     */
    private val optimizedDefs = mutableMapOf<String, IgnitorDsl>()

    /**
     * Registers [dsl] under [name] and runs the graph optimizer ONCE, here, rather than per
     * voice: `createExciter` runs per note-on, so optimizing there would pay an O(tree) cost on
     * the render path for every note.
     *
     * Overwrite, never getOrPut: names are re-registered with edited trees during live coding,
     * so caching by key alone would keep playing the previous sound forever.
     *
     * This function MUST stay total. The worklet's onmessage dispatch has no try/catch, so an
     * escaping exception here would unwind after [defs] was written but before [optimizedDefs],
     * silently dropping every voice of that instrument on JS while the JVM threw loudly. On
     * failure the authored tree is stored instead, so a broken optimizer degrades to
     * unoptimized audio rather than silence, and never serves a tree other than the last one
     * registered.
     */
    fun register(name: String, dsl: IgnitorDsl) {
        val key = name.lowercase()
        defs[key] = dsl
        optimizedDefs[key] = try {
            dsl.optimize()
        } catch (_: Throwable) {
            // Never rethrow (see the KDoc), but never silent either: a brand-new rewrite pass
            // failing must be visible, or "the optimizer crashed" is indistinguishable from
            // "nothing to fuse" on both platforms. This is a PROBE for a debugger or a
            // regression sweep, not a mutation-checked guard: nothing in the suite can make
            // optimize() throw today, so the increment itself is untested by construction.
            optimizerFailures++
            dsl
        }
    }

    /**
     * How many registrations fell back to the authored tree because [optimize] threw. Stays 0 in
     * a healthy build. Read it from a debugger or a sweep; the shipped spec only asserts that
     * the built-in defaults register without a single failure.
     */
    internal var optimizerFailures: Int = 0
        private set

    /** The tree AS AUTHORED (VoiceFactory's `maxReleaseSec` source). See [optimized] for what renders. */
    fun get(name: String): IgnitorDsl? = defs[name.lowercase()] ?: parent?.get(name)

    /**
     * The tree that renders. Mirrors [get]'s parent delegation: built-ins register on the ROOT
     * registry while voices are created on a per-playback fork, so a local-only lookup would
     * return null for every built-in and silently drop the whole song.
     */
    fun optimized(name: String): IgnitorDsl? =
        optimizedDefs[name.lowercase()] ?: parent?.optimized(name)

    fun contains(name: String?): Boolean {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        return defs.containsKey(key) || (parent?.contains(key) == true)
    }

    fun names(): Set<String> = (parent?.names() ?: emptySet()) + defs.keys

    /**
     * Creates a fresh [Ignitor] for the given oscillator name.
     *
     * [phasePools] is the playback's unison start-phase pool registry (null → the phase-pool
     * feature falls back to stateless banded selection); the orbit key comes from
     * [VoiceData.cylinder].
     *
     * Returns null if the name is unknown.
     */
    fun createExciter(
        name: String?,
        data: VoiceData,
        freqHz: Double,
        phasePools: PhasePools? = null,
        /** The voice's random stream (seeded-voice-rng; see IgniteContext.random). */
        random: Random = Random,
    ): Ignitor? {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        val oscParams = data.oscParams

        // No `?: get(key)` fallback on purpose: it could never fire (register writes both maps
        // together), so it would only mask a future regression in optimized()'s delegation by
        // silently rendering unoptimized instead of failing a test.
        val dsl = optimized(key) ?: return null

        val raw = dsl.toExciter(
            oscParams,
            soundIndex = data.soundIndex ?: 0,
            phasePools = phasePools,
            orbit = data.cylinder ?: 0,
            random = random,
        )
        val warmth = oscParams?.get("warmth") ?: 0.0
        return if (warmth > 0.0) raw.withWarmth(warmth) else raw
    }

    /** Create a child that delegates to this registry for keys not found locally. */
    fun fork(): IgnitorRegistry = IgnitorRegistry(parent = this)
}
