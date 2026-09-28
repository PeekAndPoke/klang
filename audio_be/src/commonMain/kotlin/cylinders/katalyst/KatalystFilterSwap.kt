/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.constants.BANK_CROSSFADE_SECONDS

/**
 * Click-free switching for a stereo [AudioFilter] pair: on, off, and every change in between.
 *
 * A resonant bank (body / vowel / eq) carries state: its SVF integrators are mid-ring. Replacing
 * the instance outright, or dropping it to dry, makes the output jump in one sample (measured -12
 * to -30 dB above 8 kHz against the signal, a click; `docs/tasks/katalyst-dsl.md`, step 5c). So
 * every edge is a linear crossfade over [BANK_CROSSFADE_SECONDS] (decided with the maintainer,
 * 2026-09-19, Katalyst step 5c-6; the fade became its own 20 ms constant in 5c-11), **from what
 * sounds now**:
 *
 * - **ON** fades the new pair in from DRY. **OFF** ([clear]) fades the pair in service out to dry,
 *   and only once its weight is exactly 0, so the output IS the dry input, does the swap enter
 *   [Off] and let go of it. The dry signal is simply the fade partner: an entry with no filter.
 * - **A change** ([set]) makes the pair in service THE outgoing entry, frozen at the weight it has
 *   at that sample, ramping linearly to 0 over one fade time. The new pair (or dry) takes the
 *   complement of that weight, so the two weights always sum to 1 and the edge is continuous.
 * - **A return** ([resume]): an owner that comes back to the pair that is fading out takes it back
 *   where it stands: the fade turns around, no new bank, no step.
 * - **The first initialisation is instant.** Until this swap has processed a block since it was
 *   built or [reset], [set] and [clear] act at once: nothing has sounded yet, so there is nothing
 *   to be continuous with (the `KnobGlide` snap rule). Without it every orbit would open with a
 *   swell from dry.
 * - **[reset] is a HARD cut**, to Off at once, for the cylinder's deactivation and retire: the
 *   orbit is silent by then, and a fade that survived would resume in the orbit's next life on
 *   new material.
 *
 * **TWO BANKS, never three** (the maintainer's model, 2026-09-20, Katalyst step 5c-11). There is
 * one outgoing entry and one target, and that is the whole capacity: the pool of up to ten
 * outgoing banks it replaces smeared up to four materials at once on a 64th-note run, and the
 * maintainer heard the smear rather than the switch. A change that arrives while a fade runs is
 * therefore **REFUSED here and PARKED by the host**, as the CONFIG it came as, never as a built
 * bank: [set], [clear] and [resume] have nothing to hold it in, and a host that built a bank for a
 * change that a later one overtakes would allocate for a bank nobody hears. The hosts ask
 * [settled] first and offer the parked config again from their own `process` once a fade lands
 * (see [KatalystBodyEffect.configure]). That is what keeps this class at two banks and the orbit
 * EQ at two pre-built ones.
 *
 * Used by [KatalystBodyEffect], [KatalystFormantEffect] and [KatalystEqEffect], which share this
 * one path. The hosts own the pairs (the swap only references them) and keep the INTENT apart from
 * the sound: [active] is what the owner asked for as far as THIS class knows, [sounding] is whether
 * any pair is still heard. A host with a parked config knows one more thing than this class does,
 * so the intent a test seam reports is the host's, not [active] alone.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystDelayEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan, answered for this swap:**
 * 1. *What outlives its states:* the pair in service ([curL]/[curR], it survives the fade's end into
 *    [Engaged]), the grow-once scratch buffers, and the snap flag [fresh]. No `enter` touches them.
 * 2. *The record of a finished life:* the outgoing entry, [Crossfading]'s own, initialised only by
 *    its `enter`; and the HOSTS' config caches and parking slots, which live outside the swap.
 *    A host cache that outlived a fade-out to Off would make the identical material never
 *    re-install; the hosts therefore ask [resume] instead of trusting the cache, and [resume]
 *    answers false once the pair has faded out.
 * 3. *The Off precondition:* the output is identical to dry. [Off] is entered only from a fade whose
 *    outgoing weight has landed on exactly 0 with dry as the target, or by [reset] (the host
 *    guarantees silence), or while [fresh] (nothing has sounded).
 * 4. *References and who drops them:* the outgoing entry is dropped by its own fade's end and by
 *    [reset]; the pair in service by [Off.enter]. Every event dispatches, because the reference a
 *    Crossfading holds can only be dropped by Crossfading.
 */
