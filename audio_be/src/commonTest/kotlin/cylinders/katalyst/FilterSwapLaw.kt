/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * The switching law of [KatalystFilterSwap] as decided with the maintainer on 2026-09-19 (Katalyst
 * step 5c-6) and narrowed to TWO BANKS on 2026-09-20 (step 5c-11), written the plainest way, per
 * sample and from scratch, as the ORACLE the specs compare against. It knows nothing about
 * filters: a "bank" is an id whose output at a sample the caller supplies, and id [DRY] is the dry
 * input.
 *
 * The law:
 * - the output is `target + w * (entry - target)`, i.e. the target weighs the complement of the ONE
 *   outgoing entry's weight;
 * - the outgoing entry leaves at the weight it has at that sample and falls linearly to exactly 0
 *   over [fadeLen] samples of its own; it is gone once it has landed;
 * - `set` makes the target outgoing and the new bank the target; `clear` does the same with dry as
 *   the new target; `resume` takes the fading entry back as the target (the old target goes out at
 *   its weight);
 * - **while an entry is still fading, `set` and `clear` are REFUSED** and change nothing, because a
 *   second entry would make a third bank sound. The parking that waits for the landing is the
 *   HOST's, not this law's: a spec drives this law at the block the host installs, and a host that
 *   installed a parked change early would diverge from it sample for sample;
 * - until the first block after construction or `reset`, `set` and `clear` act at once;
 * - `reset` is a hard cut to dry.
 */
internal class FilterSwapLaw(private val fadeLen: Int) {

    companion object {
        const val DRY = -1
        private const val NONE = -2
    }

    private var target = DRY
    private var outId = NONE
    private var outFrom = 0.0
    private var outStart = 0
    private var fresh = true
    private var now = 0

    /** Whether a change can start now: no entry is fading. */
    val settled: Boolean get() = outId == NONE

    /** Banks sounding now, the target included (dry is not a bank). */
    val banks: Int get() = (if (target != DRY) 1 else 0) + (if (outId != NONE && outId != DRY) 1 else 0)

    /** Ids that may be heard now: the target (unless dry) and the outgoing bank. */
    val sounding: List<Int>
        get() = (if (target != DRY) listOf(target) else emptyList()) +
            (if (outId != NONE && outId != DRY) listOf(outId) else emptyList())

    private fun weight(sample: Int): Double =
        outFrom * maxOf(0, fadeLen - (sample - outStart)).toDouble() / fadeLen

    private fun targetWeight(): Double = if (settled) 1.0 else 1.0 - weight(now)

    private fun push(id: Int, from: Double) {
        outId = id
        outFrom = from
        outStart = now
    }

    fun set(id: Int) {
        if (fresh) {
            target = id
            outId = NONE

            return
        }

        if (!settled) {
            return // refused: the host parks it
        }

        push(target, 1.0)
        target = id
    }

    fun clear() {
        if (fresh) {
            target = DRY
            outId = NONE

            return
        }

        if (target == DRY || !settled) {
            return // already heading for dry, or refused while an entry fades
        }

        push(target, 1.0)
        target = DRY
    }

    fun resume(id: Int): Boolean {
        if (target == id) {
            return true
        }

        if (outId != id) {
            return false
        }

        val w = targetWeight()

        push(target, w)
        target = id

        return true
    }

    fun reset() {
        outId = NONE
        target = DRY
        fresh = true
    }

    /**
     * One block of [n] samples. [output] answers the output of bank `id` at block-local sample `k`
     * (and must answer [DRY] with the dry input).
     */
    fun block(n: Int, output: (id: Int, k: Int) -> Double): DoubleArray {
        fresh = false

        val y = DoubleArray(n)

        for (k in 0 until n) {
            val t = output(target, k)

            y[k] = if (settled) t else t + weight(now + k) * (output(outId, k) - t)
        }

        now += n

        if (!settled && now - outStart >= fadeLen) {
            outId = NONE
        }

        return y
    }
}
