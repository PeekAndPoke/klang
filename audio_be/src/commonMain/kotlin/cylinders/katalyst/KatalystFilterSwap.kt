/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * Click-free hot-swap for a stereo [AudioFilter] pair.
 *
 * A resonant bank (body / vowel) carries state: its SVF integrators are mid-ring. Replacing the
 * instance outright makes the wet output jump from the old ring to the new bank's zero state in one
 * sample, an audible click on live material/mix/floor changes (e.g. editing `body(...)` with
 * auto-update). This wrapper instead **crossfades**: on [set] it keeps the previous pair alive and,
 * over `fadeSeconds`, runs BOTH pairs and ramps old to new. At the swap sample the blend equals the
 * old output (continuous, no step); by the end it is fully the new bank.
 *
 * Used by [KatalystBodyEffect], [KatalystFormantEffect] and [KatalystEqEffect], which share this one
 * declick path. Scratch buffers are per-instance and grow-once; both banks run only during the short
 * fade, so the doubled cost is a transient.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystDelayEffect]; the table on [State] is authoritative for its edges.
 */
class KatalystFilterSwap(sampleRate: Double, fadeSeconds: Double = 0.012) {

    private val fadeLen: Int = (sampleRate * fadeSeconds).toInt().coerceAtLeast(1)

    /**
     * The pair in service: null exactly while Off. It outlives [Crossfading], because the fade's
     * end keeps it as the pair in service, so it lives here and not on a state. [Off.enter] is the
     * one place it is dropped.
     */
    private var curL: AudioFilter? = null
    private var curR: AudioFilter? = null

    /** Grow-once, never shrunk: they outlive every state, [Off] included. */
    private var scratchL: AudioBuffer = AudioBuffer(0)
    private var scratchR: AudioBuffer = AudioBuffer(0)

    /**
     * The lifecycle, one class per state. One instance of each is created with the swap and [state]
     * points at the current one, so a transition is a pointer swap and nothing allocates on the audio
     * thread. `enter` is the only way into a state, and it is what initialises that state's own data.
     *
     * | state \ event | `set` | `clear` | `process`, the fade ends |
     * |---|---|---|---|
     * | **Off** | **Engaged** (installed at once, no fade) | Off | (never) |
     * | **Engaged** | **Crossfading** from the pair in service | **Off** (a hard cut) | (never) |
     * | **Crossfading** | Crossfading, restarted from the pair in service; the OLDEST pair is dropped | **Off** (a hard cut, both pairs dropped) | **Engaged** (the outgoing pair dropped) |
     *
     * Every event dispatches, because every event that leaves [Crossfading] has a REFERENCE to
     * drop: the outgoing pair, which dies with that state. Entering [Off] without dispatching, the
     * shortcut the delay's `reset` takes, would keep a dead pair alive until a later fade overwrote
     * it, and for ever if none came.
     */
    private sealed class State {
        /** A new pair arrived from the host. */
        abstract fun set(left: AudioFilter, right: AudioFilter)

        /** The host turned the stage off. */
        abstract fun clear()

        /** One block of the orbit mix, in place. */
        abstract fun process(mix: StereoBuffer, n: Int)
    }

    /** No pair: the mix passes through untouched. */
    private inner class Off : State() {
        /**
         * Drops the pair in service, the one reference this state has to forget.
         *
         * PRECONDITION, as today: none. Entering Off from a sounding pair is a hard cut to dry, the
         * click measured in `docs/tasks/katalyst-dsl.md`; making the wet reach zero first is the
         * later sound change, not this class's today.
         */
        fun enter() {
            curL = null
            curR = null
            state = this
        }

        /** The first pair after Off is installed at once: there is nothing sounding to fade from. */
        override fun set(left: AudioFilter, right: AudioFilter) {
            curL = left
            curR = right
            engaged.enter()
        }

        override fun clear() {}

        override fun process(mix: StereoBuffer, n: Int) {}
    }

    /** One pair in service. It owns no data: the pair outlives this state (see [curL]). */
    private inner class Engaged : State() {
        fun enter() {
            state = this
        }

        override fun set(left: AudioFilter, right: AudioFilter) {
            crossfadeTo(left, right)
        }

        override fun clear() {
            off.enter()
        }

        override fun process(mix: StereoBuffer, n: Int) {
            // Never null in this state; the checks only narrow the type (`!!` could throw on the audio thread).
            val l = curL ?: return
            val r = curR ?: return

            l.process(mix.left, 0, n)
            r.process(mix.right, 0, n)
        }
    }

