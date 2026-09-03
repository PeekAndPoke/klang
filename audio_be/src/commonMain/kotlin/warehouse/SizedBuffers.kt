/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * The shelf for large stereo buffers — delay rings today, reverb units next (`docs/plans/resource-warehouse.md`).
 *
 * Three rules, and they are the whole design:
 *
 * 1. **Class-sized.** A request for `minFrames` is rounded UP to the smallest class that holds it,
 *    where class `k` is `baseFrames * 2^k`. The ladder is open-ended (no ceiling: ask for an hour,
 *    get an hour or `null`). Ratio 2 was chosen over a tighter ladder because the ladder's job is
 *    HEADROOM — a ring with slack absorbs a later, slightly longer delay without a regrow — not
 *    packing; right-sizing already took memory down 15–30×.
 * 2. **Stocked by return, never by prediction.** Nothing is pre-filled by guessing which classes a
 *    song will want, because any such guess can be exactly wrong. A buffer lands on the shelf only
 *    when its owner gives it back. A request takes the **smallest idle buffer that is big enough**
 *    (a 2 s request with only 4 s and 8 s idle takes the 4 s); an empty shelf allocates. A miss is
 *    cheap — the smallest class is ~0.4 MB — and a cache's worst case is empty, which is today's
 *    behaviour minus 97 % of the cost.
 * 3. **Bounded by a byte budget, on IDLE memory only.** Over budget on return → free the largest
 *    first. Being wrong about the budget is never audible: too small means a few more allocations
 *    on re-run, too big means idle memory. So it is a constant, not a mechanism.
 *
 * **Every buffer that leaves [rent] is all zeros** — fresh allocations are zero by construction and
 * [giveBack] clears before shelving. A rented ring carrying a previous owner's tail would replay it
 * (the hazard `DelayLine.reset()`'s KDoc warns about). Clearing happens at return time. For eviction
 * and disposal that is away from any onset; for a GROW it is not — the old ring is returned inside
 * the same `configure` that rents the new one, so a grow pays one clear of the OLD (smaller) ring at
 * note time on top of the new allocation. That is the accepted cost of a grow, proportional to the
 * ring being outgrown, not the new one.
 *
 * **Never shrinks a buffer.** Growth is the owner's business (rent a bigger class, migrate, give the
 * old one back). The only thing that ever gets smaller is the shelf.
 *
 * **Allocation may fail, and says so with `null`.** [allocate] is the one place in the backend a
 * large buffer is created, so it is the one place out-of-memory is caught. Today an uncaught
 * allocation failure inside `process()` stops the AudioWorkletProcessor permanently. Injectable so a
 * spec can fail it without exhausting the JVM.
 */
