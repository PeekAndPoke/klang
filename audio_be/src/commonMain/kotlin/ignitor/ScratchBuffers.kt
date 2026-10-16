/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Stack-based pool of reusable AudioBuffer buffers for binary composition operators.
 *
 * All external access goes through [use], which guarantees the buffer is returned even on exceptions.
 * Max simultaneous buffers = composition tree depth. E.g. `(a + b).mul(0.5) + c` needs 2.
 */
class ScratchBuffers(val blockFrames: Int, initialCapacity: Int = 4) {

    private val pool = ArrayList<AudioBuffer>(initialCapacity).apply {
        repeat(initialCapacity) { add(AudioBuffer(blockFrames)) }
    }
    private var nextFree = 0

    /**
     * Buffers created by [acquire] because the pool was exhausted — i.e. allocations that happened
     * INSIDE render. The goal is that this stays at zero: size the pool at build with
     * [ensureCapacity] (a voice's ignitor graph knows its own depth) so the hot path never allocates.
     */
    var lateAllocations: Int = 0
        private set

    /**
     * Unbalanced [release] calls — more releases than acquires. Guarded rather than thrown (no
     * exceptions in audio paths), and counted so the imbalance is visible instead of silently
     * corrupting `nextFree` and handing out live buffers twice.
     */
    var unbalancedReleases: Int = 0
        private set

    /**
     * Pre-grows the AudioBuffer pool to [depth] and the DoubleArray pool to [doubleDepth] buffers,
     * OUTSIDE render, so neither acquire allocates inside it. Grow-only; never shrinks.
     */
    fun ensureCapacity(depth: Int, doubleDepth: Int = depth) {
        while (pool.size < depth) {
            pool.add(AudioBuffer(blockFrames))
            version++
        }
        while (doublePool.size < doubleDepth) {
            doublePool.add(DoubleArray(blockFrames))
            version++
        }
    }

    /** Buffers in the AudioBuffer pool right now, in use or not. */
    val capacity: Int get() = pool.size

    /** Bumped when a counter or a capacity changes (never on a plain acquire/release). */
    var version: Int = 0
        private set

    /** Buffers in the DoubleArray pool right now, in use or not. */
    val doubleCapacity: Int get() = doublePool.size

    /**
     * The deepest simultaneous nesting of AudioBuffer scratch this pool has ever served — i.e. the
     * deepest ignitor graph that has rendered through it. Read it to know how much of
     * [ensureCapacity]'s pre-size a real song actually uses. The DoubleArray stack is independent
     * and has its own [doubleHighWater] (review round 2: one shared counter meant "deeper of either").
     */
    var highWater: Int = 0
        private set

    /** [highWater] for the DoubleArray stack — modulation-ratio scratch, `ModApplyingIgnitor`. */
    var doubleHighWater: Int = 0
        private set

    /** Whether the sub-pool for [factor] already exists — i.e. its first use will NOT allocate. */
    fun hasOversample(factor: Int): Boolean = factor <= 1 || oversampleIndexOf(factor) >= 0

    @PublishedApi
    internal fun acquire(): AudioBuffer {
        if (nextFree >= pool.size) {
            pool.add(AudioBuffer(blockFrames))
            lateAllocations++
            version++
        }
        val buf = pool[nextFree++]
        if (nextFree > highWater) {
            highWater = nextFree
            version++
        }
        return buf
    }

    @PublishedApi
    internal fun release() {
        if (nextFree > 0) {
            nextFree--
        } else {
            unbalancedReleases++
            version++
        }
    }

    /** Scoped access — guarantees release even on exceptions. Never leak a buffer. */
    inline fun <R> use(block: (AudioBuffer) -> R): R {
        val buf = acquire()
        try {
            return block(buf)
        } finally {
            release()
        }
    }

    // ── DoubleArray pool (same stack discipline) ────────────────────────────────

    private val doublePool = ArrayList<DoubleArray>(2)
    private var doubleNextFree = 0

    @PublishedApi
    internal fun acquireDouble(): DoubleArray {
        // Same discipline and the same counters as the AudioBuffer pool (review round 1: this half
        // had been left un-hardened, and ModApplyingIgnitor's first render allocated here).
        if (doubleNextFree >= doublePool.size) {
            doublePool.add(DoubleArray(blockFrames))
            lateAllocations++
            version++
        }
        val buf = doublePool[doubleNextFree++]
        if (doubleNextFree > doubleHighWater) {
            doubleHighWater = doubleNextFree
            version++
        }
        return buf
    }

    @PublishedApi
    internal fun releaseDouble() {
        if (doubleNextFree > 0) {
            doubleNextFree--
        } else {
            unbalancedReleases++
            version++
        }
    }

    /** Scoped access for DoubleArray buffers — same guarantees as [use]. */
    inline fun <R> useDouble(block: (DoubleArray) -> R): R {
        val buf = acquireDouble()
        try {
            return block(buf)
        } finally {
            releaseDouble()
        }
    }

    // ── Oversampled ScratchBuffers (cached by factor) ───────────────────────────

    /**
     * The sub-pools by factor, in the order they were first asked for: [oversampleFactors] holds the factor of each
     * slot of [oversamplePools], the first [oversampleCount] slots in use.
     *
     * **Two arrays scanned, not a map, ON PURPOSE** (engine follow-up 34, 2026-10-16; `audio/ref/performance.md`):
     * every oversampled node and stage asks [oversample] once per block, and a stdlib map keyed by `Int` goes through
     * the generic `hashCode(dynamic)` on Kotlin/JS, which allocates a function context on every call (about 40 B per
     * lookup on node; the bus distort at 2x asked it three times per block, about 125 B). A pool holds one to four
     * factors (the warehouse warms 2, 4, 8 and 16), so the scan is a few compares and allocates nothing. No test can
     * see the bytes: this KDoc is the guard. Do not put the sub-pools back into a map.
     */
    private var oversampleFactors = IntArray(4)
    private var oversamplePools = arrayOfNulls<ScratchBuffers>(4)
    private var oversampleCount = 0

    /** The slot of [factor] in [oversamplePools], or -1 when it has no sub-pool yet. */
    private fun oversampleIndexOf(factor: Int): Int {
        for (i in 0 until oversampleCount) {
            if (oversampleFactors[i] == factor) {
                return i
            }
        }

        return -1
    }

    /**
     * Returns a [ScratchBuffers] with buffer size = blockFrames * [factor].
     * Cached per factor. If [factor] <= 1, returns this instance as-is.
     */
    fun oversample(factor: Int): ScratchBuffers {
        if (factor <= 1) {
            return this
        }

        val index = oversampleIndexOf(factor)

        if (index >= 0) {
            return oversamplePools[index]!!
        }

        return addOversample(factor)
    }

    /**
     * Builds the sub-pool for [factor] and stores it in a new slot: normally outside render, when the host warms it; a
     * factor the host did not warm builds here, on first use.
     */
    private fun addOversample(factor: Int): ScratchBuffers {
        if (oversampleCount == oversampleFactors.size) {
            oversampleFactors = oversampleFactors.copyOf(oversampleCount * 2)
            oversamplePools = oversamplePools.copyOf(oversampleCount * 2)
        }

        val pool = ScratchBuffers(blockFrames * factor)

        oversampleFactors[oversampleCount] = factor
        oversamplePools[oversampleCount] = pool
        oversampleCount++

        return pool
    }
}
