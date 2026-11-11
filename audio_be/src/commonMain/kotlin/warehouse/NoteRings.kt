/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

/**
 * The warehouse's shelf of per-note rings (KLANG-00429, audit finding L5-03): the mono lines a voice's Ignitor tree
 * builds with, the Karplus strings' delay lines (`KarplusString`, 2,500 frames each, one per pluck and one per
 * superpluck string) and the shimmer's grain ring (`shimmerRingFrames`, 14,402 frames at its default at 48 kHz).
 * Before, every such note allocated its rings at its build, on the audio thread, and never gave them back. Now a
 * voice's tree rents them at its build through the voice's [NoteRingLease], and the voice gives them back when it
 * leaves its engine (`VoiceScheduler`, on every way out).
 *
 * The delay rings' rules ([SizedBuffers]), simpler, one ring per line:
 *
 * 1. **Class-sized, powers of two.** A request rents the smallest class of `2^k` frames at or above it, from
 *    [MIN_CLASS_FRAMES] (4096 frames, 32 KB: a Karplus line's 2,500 frames) to [MAX_CLASS_FRAMES] (131,072 frames,
 *    1 MB: the shimmer's largest ring at the base rate, 96,000 frames). A ring is therefore longer than its user asked
 *    for, and **its user reads its own length, never the array's size** (`KarplusString.length`, the shimmer's
 *    `ringSize`). A request past the top class is allocated at its own size and never shelved (a shimmer inside a
 *    region of 2x or more whose ring is past 1 MB).
 * 2. **Only a clean ring is rented; nothing is cleared on the note-on path.** A return is O(1) and leaves the ring
 *    DIRTY; the per-block [housekeep] zeroes dirty rings a bounded slice at a time ([HOUSEKEEP_FRAMES]) and puts them
 *    back among the clean ones. A class with no clean ring allocates a fresh one, zero by construction, as a build
 *    always did, counted in [allocations]: a dirty ring is never handed out, not even cleaned on the spot.
 * 3. **Bounded on idle bytes** ([budgetBytes], its part of the warehouse's `SHELF_BUDGET_BYTES`): a return that
 *    would take the idle rings over it is dropped, left to the garbage collector, and counted in [dropped]. Rented
 *    rings are working memory and not bounded here.
 * 4. **Stocked by return**, and once by the warmup's stock of Karplus lines ([stock]).
 *
 * A double return is refused and counted ([doubleReturns]): one ring on the shelf twice would be rented to two voices,
 * two strings writing one line. The lease gives each ring back once; this is the second wall.
 */