class SizedBuffers(
    /** Frames in class 0 — the smallest ring. `ResourceWarehouse` sets it to half a second. */
    val baseFrames: Int,
    /** Bound on IDLE bytes held on the shelf. Working memory is demand-driven and unbounded. */
    val budgetBytes: Int,
    private val allocate: (frames: Int) -> StereoBuffer? = ::allocateOrNull,
) {
    private val shelf = ArrayList<StereoBuffer>()

    /**
     * Bytes currently idle on the shelf. Always `<= budgetBytes` after any call returns. A `Double`
     * because the ladder has no ceiling: a 45-minute ring at 48 kHz is 2.1 GB, past `Int`, and a
     * wrapped-negative total would switch the budget off for the life of the shelf. (No `Long` in
     * audio paths by house rule; a `Double` counts bytes exactly to 2^53.)
     */
    var shelfBytes: Double = 0.0
        private set

    /** How many buffers are idle on the shelf. */
    val shelfCount: Int get() = shelf.size

    // Counters, for specs and for the diagnostics feed. Never reset; monotone.
    var allocations: Int = 0
        private set
    var hits: Int = 0
        private set
    var failures: Int = 0
        private set
    var dropped: Int = 0
        private set

    /**
     * [giveBack] calls for a buffer that was ALREADY on the shelf. Guarded, not thrown: a double
     * return would put one ring on the shelf twice and rent it to two live orbits — two delays
     * writing one ring, heard as cross-talk. Eviction (2f) is where that mistake is easiest to make.
     */
    var doubleReturns: Int = 0
        private set

    /**
     * The smallest class holding [minFrames]: `baseFrames * 2^k` with the least `k >= 0` such that
     * it is `>= minFrames`. Anything at or below the base gets the base.
     */
    fun classFrames(minFrames: Int): Int {
        var frames = baseFrames

        while (frames < minFrames && frames <= Int.MAX_VALUE / 2) {
            frames *= 2
        }

        // Past Int range on the ladder (12 hours at 48 kHz) — hand the request through as is; the
        // allocation will fail and report null, which is the right answer for a 24 GB ring.
        return if (frames < minFrames) minFrames else frames
    }

    /**
     * A zeroed stereo buffer of at least [minFrames] frames, or `null` if the shelf had nothing big
     * enough and allocation failed. Best-fit-up from the shelf; allocate on a miss — unless
     * [allocateOnMiss] is false, in which case a miss is `null` and counts as nothing: the caller
     * already knows allocation at this size fails and only wants what the shelf can serve for free
     * (review round 2: a latched refusal must not also block a pure shelf hit).
     */
    fun rent(minFrames: Int, allocateOnMiss: Boolean = true): StereoBuffer? {
        val need = classFrames(minFrames)

        var best: StereoBuffer? = null
        for (candidate in shelf) {
            if (candidate.left.size >= need && (best == null || candidate.left.size < best.left.size)) {
                best = candidate
            }
        }

        if (best != null) {
            shelf.remove(best)
            shelfBytes -= bytesOf(best)
            hits++

            return best
        }

        if (!allocateOnMiss) {
            return null
        }

        val fresh = allocate(need)

        if (fresh == null) {
            failures++

            return null
        }

        allocations++

        return fresh
    }

    /**
     * Returns [buffer] to the shelf, cleared. If that puts the shelf over budget, the largest idle
     * buffers are freed until it is not — which may be this one, if it alone exceeds the budget.
     * The eviction is decided FIRST and only a buffer that stays on the shelf is cleared: this runs
     * on the audio thread (a grow returns the old ring inside `configure`), and zero-filling a ring
     * that the next line drops is O(frames) for nothing (review round 2).
     */
    fun giveBack(buffer: StereoBuffer) {
        for (idle in shelf) {
            if (idle === buffer) {
                doubleReturns++

                return
            }
        }

        shelf.add(buffer)
        shelfBytes += bytesOf(buffer)

        var kept = true
        while (shelfBytes > budgetBytes && shelf.isNotEmpty()) {
            var largest = shelf[0]
            for (candidate in shelf) {
                if (candidate.left.size > largest.left.size) {
                    largest = candidate
                }
            }
            if (largest === buffer) {
                kept = false
            }
            shelf.remove(largest)
            shelfBytes -= bytesOf(largest)
            dropped++
        }

        if (kept) {
            // Everything already on the shelf was cleared when it arrived; only the newcomer needs it.
            buffer.clear()
        }
    }

    companion object {
        private const val BYTES_PER_FRAME = 2 * 8 // stereo, Double

        /**
         * A ring shelf whose class 0 holds a [ResourceWarehouse.MIN_RING_SECONDS] delay at
         * [sampleRate] — INCLUDING the [ResourceWarehouse.RING_MARGIN_FRAMES] a delay effect adds
         * for interpolation. Without the margin an exactly-0.5 s delay (a quarter at 120 BPM, the
         * most common musical delay time) needs 64 frames more than class 0 and rents class 1,
         * twice the memory, and the "corpus fits class 0" claim is false at its own boundary
         * (review round 2).
         */
        fun forRings(
            sampleRate: Int,
            budgetBytes: Int = ResourceWarehouse.SHELF_BUDGET_BYTES,
            allocate: (frames: Int) -> StereoBuffer? = ::allocateOrNull,
        ): SizedBuffers = SizedBuffers(
            baseFrames = (sampleRate * ResourceWarehouse.MIN_RING_SECONDS).toInt() + ResourceWarehouse.RING_MARGIN_FRAMES,
            budgetBytes = budgetBytes,
            allocate = allocate,
        )

        fun bytesOf(buffer: StereoBuffer): Double = bytesOfFrames(buffer.left.size)

        /** The same accounting by frame count — a 200 M-frame ring is 3.2 GB, past Int, without allocating one. */
        fun bytesOfFrames(frames: Int): Double = frames.toDouble() * BYTES_PER_FRAME

        /**
         * The one allocation site. A `RangeError` on Kotlin/JS or an `OutOfMemoryError` on the JVM
         * becomes `null`. Catching at a single large-allocation site is sound: the allocation that
         * failed never happened, so the heap is exactly as it was.
         */
        fun allocateOrNull(frames: Int): StereoBuffer? = try {
            StereoBuffer(frames)
        } catch (e: Throwable) {
            null
        }
    }
}