    /**
     * The pair in service fades in over the outgoing one. The outgoing pair and the fade position
     * die with this state, so they live here; [enter] is their only initialiser and every event that
     * leaves the state drops the pair on the way out.
     */
    private inner class Crossfading : State() {
        /** Readable by the swap for [holds]; written only by this state. */
        var outL: AudioFilter? = null
            private set
        var outR: AudioFilter? = null
            private set

        private var pos = 0

        /** A restart mid-fade overwrites the outgoing pair, which is what drops the oldest one. */
        fun enter(outL: AudioFilter, outR: AudioFilter) {
            this.outL = outL
            this.outR = outR
            pos = 0
            state = this
        }

        /**
         * Restart FROM the pair in service. The oldest pair is dropped: a rare double-change may tick
         * faintly, a single change is click-free (measured, `docs/tasks/katalyst-dsl.md`).
         */
        override fun set(left: AudioFilter, right: AudioFilter) {
            crossfadeTo(left, right)
        }

        override fun clear() {
            outL = null
            outR = null
            off.enter()
        }

        override fun process(mix: StereoBuffer, n: Int) {
            // Never null in this state ([enter] takes non-null pairs, and every drop leaves it); the
            // checks only narrow the type (`!!` could throw on the audio thread).
            val l = curL ?: return
            val r = curR ?: return
            val oL = outL ?: return
            val oR = outR ?: return

            // Grow the FIELDS first, then take the locals: a grow that reached only the locals would
            // allocate on every fade block and sound the same.
            if (scratchL.size < n) {
                scratchL = AudioBuffer(n)
                scratchR = AudioBuffer(n)
            }

            val sL = scratchL
            val sR = scratchR

            val mixL = mix.left
            val mixR = mix.right

            // Keep the dry input for the OLD pair before the NEW pair overwrites the mix in place.
            mixL.copyInto(sL, 0, 0, n)
            mixR.copyInto(sR, 0, 0, n)

            l.process(mixL, 0, n)   // new -> mix
            r.process(mixR, 0, n)
            oL.process(sL, 0, n)    // old -> scratch
            oR.process(sR, 0, n)

            val len = fadeLen
            val start = pos

            for (i in 0 until n) {
                val t = ((start + i).toDouble() / len).coerceAtMost(1.0)
                mixL[i] = sL[i] * (1.0 - t) + mixL[i] * t
                mixR[i] = sR[i] * (1.0 - t) + mixR[i] * t
            }

            val next = start + n
            pos = next

            if (next >= len) {
                outL = null
                outR = null
                engaged.enter()
            }
        }
    }

    private val off = Off()
    private val engaged = Engaged()
    private val crossfading = Crossfading()

    /**
     * The current state: one of the three instances above, never a fresh one. This initializer is
     * the one entry into Off that does not run [Off.enter]; there is no pair yet to drop.
     */
    private var state: State = off

    /**
     * Test seam: the current state OBJECT, for `KatalystFilterSwapStateIdentitySpec`, which walks the
     * transition table and asserts that only ever those three instances appear. Production never
     * reads it.
     */
    internal val currentState: Any get() = state

    /**
     * Test seam: whether any field of this swap still references [filter], the pair in service or
     * the outgoing one. It is how `KatalystFilterSwapSpec` sees that a finished fade and a `clear`
     * leave no dead bank alive, which the output cannot show. Production never reads it.
     */
    internal fun holds(filter: AudioFilter): Boolean =
        curL === filter || curR === filter || crossfading.outL === filter || crossfading.outR === filter

    /** True once a pair is installed (mirrors the old `left != null` "active" flag). */
    val active: Boolean get() = state !== off

    /** Install a new stereo pair, crossfading from the current one (if any). */
    fun set(left: AudioFilter, right: AudioFilter) {
        state.set(left, right)
    }

    /** Drop both pairs: the effect is off. */
    fun clear() {
        state.clear()
    }

    /** Process the orbit mix in place through the current pair, crossfading from the old one if fading. */
    fun process(mix: StereoBuffer, n: Int) {
        state.process(mix, n)
    }

    /** Engaged and Crossfading answer [set] alike: the pair in service becomes the outgoing one. */
    private fun crossfadeTo(left: AudioFilter, right: AudioFilter) {
        val oL = curL
        val oR = curR

        curL = left
        curR = right

        // Only Engaged and Crossfading call this, where the pair in service is never null; the
        // fallback is Off's own answer, an install without a fade.
        if (oL != null && oR != null) {
            crossfading.enter(oL, oR)
        } else {
            engaged.enter()
        }
    }
}
