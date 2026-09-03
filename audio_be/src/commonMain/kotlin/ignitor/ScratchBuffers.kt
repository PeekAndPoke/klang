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
class ScratchBuffers(private val blockFrames: Int, initialCapacity: Int = 4) {

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

    /** Pre-grows BOTH pools to [depth] buffers, OUTSIDE render, so neither acquire allocates inside it. */
    fun ensureCapacity(depth: Int) {
        while (pool.size < depth) {
            pool.add(AudioBuffer(blockFrames))
        }
        while (doublePool.size < depth) {
            doublePool.add(DoubleArray(blockFrames))
        }
    }

    /** Buffers in the AudioBuffer pool right now, in use or not. */
    val capacity: Int get() = pool.size

    /** Buffers in the DoubleArray pool right now, in use or not. */
    val doubleCapacity: Int get() = doublePool.size

    /**
     * The deepest simultaneous nesting this pool has ever served — i.e. the deepest ignitor graph
     * that has rendered through it. Read it to know how much of [ensureCapacity]'s pre-size a real
     * song actually uses.
     */
    var highWater: Int = 0
        private set

    /** Whether the sub-pool for [factor] already exists — i.e. its first use will NOT allocate. */
    fun hasOversample(factor: Int): Boolean = factor <= 1 || factor in oversampleCache

    @PublishedApi
    internal fun acquire(): AudioBuffer {
        if (nextFree >= pool.size) {
            pool.add(AudioBuffer(blockFrames))
            lateAllocations++
        }
        val buf = pool[nextFree++]
        if (nextFree > highWater) {
            highWater = nextFree
        }
        return buf
    }

    @PublishedApi
    internal fun release() {
        if (nextFree > 0) {
            nextFree--
        } else {
            unbalancedReleases++
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

    fun reset() {
        nextFree = 0
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
        }
        val buf = doublePool[doubleNextFree++]
        if (doubleNextFree > highWater) {
            highWater = doubleNextFree
        }
        return buf
    }

    @PublishedApi
    internal fun releaseDouble() {
        if (doubleNextFree > 0) {
            doubleNextFree--
        } else {
            unbalancedReleases++
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

    private val oversampleCache = mutableMapOf<Int, ScratchBuffers>()

    /**
     * Returns a [ScratchBuffers] with buffer size = blockFrames * [factor].
     * Cached per factor. If [factor] <= 1, returns this instance as-is.
     */
    fun oversample(factor: Int): ScratchBuffers {
        if (factor <= 1) return this
        return oversampleCache.getOrPut(factor) { ScratchBuffers(blockFrames * factor) }
    }
}
