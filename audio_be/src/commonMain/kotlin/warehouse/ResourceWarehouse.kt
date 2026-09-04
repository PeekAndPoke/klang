/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * Where the backend's expensive buffers come from (`docs/plans/resource-warehouse.md`).
 *
 * **One per backend, owned by `AudioBackendContext`** — a singleton in effect, so scratch depth and
 * shelved rings are shared across playbacks (and kept from the warmup engine rather than disposed
 * with it) — but deliberately NOT a Kotlin `object`: a real singleton cannot be replaced, so every
 * spec would share one warehouse, the offline renderer would share it with a live backend in the
 * same JVM, and state would leak between tests.
 *
 * Four shelves, two kinds of rule:
 *
 * - [sized] (delay rings, class-sized, byte-budgeted), [reverbs] (Freeverb networks, one size,
 *   count-bounded) and [cylinders] (whole orbits, count-bounded) — large, returnable state. Rent
 *   may return `null` for rings and networks: those are the out-of-memory catch sites, and the
 *   compiler makes every consumer decide. A cylinder without units is a few KB and is not caught.
 *   Idle memory is therefore `SHELF_BUDGET_BYTES` (rings) + 32 networks (~6.5 MB at 44.1 kHz) +
 *   32 cylinders (KBs); the byte budget covers the part that has a ladder.
 * - [scratch] — per-node, per-block, KB-scale, held for microseconds. NOT nullable: a 1 KB failure
 *   cannot be handled meaningfully, and `acquire()` runs per node per block, so nullability there
 *   would spread branches whose only content is "render silence" through every composing ignitor.
 *   Its allocation is taken out of the hot path instead — sized at build via
 *   [ScratchBuffers.ensureCapacity], never grown inside `process()`.
 */
class ResourceWarehouse(
    sampleRate: Int,
    blockFrames: Int,
    budgetBytes: Int = SHELF_BUDGET_BYTES,
    allocate: (frames: Int) -> StereoBuffer? = SizedBuffers::allocateOrNull,
    allocateReverb: (sampleRate: Int) -> Reverb? = ReverbUnits::allocateOrNull,
) {
    /** Delay rings. Class 0 is [MIN_RING_SECONDS] at [sampleRate]. */
    val sized: SizedBuffers = SizedBuffers.forRings(sampleRate, budgetBytes, allocate)

    /** Reverb units — one size, lazy on the first `room`, shelved by return (step 2d). */
    val reverbs: ReverbUnits = ReverbUnits(sampleRate, allocate = allocateReverb)

    /** Whole cylinders — built by the warmup, returned by engine disposal, taken by the next engine. */
    val cylinders: CylinderUnits = CylinderUnits(blockFrames = blockFrames, sampleRate = sampleRate, rings = sized, reverbs = reverbs)

    /**
     * One block's worth of deferred clearing: up to one class-0 ring's frames and one reverb network
     * (review round 3). The backend calls this once per rendered block, after the mix, so a return
     * costs nothing where it happens and the shelf is zeroed a few dozen blocks later. Cylinders
     * need no housekeeping: a retired one holds no units and its own state is small.
     */
    fun housekeep() {
        sized.housekeep()
        reverbs.housekeep()
    }

    /** True when every idle ring and network is zeroed — what the warmup waits for before `BackendReady`. */
    val isClean: Boolean get() = sized.isClean && reverbs.isClean

    /**
     * The one shared scratch pool. Engines render sequentially within a block, so one is enough.
     *
     * Sized HERE, once, to [SCRATCH_DEPTH] — not per voice at build. The plan said "the factory
     * knows its graph's depth"; it does not, cheaply: the DSL tree has no walker, and a per-voice
     * count would be a 78-arm `when` that every new node type must maintain, for a 1 KB buffer. The
     * depth bound is a property of the corpus, not of one voice, and this pool is shared and never
     * shrinks, so one generous pre-size covers every graph a song has — the deepest shipped chain
     * nests well under half of it. The proof is not the number but the counter:
     * [ScratchBuffers.lateAllocations] must read zero after any render, and a spec says so.
     * The oversample sub-pools are warmed for the same reason — their first use would otherwise
     * allocate inside `process()`.
     */
    val scratch: ScratchBuffers = ScratchBuffers(blockFrames).apply {
        ensureCapacity(SCRATCH_DEPTH)
        for (factor in WARM_OVERSAMPLE_FACTORS) {
            // Creation is the whole cost: a sub-pool's constructor already holds 4 work buffers,
            // and its real depth is 1 (see WARM_OVERSAMPLE_FACTORS). Nothing more to pre-size.
            oversample(factor)
        }
    }

    companion object {
        /**
         * The smallest ring class, in seconds. Every `delaytime` in the shipped corpus is 1/8–1/4 of
         * a cycle, i.e. at most 0.5 s at 60 BPM, so the smallest class covers all of it.
         */
        const val MIN_RING_SECONDS: Double = 0.5

        /**
         * Frames a delay ring holds BEYOND its delay time: `DelayLine` reads one sample past the tap
         * for interpolation and clamps the tap to `size - 2`, so a ring must be a little longer than
         * the time it serves. Class 0 includes it (see [SizedBuffers.forRings]), so a delay of exactly
         * [MIN_RING_SECONDS] fits the smallest class.
         */
        const val RING_MARGIN_FRAMES: Int = 64

        /**
         * Idle-shelf bound: roughly four songs of right-sized rings, one order below the old
         * single-song spike. A named constant, not a mechanism — it bounds idle memory only, so being
         * wrong is never audible. Live browser only; the offline renderer renders once and needs no
         * shelf. `navigator.deviceMemory` is Chromium-only, coarse, and unreadable inside an
         * AudioWorklet; revisit only if a device proves the need.
         */
        const val SHELF_BUDGET_BYTES: Int = 32 * 1024 * 1024

        /**
         * Scratch buffers pre-allocated per pool: 64 × 1 KB. Measured: a synthetic 24-effect chain
         * (deeper than any shipped instrument) reaches a high-water mark of 30. Real songs are
         * shallower; `SharedScratchSpec` pins zero in-render allocations.
         */
        const val SCRATCH_DEPTH: Int = 64

        /**
         * The WARMED oversample factors — not the set the DSL accepts. `IgnitorDsl` takes any Int
         * and `Oversampler.factorToStages` floors it to a power of two, so 32× is legal; a factor
         * outside this list builds its sub-pool on first use, inside render, and `lateAllocations`
         * cannot see that (it counts growth, not creation). 16× is the largest anyone has authored;
         * beyond it the trade is the user's.
         *
         * A sub-pool is NOT pre-sized beyond its construction default (4 work buffers): the real
         * depth is ONE — `Oversampler.process` opens a single `use`, and `ShapeIgnitor` renders its
         * upstream BEFORE opening it, so oversampled work never nests even when shaped nodes stack —
         * and the DoubleArray half stays EMPTY, since nothing on an oversampled path calls
         * `useDouble` (only `ModApplyingIgnitor`, on the main pool). Review round 1 had warmed both
         * halves to depth 8, ~460 KB that no render can reach, paid eagerly in the worklet's first
         * `process()` (review round 2). The proof is the sub-pool's own counters after a render.
         */
        val WARM_OVERSAMPLE_FACTORS: List<Int> = listOf(2, 4, 8, 16)
    }
}