class NoteRings(
    /** Bound on IDLE bytes held on the shelf. */
    val budgetBytes: Int = BUDGET_BYTES,
) {
    /** The clean rings of each class, index `k` holding rings of `MIN_CLASS_FRAMES shl k` frames. */
    private val clean: Array<ArrayList<DoubleArray>> = Array(CLASS_COUNT) { ArrayList<DoubleArray>() }

    /** The dirty rings of each class, waiting for [housekeep]. */
    private val dirty: Array<ArrayList<DoubleArray>> = Array(CLASS_COUNT) { ArrayList<DoubleArray>() }

    /** The dirty ring [housekeep] is zeroing, taken off [dirty]; null when none is half done. */
    private var clearing: DoubleArray? = null

    /** How many frames of [clearing] are zero, from its start. */
    private var clearedFrames: Int = 0

    /** Bytes idle on the shelf, clean and dirty. Always `<= budgetBytes` after any call returns. */
    var idleBytes: Double = 0.0
        private set

    /** Rings idle on the shelf, clean and dirty. */
    var idleCount: Int = 0
        private set

    /** Idle rings not yet zeroed (the one being zeroed included); the per-block [housekeep] is a compare while 0. */
    var dirtyCount: Int = 0
        private set

    /** Bumped on every change to the shelf or its counters: the stats snapshot rebuilds only then. */
    var version: Int = 0
        private set

    // Counters, for specs and for the diagnostics feed. Never reset; monotone.

    /** Rings allocated because the class had no clean ring (the fallback), or put on the shelf by [stock]. */
    var allocations: Int = 0
        private set

    /** Rents served from the shelf. */
    var hits: Int = 0
        private set

    /** Returns not shelved: over the byte bound, or a ring of no class (past [MAX_CLASS_FRAMES]). */
    var dropped: Int = 0
        private set

    /** [giveBack] calls for a ring that was already on the shelf; refused. */
    var doubleReturns: Int = 0
        private set

    /** Frames zeroed by [housekeep] so far. */
    var housekeptFrames: Double = 0.0
        private set

    /** True when every idle ring is zeroed: the warmup waits for this before `BackendReady`. */
    val isClean: Boolean get() = dirtyCount == 0

    /**
     * The frames a request of [minFrames] rents: the smallest power of two at or above it, at least
     * [MIN_CLASS_FRAMES]; past [MAX_CLASS_FRAMES] the request itself (an unshelved ring).
     */
    fun classFrames(minFrames: Int): Int {
        if (minFrames > MAX_CLASS_FRAMES) {
            return minFrames
        }

        var frames = MIN_CLASS_FRAMES

        while (frames < minFrames) {
            frames = frames shl 1
        }

        return frames
    }

    /**
     * A zeroed ring of at least [minFrames] frames ([classFrames]): a clean one off the shelf, else a fresh one. Never
     * a dirty one. O(1), and allocation free on a hit.
     */
    fun rent(minFrames: Int): DoubleArray {
        val frames = classFrames(minFrames)
        val k = classIndexOf(frames)

        version++

        if (k >= 0) {
            val shelf = clean[k]

            if (shelf.isNotEmpty()) {
                val ring = shelf.removeAt(shelf.lastIndex)

                idleBytes -= bytesOfFrames(frames)
                idleCount--
                hits++

                return ring
            }
        }

        allocations++

        return DoubleArray(frames)
    }

    /**
     * Takes [ring] back, DIRTY: nothing is touched here (a return runs on the audio thread, when a voice ends). Not
     * shelved when it is of no class or would take the idle bytes over [budgetBytes] ([dropped]), nor when it is on the
     * shelf already ([doubleReturns]).
     */
    fun giveBack(ring: DoubleArray) {
        version++

        val k = classIndexOf(ring.size)

        if (k < 0) {
            dropped++

            return
        }

        if (isIdle(ring = ring, k = k)) {
            doubleReturns++

            return
        }

        val bytes = bytesOfFrames(ring.size)

        if (idleBytes + bytes > budgetBytes) {
            dropped++

            return
        }

        dirty[k].add(ring)
        idleBytes += bytes
        idleCount++
        dirtyCount++
    }

    /**
     * Zeroes up to [maxFrames] frames of dirty rings, the smallest class first, and returns how many it zeroed (0 when
     * the shelf is clean, a compare). A ring zeroed to its end joins the clean rings of its class. The backend calls
     * this once per block ([ResourceWarehouse.housekeep]).
     */
    fun housekeep(maxFrames: Int = HOUSEKEEP_FRAMES): Int {
        if (dirtyCount == 0) {
            return 0
        }

        var budget = maxFrames

        while (budget > 0) {
            var ring = clearing

            if (ring == null) {
                ring = nextDirty() ?: break
                clearing = ring
                clearedFrames = 0
            }

            val size = ring.size
            val left = size - clearedFrames
            val n = if (left < budget) left else budget

            ring.fill(0.0, clearedFrames, clearedFrames + n)
            clearedFrames += n
            budget -= n

            if (clearedFrames == size) {
                clean[classIndexOf(size)].add(ring)
                clearing = null
                dirtyCount--
            }
        }

        val done = maxFrames - budget

        if (done > 0) {
            housekeptFrames += done
            version++
        }

        return done
    }

    /**
     * Puts fresh rings of the class of [minFrames] on the shelf until it holds [count] idle rings of that class, inside
     * the byte bound: the warmup's stock (`WarmupRunner`), the one place this shelf is filled by prediction. A fresh
     * ring is zero, so it is clean at once. Not for render code: it allocates.
     */
    fun stock(minFrames: Int, count: Int) {
        val frames = classFrames(minFrames)
        val k = classIndexOf(frames)

        if (k < 0) {
            return
        }

        var held = clean[k].size + dirty[k].size + (if (clearing?.size == frames) 1 else 0)
        val bytes = bytesOfFrames(frames)

        while (held < count && idleBytes + bytes <= budgetBytes) {
            clean[k].add(DoubleArray(frames))
            idleBytes += bytes
            idleCount++
            allocations++
            held++
            version++
        }
    }

    /** The next dirty ring, the smallest class first, taken off its list; null when none is left. */
    private fun nextDirty(): DoubleArray? {
        for (k in 0 until CLASS_COUNT) {
            val list = dirty[k]

            if (list.isNotEmpty()) {
                return list.removeAt(list.lastIndex)
            }
        }

        return null
    }

    /** True when [ring], of class [k], is on the shelf already: clean, dirty or being zeroed. */
    private fun isIdle(ring: DoubleArray, k: Int): Boolean {
        if (clearing === ring) {
            return true
        }

        val c = clean[k]

        for (i in 0 until c.size) {
            if (c[i] === ring) {
                return true
            }
        }

        val d = dirty[k]

        for (i in 0 until d.size) {
            if (d[i] === ring) {
                return true
            }
        }

        return false
    }

    companion object {
        /** The smallest class: 4096 frames, 32 KB, the class of a Karplus line's 2,500 frames. */
        const val MIN_CLASS_FRAMES: Int = 4096

        /** The classes, 4096 to 131,072 frames. */
        const val CLASS_COUNT: Int = 6

        /** The largest class: 131,072 frames, 1 MB, the class of the shimmer's largest ring at the base rate (96,000). */
        const val MAX_CLASS_FRAMES: Int = MIN_CLASS_FRAMES shl (CLASS_COUNT - 1)

        /**
         * The shelf's bound on idle bytes: 4 MB, 128 Karplus lines (sixteen superplucks of eight strings), carved out of
         * the warehouse's `SHELF_BUDGET_BYTES` (the delay rings keep the rest). Like that one, a constant, not a
         * mechanism: being wrong costs a few allocations or some idle memory, never sound.
         */
        const val BUDGET_BYTES: Int = 4 * 1024 * 1024

        /** Frames [housekeep] zeroes per block: 16,384 (128 KB), four Karplus lines. */
        const val HOUSEKEEP_FRAMES: Int = 16_384

        private const val BYTES_PER_FRAME: Double = 8.0

        /** The bytes of a ring of [frames] frames. */
        fun bytesOfFrames(frames: Int): Double = frames * BYTES_PER_FRAME

        /** The class index of a ring of [frames] frames, or -1 when it is of no class. */
        fun classIndexOf(frames: Int): Int {
            if (frames < MIN_CLASS_FRAMES || frames > MAX_CLASS_FRAMES || (frames and (frames - 1)) != 0) {
                return -1
            }

            return frames.countTrailingZeroBits() - MIN_CLASS_FRAMES.countTrailingZeroBits()
        }
    }
}
