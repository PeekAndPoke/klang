/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.pregain
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

        /**
         * THE built-in voice shape (phase 3 steps 6 and 7), the one place it is written: [source] becomes the
         * subtractive synth voice `sound("saw")` has always been, as one Ignitor tree,
         *
         * ```
         * source.pregain().classic()
         * ```
         *
         * The `pregain` slot sits on the source, where the player's touch enters, in front of every
         * nonlinearity (`docs/plans/signal-flow-redesign.md` sections 5 and 6: "Osc -> pregain -> classic").
         * Unwritten it is 1.0, and the gate folds a unity `mul` over a signal away at build, so it costs
         * nothing until a pattern writes `pregain(x)`. Then `classic()`: the pattern's `onepole`, then crush
         * ... adsr, the retired voice strip's order. Every built-in sound ([registerDefaults]) and the sample
         * instrument ([SAMPLE_INSTRUMENT]) are this shape.
         */
        internal fun builtInVoice(source: IgnitorDsl): IgnitorDsl = source.pregain().classic()

        /**
         * The SAMPLE INSTRUMENT (phase 3 step 7): the tree every sample voice (`sound("bd")`, any name that is
         * not a registered instrument) runs, [builtInVoice] over the voice's sample ([IgnitorDsl.Sample]). So a
         * sample takes the pattern's doors exactly as a built-in synth does.
         *
         * One generic instrument, keyed by nothing: the voice's sample is resolved by the engine and handed to
         * the build (`VoiceFactory`). Deliberately NOT registered under a name: a name would enter the sound
         * namespace (`sound("sample")` would pick an instrument with no sample) and invite a fork to shadow it.
         * Optimized once, here, like every registered tree; like [register] it never throws, falling back to
         * the authored tree ([optimizerFailures] cannot count it: no registry owns it).
         */
        internal val SAMPLE_INSTRUMENT: IgnitorDsl = builtInVoice(IgnitorDsl.Sample).let { tree ->
            try {
                tree.optimize()
            } catch (_: Throwable) {
                tree
            }
        }
    }

    private val defs = mutableMapOf<String, IgnitorDsl>()

    /**
     * Optimized twin of [defs] — what voices actually render. Kept separate so [get] can keep
     * returning the AUTHORED tree, which live coding re-registers under new names and which keeps
     * the two trees comparable for debugging. (Until 2026-08-27 `VoiceFactory` also read [get] for
     * `maxReleaseSec`, so authored-vs-optimized could diverge on voice lifetime; the tail now comes
     * out of the build of the OPTIMIZED tree, so there is one source and that hazard is gone.) The by-ear A/B does NOT go through here: `.optimizer(0)` travels in the tree
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
     *
     * The built-in sounds come through here too ([registerDefaults], each in [builtInVoice]'s shape).
     */
    fun register(name: String, dsl: IgnitorDsl) {
        store(name.lowercase(), dsl)
    }

    private fun store(key: String, dsl: IgnitorDsl) {
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

    /** The tree AS AUTHORED. See [optimized] for what renders. */
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
        /** The backend's sample rate and block size. Read only by a `humanize` filter's drift
         *  lane, whose time constants follow the rate it is stepped at. */
        sampleRate: Int = DEFAULT_BUILD_SAMPLE_RATE,
        blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
    ): BuiltIgnitor? {
        val key = (name ?: DEFAULT_SOUND).lowercase()

        // No `?: get(key)` fallback on purpose: it could never fire (register writes both maps
        // together), so it would only mask a future regression in optimized()'s delegation by
        // silently rendering unoptimized instead of failing a test.
        val dsl = optimized(key) ?: return null

        // The tree renders as registered: nothing is hung around it (the registry's `onepole` wrap for an
        // instrument that did not end in `classic()` retired with the voice strip, phase 3 step 9; the pattern's
        // `onepole` reaches `classic()`'s first stage, or a tree that places `OscSlot.onepole` itself).
        // The slot bag is the voice's own: a producer writes `classic()`'s slots there (sprudel's `toVoiceData`,
        // phase 3 step 8).
        return dsl.buildExciter(
            data.oscParams,
            soundIndex = data.soundIndex ?: 0,
            phasePools = phasePools,
            orbit = data.cylinder ?: 0,
            random = random,
            freqHz = freqHz,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )
    }

    /** Create a child that delegates to this registry for keys not found locally. */
    fun fork(): IgnitorRegistry = IgnitorRegistry(parent = this)
}
