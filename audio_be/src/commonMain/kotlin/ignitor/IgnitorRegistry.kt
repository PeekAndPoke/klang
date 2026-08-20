/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
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

    fun register(name: String, dsl: IgnitorDsl) {
        defs[name.lowercase()] = dsl
    }

    fun get(name: String): IgnitorDsl? = defs[name.lowercase()] ?: parent?.get(name)

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

        val dsl = get(key) ?: return null

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
