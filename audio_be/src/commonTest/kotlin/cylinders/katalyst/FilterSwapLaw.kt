/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * The switching law of [KatalystFilterSwap] as decided with the maintainer on 2026-09-19 (Katalyst
 * step 5c-6), written the plainest way, per sample and from scratch, as the ORACLE the specs
 * compare against. It knows nothing about filters: a "bank" is an id whose output at a sample the
 * caller supplies, and id [DRY] is the dry input.
 *
 * The law:
 * - the output is `target + sum(w_i * (entry_i - target))`, i.e. the target weighs the complement
 *   of every outgoing weight;
 * - an outgoing entry leaves at the weight it has at that sample and falls linearly to exactly 0
 *   over [fadeLen] samples of its own; it is gone once it has landed;
 * - `set` makes the target outgoing and the new bank the target; `clear` does the same with dry as
 *   the new target (a dry entry already fading out turns around instead); `resume` takes a fading
 *   entry back as the target (the old target goes out at its weight);
 * - at [maxBanks] banks sounding (dry is not a bank) a `set` is PARKED, the latest wins, and goes
 *   in at the first block boundary after a bank has landed; `clear`, `resume` and `reset` drop it;
 * - until the first block after construction or `reset`, `set` and `clear` act at once;
 * - `reset` is a hard cut to dry.
 */
internal class FilterSwapLaw(private val fadeLen: Int, private val maxBanks: Int) {

    companion object {
        const val DRY = -1
        private const val NONE = -2
    }

    private class Entry(val id: Int, val from: Double, val start: Int)

    private val out = mutableListOf<Entry>()
    private var target = DRY
    private var parked = NONE
    private var fresh = true
    private var now = 0

    /** Banks sounding now, the target included. */
    val banks: Int get() = (if (target != DRY) 1 else 0) + out.count { it.id != DRY }

    /** Ids that may be heard now: the target (unless dry) and every outgoing bank. */
    val sounding: List<Int> get() = (if (target != DRY) listOf(target) else emptyList()) + out.filter { it.id != DRY }.map { it.id }

    val parkedId: Int? get() = if (parked == NONE) null else parked

    private fun weight(e: Entry, sample: Int): Double =
        e.from * maxOf(0, fadeLen - (sample - e.start)).toDouble() / fadeLen

    private fun targetWeight(): Double = 1.0 - out.sumOf { weight(it, now) }

    fun set(id: Int) {
        if (fresh) {
            target = id
            out.clear()

            return
        }

        if (banks >= maxBanks) {
            parked = id

            return
        }

        parked = NONE
        out += Entry(target, targetWeight(), now)
        target = id
    }

    fun clear() {
        parked = NONE

        if (fresh) {
            target = DRY
            out.clear()

            return
        }

        if (target == DRY) {
            return
        }

        val w = targetWeight()
        out.removeAll { it.id == DRY }
        out += Entry(target, w, now)
        target = DRY
    }

    fun resume(id: Int): Boolean {
        parked = NONE

        if (target == id) {
            return true
        }

        val found = out.firstOrNull { it.id == id } ?: return false
        val w = targetWeight()
        out.remove(found)
        out += Entry(target, w, now)
        target = id

        return true
    }

    fun reset() {
        out.clear()
        target = DRY
        parked = NONE
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
            val s = now + k
            val t = output(target, k)
            var acc = 0.0

            for (e in out) {
                acc += weight(e, s) * (output(e.id, k) - t)
            }

            y[k] = t + acc
        }

        now += n
        out.removeAll { now - it.start >= fadeLen }

        if (parked != NONE && banks < maxBanks) {
            val p = parked
            parked = NONE
            set(p)
        }

        return y
    }
}
