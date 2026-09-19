/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS

/**
 * Click-free switching for a stereo [AudioFilter] pair: on, off, and every change in between.
 *
 * A resonant bank (body / vowel / eq) carries state: its SVF integrators are mid-ring. Replacing
 * the instance outright, or dropping it to dry, makes the output jump in one sample (measured -12
 * to -30 dB above 8 kHz against the signal, a click; `docs/tasks/katalyst-dsl.md`, step 5c). So
 * every edge is a linear crossfade over [KNOB_GLIDE_SECONDS] (decided with the maintainer,
 * 2026-09-19, Katalyst step 5c-6), **from what sounds now**:
 *
 * - **ON** fades the new pair in from DRY. **OFF** ([clear]) fades the pair in service out to dry,
 *   and only once its weight is exactly 0, so the output IS the dry input, does the swap enter
 *   [Off] and let go of it. The dry signal is simply the fade partner: an entry with no filter.
 * - **A change** ([set]) makes the pair in service an OUTGOING entry, frozen at the weight it has
 *   at that sample, ramping linearly to 0 over one fade time of its own. The new pair (or dry)
 *   takes the COMPLEMENT of every outgoing weight, so the weights always sum to 1 and every edge is
 *   continuous whatever is still in flight. A change mid-fade never drops a sounding bank (the old
 *   "drop the oldest" measured -11 to -34 dB), it just adds one more outgoing entry.
 * - **A return** ([resume]): an owner that comes back to the pair that is fading out takes it back
 *   where it stands: the fade turns around, no new bank, no step.
 * - **The first initialisation is instant.** Until this swap has processed a block since it was
 *   built or [reset], [set] and [clear] act at once: nothing has sounded yet, so there is nothing
 *   to be continuous with (the `KnobGlide` snap rule). Without it every orbit would open with a
 *   50 ms swell from dry.
 * - **[reset] is a HARD cut**, to Off at once, for the cylinder's deactivation and retire: the
 *   orbit is silent by then, and a fade that survived would resume in the orbit's next life on
 *   new material.
 * - **At most [MAX_BANKS] banks sound at once** (the maintainer's cap, 2026-09-19), preallocated
 *   with the swap. A fade is 17 blocks at 44.1 kHz and 18.75 at 48 kHz, so the cap engages when
 *   changes arrive on every block, and at 48 kHz already on every second block (ten banks
 *   started 256 frames apart still overlap a 2400-frame fade). A change that finds the pool full is PARKED until an outgoing bank has
 *   finished, the latest one wins (the master's "one queued swap" rule as the overflow rule);
 *   dropping the quietest bank instead measured in the click class, see `audio/MEMORY.md`.
 *
 * Used by [KatalystBodyEffect], [KatalystFormantEffect] and [KatalystEqEffect], which share this
 * one path. The hosts own the pairs (the swap only references them) and keep the INTENT apart from
 * the sound: [active] is what the owner asked for, [sounding] is whether any pair is still heard.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystDelayEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan, answered for this swap:**
 * 1. *What outlives its states:* the pair in service ([curL]/[curR], it survives the fade's end into
 *    [Engaged]), the grow-once scratch buffers, and the snap flag [fresh]. No `enter` touches them.
 * 2. *The record of a finished life:* the outgoing entries and the parked pair, both [Crossfading]'s
 *    own, initialised only by its `enter`; and the HOSTS' config caches, which live outside the swap.
 *    A host cache that outlived a fade-out to Off would make the identical material never re-install;
 *    the hosts therefore ask [resume] instead of trusting the cache, and [resume] answers false once
 *    the pair has faded out.
 * 3. *The Off precondition:* the output is identical to dry. [Off] is entered only from a fade whose
 *    last outgoing weight has landed on exactly 0 with dry as the target, or by [reset] (the host
 *    guarantees silence), or while [fresh] (nothing has sounded).
 * 4. *References and who drops them:* the outgoing entries are dropped by their own fade's end and
 *    by [reset]; the parked pair by its install, by a later change (latest wins), by [clear],
 *    [resume] and [reset]; the pair in service by [Off.enter]. Every event dispatches, because the
 *    references a Crossfading holds can only be dropped by Crossfading.
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

    companion object {
        /**
         * How many banks may sound at once in one swap, the pair in service included; the dry
         * partner is not a bank. Decided with the maintainer 2026-09-19 ("for now"): a change on
         * every block would otherwise keep about 19 banks alive.
         */
        const val MAX_BANKS = 10
    }

    private val fadeLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

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
    private var accL: AudioBuffer = AudioBuffer(blockFrames)
    private var accR: AudioBuffer = AudioBuffer(blockFrames)

    /**
     * The lifecycle, one class per state. One instance of each is created with the swap and [state]
     * points at the current one, so a transition is a pointer swap and nothing allocates on the audio
     * thread. `enter` is the only way into a state, and it is what initialises that state's own data.
     *
     * "Fresh" is [fresh]: no block processed since construction or [reset].
     *
     * | state \ event | `set` (a new pair) | `resume` (the pair fading out) | `clear` | `reset` | `process`, the last fade ends |
     * |---|---|---|---|---|---|
     * | **Off** | fresh: **Engaged** at once; else **Crossfading** from dry | false, Off | Off | Off | (never) |
     * | **Engaged** | fresh: Engaged, replaced at once; else **Crossfading**, the pair in service outgoing | true if it is the pair in service, else false | fresh: **Off** at once; else **Crossfading** to dry | **Off** | (never) |
     * | **Crossfading** | Crossfading, the target outgoing at its current weight; at [MAX_BANKS] banks PARKED (latest wins) | Crossfading, turned around (true), or false | Crossfading to dry (a dry entry turns around); idempotent | **Off**, every entry dropped | **Engaged** (target a pair) or **Off** (target dry, output exactly dry); a parked pair is installed first once a bank has freed |
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
     * The target (a pair, or dry) fades in over one or more OUTGOING entries, each a pair (or dry,
     * at most one entry) frozen at the weight it had when it left and ramping linearly to 0 over one
     * fade time of its own. The target's weight is the complement, `1 - sum(outgoing)`.
     *
     * The entries and the parked pair die with this state, so they live here; [enter] is their
     * only initialiser, and every event that drops one drops it here.
     */
    private inner class Crossfading : State() {
        // Capacity: at most MAX_BANKS banks sound, the target included, plus at most one dry entry
        // while the target is a pair; while the target is dry there is no dry entry. So the
        // entries never exceed MAX_BANKS (checked per event in the KDoc of [push]).
        private val outL = arrayOfNulls<AudioFilter>(MAX_BANKS)
        private val outR = arrayOfNulls<AudioFilter>(MAX_BANKS)
        private val outFrom = DoubleArray(MAX_BANKS)
        private val outPos = IntArray(MAX_BANKS)
        private var count = 0

        /** A change that found the pool full; installed once a bank has freed. Latest wins. */
        var parkedL: AudioFilter? = null
            private set
        var parkedR: AudioFilter? = null
            private set

        /**
         * From Off or Engaged: the one thing sounding now ([fromL] a pair, or null for dry) starts
         * its fade out at full weight. The caller then installs the new target.
         */
        fun enter(fromL: AudioFilter?, fromR: AudioFilter?) {
            count = 0
            parkedL = null
            parkedR = null
            push(fromL, fromR, 1.0)
            state = this
        }

        /** Whether an outgoing entry or the parked pair references [filter]; read through [holds]. */
        fun references(filter: AudioFilter): Boolean {
            if (parkedL === filter || parkedR === filter) {
                return true
            }

            for (e in 0 until count) {
                if (outL[e] === filter || outR[e] === filter) {
                    return true
                }
            }

            return false
        }

        override fun set(left: AudioFilter, right: AudioFilter) {
            if (banks() >= MAX_BANKS) {
                // The pool is full: park, the latest change wins. Nothing sounding is cut; the
                // change goes in at the first block boundary after an outgoing bank has finished.
                parkedL = left
                parkedR = right

                return
            }

            parkedL = null
            parkedR = null
            push(curL, curR, targetWeight())
            curL = left
            curR = right
        }

        override fun resume(left: AudioFilter): Boolean {
            parkedL = null
            parkedR = null

            if (left === curL) {
                return true
            }

            for (e in 0 until count) {
                if (outL[e] === left) {
                    // The entry leaves the outgoing list, so its weight becomes the complement: the
                    // fade turns around where it stands. The old target (dry, after a clear) goes
                    // out at the weight it has now.
                    val tw = targetWeight()
                    val l = outL[e]
                    val r = outR[e]
                    removeAt(e)
                    push(curL, curR, tw)
                    curL = l
                    curR = r

                    return true
                }
            }

            return false
        }

        override fun clear() {
            parkedL = null
            parkedR = null

            if (curL == null) {
                return // already heading for dry: idempotent
            }

            val tw = targetWeight()

            for (e in 0 until count) {
                if (outL[e] == null) {
                    removeAt(e) // dry was fading out: it turns around

                    break
                }
            }

            push(curL, curR, tw)
            curL = null
            curR = null
        }

        override fun reset() {
            for (e in 0 until count) {
                outL[e] = null
                outR[e] = null
            }

            count = 0
            parkedL = null
            parkedR = null
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
                accL = AudioBuffer(n)
                accR = AudioBuffer(n)
            }

            val dL = dryL
            val dR = dryR
            val sL = wetL
            val sR = wetR
            val aL = accL
            val aR = accR
            val mixL = mix.left
            val mixR = mix.right
            val fL = outL
            val fR = outR
            val from = outFrom
            val pos = outPos
            val c = count
            val len = fadeLen
            val inv = invFadeLen

            // Keep the dry input: every outgoing pair and the dry partner read it.
            mixL.copyInto(dL, 0, 0, n)
            mixR.copyInto(dR, 0, 0, n)

            // The target in place: the mix becomes its output (untouched when the target is dry).
            val tL = curL
            val tR = curR

            if (tL != null && tR != null) {
                tL.process(mixL, 0, n)
                tR.process(mixR, 0, n)
            }

            aL.fill(0.0, 0, n)
            aR.fill(0.0, 0, n)

            // out = target + sum(w * (entry - target)), which is (1 - sum w) * target + sum(w * entry).
            for (e in 0 until c) {
                val bL = fL[e]
                val bR = fR[e]
                val srcL: AudioBuffer
                val srcR: AudioBuffer

                if (bL != null && bR != null) {
                    // Every entry runs the whole block, so its own state stays continuous.
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

                val w0 = from[e]
                val p0 = pos[e]
                val end = minOf(n, len - p0)

                // The weight counts DOWN to the landing, so it is exactly 0 there, never a rounding.
                for (k in 0 until end) {
                    val w = w0 * ((len - p0 - k) * inv)

                    aL[k] += w * (srcL[k] - mixL[k])
                    aR[k] += w * (srcR[k] - mixR[k])
                }

                pos[e] = p0 + n
            }

            for (k in 0 until n) {
                mixL[k] += aL[k]
                mixR[k] += aR[k]
            }

            // Finished entries leave (their weight has landed on 0), in order.
            var kept = 0

            for (e in 0 until c) {
                if (pos[e] < len) {
                    if (kept != e) {
                        fL[kept] = fL[e]
                        fR[kept] = fR[e]
                        from[kept] = from[e]
                        pos[kept] = pos[e]
                    }

                    kept++
                }
            }

            for (e in kept until c) {
                fL[e] = null
                fR[e] = null
            }

            count = kept

            // A parked change goes in as soon as a bank has freed, at this block boundary.
            val pL = parkedL
            val pR = parkedR

            if (pL != null && pR != null && banks() < MAX_BANKS) {
                set(pL, pR)
            }

            if (count == 0) {
                if (curL != null) {
                    engaged.enter()
                } else {
                    off.enter()
                }
            }
        }

        /** Banks sounding now: the target if it is a pair, plus every outgoing pair. */
        private fun banks(): Int {
            var b = if (curL != null) 1 else 0

            for (e in 0 until count) {
                if (outL[e] != null) {
                    b++
                }
            }

            return b
        }

        /** The weight entry [e] has at the next sample to be processed. */
        private fun weightOf(e: Int): Double = outFrom[e] * ((fadeLen - outPos[e]) * invFadeLen)

        /** The target's weight at the next sample: the complement of every outgoing weight. */
        private fun targetWeight(): Double {
            var sum = 0.0

            for (e in 0 until count) {
                sum += weightOf(e)
            }

            return 1.0 - sum
        }

        /**
         * Appends an outgoing entry at weight [w]. Never over capacity: `enter` starts from 0; `set`
         * pushes only below the cap, with at most MAX_BANKS - 1 banks sounding and so at most
         * MAX_BANKS - 1 entries (the dry entry exists only while the target is a pair, which is then
         * one of the banks); `clear` pushes the target after removing the dry entry, or, with no dry
         * entry, onto at most MAX_BANKS - 1 outgoing banks; `resume` removes before it pushes.
         */
        private fun push(l: AudioFilter?, r: AudioFilter?, w: Double) {
            outL[count] = l
            outR[count] = r
            outFrom[count] = w
            outPos[count] = 0
            count++
        }

        private fun removeAt(index: Int) {
            for (e in index until count - 1) {
                outL[e] = outL[e + 1]
                outR[e] = outR[e + 1]
                outFrom[e] = outFrom[e + 1]
                outPos[e] = outPos[e + 1]
            }

            count--
            outL[count] = null
            outR[count] = null
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
     * Whether any field of this swap references [filter]: the target, an outgoing entry or the
     * parked pair. [KatalystEqEffect] asks it to find a bank it may zero and reuse (a bank this
     * answers false for is silent to the listener); `KatalystFilterSwapSpec` asks it to see that a
     * finished fade and a reset leave no dead bank alive, which the output cannot show.
     */
    internal fun holds(filter: AudioFilter): Boolean =
        curL === filter || curR === filter || crossfading.references(filter)

    /**
     * INTENT: the owner's latest word was a pair (set, or resumed), not off. Flips synchronously in
     * [set], [resume], [clear] and [reset], whatever is still fading.
     */
    val active: Boolean get() = curL != null || crossfading.parkedL != null

    /** SOUND: a pair may still be heard (the swap is not Off). */
    val sounding: Boolean get() = state !== off

    /** Install a NEW pair: it fades in over whatever sounds now (installed at once while fresh). */
    fun set(left: AudioFilter, right: AudioFilter) {
        state.set(left, right)
    }

    /**
     * The owner wants back the pair whose left filter is [left], the one it installed last: true
     * when that pair is still sounding (the fade turns around where it stands), false when it has
     * faded out, and the host then installs a fresh one.
     */
    fun resume(left: AudioFilter): Boolean = state.resume(left)

    /** Turn the stage off: the pair in service fades to dry, then is released (at once while fresh). */
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
