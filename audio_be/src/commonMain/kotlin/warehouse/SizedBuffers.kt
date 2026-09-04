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
 *    when its owner gives it back. A request takes the **smallest idle buffer that is big enough,
 *    up to one class above the need** (a 2 s request with only 4 s and 8 s idle takes the 4 s; with
 *    only the 8 s idle it allocates a 2 s — review round 4: an oversized ring makes every later
 *    tail scan and clear proportional to the ring, not the delay); an empty shelf allocates. A
 *    miss is cheap — the smallest class is ~0.4 MB — and a cache's worst case is empty, which is
 *    today's behaviour minus 97 % of the cost.
 * 3. **Bounded by a byte budget, on IDLE memory only.** Over budget on return → free the largest
 *    first. Being wrong about the budget is never audible: too small means a few more allocations
 *    on re-run, too big means idle memory. So it is a constant, not a mechanism.
 *
 * **Every buffer that leaves [rent] is all zeros** — fresh allocations are zero by construction, and
 * a returned one is cleared before it is rented again. A rented ring carrying a previous owner's
 * tail would replay it (the hazard `DelayLine.reset()`'s KDoc warns about). **The clearing is
 * DEFERRED housekeeping, not done at return** (review round 3): every return site runs on the audio
 * thread — a grow inside `configure`, an engine's disposal inside the render callback, a master
 * chain's eviction inside a command drain — and clearing there is O(ring) at the worst moment (a
 * 20 s master ring is 24 MB; sixteen warmup orbits retired in one block were ~19 MB). [giveBack] is
 * O(1) and marks the buffer DIRTY; [housekeep] clears a bounded number of frames per call, and the
 * backend calls it once per block; [rent] prefers a clean buffer and cleans a dirty one on the spot
 * only when nothing clean fits — which costs what the allocation it replaces would have cost.
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
    /** An idle buffer and how much of it is already zero: `cleanFrames == size` means clean. */
    private class Idle(val buffer: StereoBuffer, var cleanFrames: Int)

    private val shelf = ArrayList<Idle>()

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

    /** Frames zeroed by [housekeep] so far. Monotone; the per-block cost is `<= baseFrames` frames. */
    var housekeptFrames: Double = 0.0
        private set

    /** Rents that had to clean a dirty buffer synchronously because nothing clean fitted. */
    var syncCleans: Int = 0
        private set

    /**
     * Idle buffers not yet fully zeroed. Maintained by [giveBack], [housekeep], [rent] and the drop
     * loop, so the per-block [housekeep] is an integer compare while nothing is dirty — which is
     * 100 % of playing time (review round 4).
     */
    var dirtyCount: Int = 0
        private set

    /** True when every idle buffer is fully zeroed — the warmup waits for this before `BackendReady`. */
    val isClean: Boolean get() = dirtyCount == 0

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

        // Best-fit-up, bounded: a candidate is acceptable up to ONE class above the need (a 2 s
        // request takes an idle 4 s — the maintainer's example — never an idle 32 s). Among the
        // acceptable, a clean buffer beats a dirty one whatever its class, because idle memory is
        // plentiful and audio-thread time is not; a dirty one is zeroed on the spot, which costs
        // what the allocation it replaces would (a fresh array is zeroed by the runtime). The cap
        // is what keeps that claim true — a dirty 20 s master ring handed to a 0.25 s delay would be
        // a 24 MB clear inside an onset block — and keeps `DelayLine.hasTail()`'s whole-ring scan
        // proportional to the delay, not to whatever happened to be idle (review round 4). Past the
        // cap the request allocates; only when allocation fails does an oversized idle buffer serve,
        // clean before dirty: the big clear beats silence.
        val cap = if (need <= Int.MAX_VALUE / 2) 2 * need else Int.MAX_VALUE
        var best: Idle? = null
        var bestIsClean = false
        var oversized: Idle? = null
        var oversizedIsClean = false
        for (i in shelf.indices) {
            val candidate = shelf[i]
            val size = candidate.buffer.left.size
            if (size < need) {
                continue
            }
            val clean = candidate.cleanFrames == size
            if (size > cap) {
                val better = oversized == null ||
                    (clean && !oversizedIsClean) ||
                    (clean == oversizedIsClean && size < oversized.buffer.left.size)
                if (better) {
                    oversized = candidate
                    oversizedIsClean = clean
                }
                continue
            }
            val better = best == null ||
                (clean && !bestIsClean) ||
                (clean == bestIsClean && size < best.buffer.left.size)
            if (better) {
                best = candidate
                bestIsClean = clean
            }
        }

        if (best != null) {
            return take(best, clean = bestIsClean)
        }

        if (allocateOnMiss) {
            val fresh = allocate(need)

            if (fresh != null) {
                allocations++

                return fresh
            }

            failures++
        }

        if (oversized != null) {
            return take(oversized, clean = oversizedIsClean)
        }

        return null
    }

    private fun take(idle: Idle, clean: Boolean): StereoBuffer {
        shelf.remove(idle)
        shelfBytes -= bytesOf(idle.buffer)
        hits++

        if (!clean) {
            clearFrom(idle)
            dirtyCount--
            syncCleans++
        }

        return idle.buffer
    }

    /**
     * Returns [buffer] to the shelf, DIRTY — O(1), nothing is touched (see the class KDoc: every
     * return site is on the audio thread). If that puts the shelf over budget, the largest idle
     * buffers are freed until it is not — which may be this one, if it alone exceeds the budget.
     */
    fun giveBack(buffer: StereoBuffer) {
        for (i in shelf.indices) {
            if (shelf[i].buffer === buffer) {
                doubleReturns++

                return
            }
        }

        shelf.add(Idle(buffer, cleanFrames = 0))
        shelfBytes += bytesOf(buffer)
        dirtyCount++

        while (shelfBytes > budgetBytes && shelf.isNotEmpty()) {
            var largest = shelf[0]
            for (i in shelf.indices) {
                val candidate = shelf[i]
                if (candidate.buffer.left.size > largest.buffer.left.size) {
                    largest = candidate
                }
            }
            shelf.remove(largest)
            shelfBytes -= bytesOf(largest.buffer)
            if (largest.cleanFrames < largest.buffer.left.size) {
                dirtyCount--
            }
            dropped++
        }
    }

    /**
     * Zeroes up to [maxFrames] frames of dirty idle buffers, oldest return first, and returns how
     * many it zeroed (0 when the shelf is clean — an integer compare, no walk). The backend calls
     * this once per block with the default budget: one class-0 ring ([baseFrames], rate-derived),
     * ~385 KB of stores at 48 kHz, a few percent of a block on a phone, so a sixteen-ring warmup
     * teardown is clean sixteen blocks later and a 20 s master ring in about fifty.
     */
    fun housekeep(maxFrames: Int = baseFrames): Int {
        if (dirtyCount == 0) {
            return 0
        }

        var budget = maxFrames

        for (i in shelf.indices) {
            if (budget <= 0) {
                break
            }
            val idle = shelf[i]
            val size = idle.buffer.left.size
            val dirty = size - idle.cleanFrames
            if (dirty == 0) {
                continue
            }
            val n = if (dirty < budget) dirty else budget
            val from = idle.cleanFrames
            idle.buffer.left.fill(0.0, from, from + n)
            idle.buffer.right.fill(0.0, from, from + n)
            idle.cleanFrames += n
            if (idle.cleanFrames == size) {
                dirtyCount--
            }
            budget -= n
        }

        val done = maxFrames - budget
        housekeptFrames += done

        return done
    }

    private fun clearFrom(idle: Idle) {
        val size = idle.buffer.left.size
        if (idle.cleanFrames < size) {
            idle.buffer.left.fill(0.0, idle.cleanFrames, size)
            idle.buffer.right.fill(0.0, idle.cleanFrames, size)
            idle.cleanFrames = size
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
