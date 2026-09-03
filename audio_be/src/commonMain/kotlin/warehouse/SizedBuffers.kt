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
 * (the hazard `DelayLine.reset()`'s KDoc warns about). Clearing happens at return time, i.e. at
 * eviction or disposal, never at the moment a note needs the ring.
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

    /** Bytes currently idle on the shelf. Always `<= budgetBytes` after any call returns. */
    var shelfBytes: Int = 0
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
     * enough and allocation failed. Best-fit-up from the shelf; allocate on a miss.
     */
    fun rent(minFrames: Int): StereoBuffer? {
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
     */
    fun giveBack(buffer: StereoBuffer) {
        buffer.clear()
        shelf.add(buffer)
        shelfBytes += bytesOf(buffer)

        while (shelfBytes > budgetBytes && shelf.isNotEmpty()) {
            var largest = shelf[0]
            for (candidate in shelf) {
                if (candidate.left.size > largest.left.size) {
                    largest = candidate
                }
            }
            shelf.remove(largest)
            shelfBytes -= bytesOf(largest)
            dropped++
        }
    }

    companion object {
        private const val BYTES_PER_FRAME = 2 * 8 // stereo, Double

        /** A ring shelf whose class 0 is [ResourceWarehouse.MIN_RING_SECONDS] at [sampleRate]. */
        fun forRings(
            sampleRate: Int,
            budgetBytes: Int = ResourceWarehouse.SHELF_BUDGET_BYTES,
            allocate: (frames: Int) -> StereoBuffer? = ::allocateOrNull,
        ): SizedBuffers = SizedBuffers(
            baseFrames = (sampleRate * ResourceWarehouse.MIN_RING_SECONDS).toInt(),
            budgetBytes = budgetBytes,
            allocate = allocate,
        )

        fun bytesOf(buffer: StereoBuffer): Int = buffer.left.size * BYTES_PER_FRAME

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