class KatalystFilterSwap(
    sampleRate: Double,
    /**
     * The frames of one render block, pinned to [AudioBackendContext.RENDER_QUANTUM_FRAMES] in the
     * engine; `KatalystChainBuilder` passes its own. It sizes the scratch buffers at construction,
     * so the first fade allocates nothing on the audio thread.
     */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) {

    private val fadeLen: Int = (sampleRate * BANK_CROSSFADE_SECONDS).toInt().coerceAtLeast(1)

    private val invFadeLen: Double = 1.0 / fadeLen

    /**
     * The TARGET pair, the one the weights converge on: null means the target is dry. It outlives
     * [Crossfading] (the fade's end keeps it as the pair in service), so it lives here and not on a
     * state. [Off.enter] is where it is dropped.
     */
    private var curL: AudioFilter? = null
    private var curR: AudioFilter? = null

    /**
     * True from construction and from [reset] until a block has been processed: while it holds,
     * [set] and [clear] act at once (the first initialisation is instant). It outlives every state.
     */
    private var fresh: Boolean = true

    /**
     * Sized for one block at construction; they outlive every state, [Off] included. A block
     * longer than `blockFrames` grows them once (a direct caller only: the engine's blocks are
     * all `blockFrames` long, so production never reaches that allocation).
     */
    private var dryL: AudioBuffer = AudioBuffer(blockFrames)
    private var dryR: AudioBuffer = AudioBuffer(blockFrames)
    private var wetL: AudioBuffer = AudioBuffer(blockFrames)
    private var wetR: AudioBuffer = AudioBuffer(blockFrames)

    /**
     * The lifecycle, one class per state. One instance of each is created with the swap and [state]
     * points at the current one, so a transition is a pointer swap and nothing allocates on the audio
     * thread. `enter` is the only way into a state, and it is what initialises that state's own data.
     *
     * "Fresh" is [fresh]: no block processed since construction or [reset].
     *
     * | state \ event | `set` (a new pair) | `resume` (the pair fading out) | `clear` | `reset` | `process`, the fade ends |
     * |---|---|---|---|---|---|
     * | **Off** | fresh: **Engaged** at once; else **Crossfading** from dry | false, Off | Off | Off | (never) |
     * | **Engaged** | fresh: Engaged, replaced at once; else **Crossfading**, the pair in service outgoing | true if it is the pair in service, else false | fresh: **Off** at once; else **Crossfading** to dry | **Off** | (never) |
     * | **Crossfading** | REFUSED, the fade runs on (the host parks the config) | Crossfading, turned around (true), or false | REFUSED, except that a fade already heading for dry is idempotent | **Off**, the entry dropped | **Engaged** (target a pair) or **Off** (target dry, output exactly dry) |
     */
    private sealed class State {
        /** A NEW pair arrived from the host, one that has never sounded. */
        abstract fun set(left: AudioFilter, right: AudioFilter)

        /** The host wants the pair whose left filter is [left] back. True when it is still sounding. */
        abstract fun resume(left: AudioFilter): Boolean

        /** The host turned the stage off: fade to dry. */
        abstract fun clear()

        /** A hard cut: the orbit is silent or going to the shelf. */
        abstract fun reset()

        /** One block of the orbit mix, in place. */
        abstract fun process(mix: StereoBuffer, n: Int)
    }

    /** Nothing sounds: the mix passes through untouched. */
    private inner class Off : State() {
        /**
         * Drops the pair in service, the one reference this state has to forget.
         *
         * PRECONDITION: the output is already identical to dry (a fade to dry has landed, or the
         * host guarantees silence at [reset], or nothing has sounded yet). Entering Off from a
         * sounding pair is a cut, the click this class exists to prevent.
         */
        fun enter() {
            curL = null
            curR = null
            state = this
        }

        override fun set(left: AudioFilter, right: AudioFilter) {
            if (fresh) {
                curL = left
                curR = right
                engaged.enter()

                return
            }

            // Dry is what sounds now: it becomes the outgoing entry, the new pair fades in from 0.
            crossfading.enter(null, null)
            curL = left
            curR = right
        }

        override fun resume(left: AudioFilter): Boolean = false

        override fun clear() {}

        override fun reset() {
            enter()
        }

        override fun process(mix: StereoBuffer, n: Int) {}
    }

    /** One pair in service at full weight. It owns no data: the pair outlives this state (see [curL]). */
    private inner class Engaged : State() {
        fun enter() {
            state = this
        }

        override fun set(left: AudioFilter, right: AudioFilter) {
            if (fresh) {
                // Nothing has sounded: the pair in service is replaced, not faded.
                curL = left
                curR = right

                return
            }

            crossfading.enter(curL, curR)
            curL = left
            curR = right
        }

        override fun resume(left: AudioFilter): Boolean = left === curL

        override fun clear() {
            if (fresh) {
                off.enter()

                return
            }

            crossfading.enter(curL, curR)
            curL = null
            curR = null
        }

        override fun reset() {
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
     * The target (a pair, or dry) fades in over THE outgoing entry: one pair, or dry, frozen at the
     * weight it had when it left and ramping linearly to 0 over one fade time. The target's weight
     * is the complement, `1 - w`.
     *
     * The entry dies with this state, so it lives here; [enter] is its only initialiser, and every
     * event that drops it drops it here.
     */
    private inner class Crossfading : State() {
        private var outL: AudioFilter? = null
        private var outR: AudioFilter? = null
        private var outFrom: Double = 0.0
        private var outPos: Int = 0

        /**
         * From Off or Engaged: the one thing sounding now ([fromL] a pair, or null for dry) starts
         * its fade out at full weight. The caller then installs the new target.
         */
        fun enter(fromL: AudioFilter?, fromR: AudioFilter?) {
            outL = fromL
            outR = fromR
            outFrom = 1.0
            outPos = 0
            state = this
        }

        /** Whether the outgoing entry references [filter]; read through [holds]. */
        fun references(filter: AudioFilter): Boolean = outL === filter || outR === filter

        /**
         * REFUSED: a second bank is already fading out, and taking this pair would make three
         * sound. The host parks the CONFIG and offers it again once [settled] (the class KDoc).
         */
        override fun set(left: AudioFilter, right: AudioFilter) {}

        override fun resume(left: AudioFilter): Boolean {
            if (left === curL) {
                return true
            }

            if (left === outL) {
                // The entry becomes the target, so its weight becomes the complement: the fade
                // turns around where it stands. The old target (dry, after a clear) goes out at
                // the weight it has now, and falls to 0 over a fade time of its own.
                val tw = targetWeight()
                val l = outL
                val r = outR

                outL = curL
                outR = curR
                outFrom = tw
                outPos = 0
                curL = l
                curR = r

                return true
            }

            return false
        }

        /**
         * A fade already heading for dry is idempotent; a fade heading for a PAIR refuses, exactly
         * as [set] does and for its reason (the host parks the off and offers it again).
         */
        override fun clear() {}

        override fun reset() {
            outL = null
            outR = null
            off.enter()
        }

        override fun process(mix: StereoBuffer, n: Int) {
            // Grow the FIELDS first, then take the locals: a grow that reached only the locals would
            // allocate on every fade block and sound the same.
            if (dryL.size < n) {
                dryL = AudioBuffer(n)
                dryR = AudioBuffer(n)
                wetL = AudioBuffer(n)
                wetR = AudioBuffer(n)
            }

            val dL = dryL
            val dR = dryR
            val sL = wetL
            val sR = wetR
            val mixL = mix.left
            val mixR = mix.right
            val len = fadeLen
            val inv = invFadeLen
            val p0 = outPos
            val w0 = outFrom

            // Keep the dry input: the outgoing pair and the dry partner both read it.
            mixL.copyInto(dL, 0, 0, n)
            mixR.copyInto(dR, 0, 0, n)

            // The target in place: the mix becomes its output (untouched when the target is dry).
            val tL = curL
            val tR = curR

            if (tL != null && tR != null) {
                tL.process(mixL, 0, n)
                tR.process(mixR, 0, n)
            }

            val bL = outL
            val bR = outR
            val srcL: AudioBuffer
            val srcR: AudioBuffer

            if (bL != null && bR != null) {
                // The entry runs the whole block, so its own state stays continuous.
                dL.copyInto(sL, 0, 0, n)
                dR.copyInto(sR, 0, 0, n)
                bL.process(sL, 0, n)
                bR.process(sR, 0, n)
                srcL = sL
                srcR = sR
            } else {
                srcL = dL
                srcR = dR
            }

            // out = target + w * (entry - target), which is (1 - w) * target + w * entry.
            val end = if (n < len - p0) n else len - p0

            // The weight counts DOWN to the landing, so it is exactly 0 there, never a rounding.
            for (k in 0 until end) {
                val w = w0 * ((len - p0 - k) * inv)

                mixL[k] += w * (srcL[k] - mixL[k])
                mixR[k] += w * (srcR[k] - mixR[k])
            }

            outPos = p0 + n

            if (outPos >= len) {
                outL = null
                outR = null

                if (curL != null) {
                    engaged.enter()
                } else {
                    off.enter()
                }
            }
        }

        /** The target's weight at the next sample to be processed: the complement of the entry's. */
        private fun targetWeight(): Double = 1.0 - outFrom * ((fadeLen - outPos) * invFadeLen)
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
     * Whether any field of this swap references [filter]: the target or the outgoing entry.
     * [KatalystEqEffect] asks it to find a bank it may zero and reuse (a bank this answers false
     * for is silent to the listener); `KatalystFilterSwapSpec` asks it to see that a finished fade
     * and a reset leave no dead bank alive, which the output cannot show.
     */
    internal fun holds(filter: AudioFilter): Boolean =
        curL === filter || curR === filter || crossfading.references(filter)

    /**
     * Whether a change can START now: no fade is running, so [set] and [clear] act. The hosts ask
     * this BEFORE they build anything, and park the config when it is false (the class KDoc).
     */
    val settled: Boolean get() = state !== crossfading

    /**
     * INTENT as far as this class knows it: the last word it was GIVEN was a pair, not off. Flips
     * synchronously in [set], [resume], [clear] and [reset]. A host that has a config parked knows
     * a newer word than this; its own test seam answers from the parking slot first.
     */
    val active: Boolean get() = curL != null

    /** SOUND: a pair may still be heard (the swap is not Off). */
    val sounding: Boolean get() = state !== off

    /**
     * Install a NEW pair: it fades in over whatever sounds now (installed at once while fresh).
     *
     * Only while [settled]. A call while a fade runs is REFUSED and changes nothing.
     */
    fun set(left: AudioFilter, right: AudioFilter) {
        state.set(left, right)
    }

    /**
     * The owner wants back the pair whose left filter is [left], the one it installed last: true
     * when that pair is still sounding (the fade turns around where it stands), false when it has
     * faded out, and the host then installs a fresh one. Legal at any time, a fade included: it
     * takes a bank back instead of adding one.
     */
    fun resume(left: AudioFilter): Boolean = state.resume(left)

    /**
     * Turn the stage off: the pair in service fades to dry, then is released (at once while fresh).
     *
     * Only while [settled], like [set]; a fade already heading for dry takes it as idempotent.
     */
    fun clear() {
        state.clear()
    }

    /** A HARD cut to Off, every pair dropped, and the next [set] snaps again. */
    fun reset() {
        state.reset()
        fresh = true
    }

    /** Process the orbit mix in place. */
    fun process(mix: StereoBuffer, n: Int) {
        fresh = false
        state.process(mix, n)
    }
}
