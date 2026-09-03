/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.StereoBuffer
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
 * Two shelves with different rules, on purpose:
 *
 * - [sized] — large, per-orbit, returnable state (delay rings, reverb units). Rent may return
 *   `null`: this is the one out-of-memory catch site, and the compiler makes every consumer decide.
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
) {
    /** Delay rings (and reverb units, once they rent). Class 0 is [MIN_RING_SECONDS] at [sampleRate]. */
    val sized: SizedBuffers = SizedBuffers.forRings(sampleRate, budgetBytes, allocate)

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
            oversample(factor).ensureCapacity(OVERSAMPLE_SCRATCH_DEPTH)
        }
    }

    companion object {
        /**
         * The smallest ring class, in seconds. Every `delaytime` in the shipped corpus is 1/8–1/4 of
         * a cycle, i.e. at most 0.5 s at 60 BPM, so the smallest class covers all of it.
         */
        const val MIN_RING_SECONDS: Double = 0.5

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
         * Nesting depth pre-sized per OVERSAMPLE sub-pool. Oversampled scratch is not the graph's
         * depth: `Oversampler.process` takes one work buffer per oversampled node, and a chain
         * rarely stacks more than a couple. The main pool's 64 here would cost 1 MB for the 8× pool
         * alone — and every `AudioBackendContext` pays this eagerly, in the worklet inside the first
         * `process()` (review round 1).
         */
        const val OVERSAMPLE_SCRATCH_DEPTH: Int = 8

        /**
         * The WARMED oversample factors — not the set the DSL accepts. `IgnitorDsl` takes any Int
         * and `Oversampler.factorToStages` floors it to a power of two, so 32× is legal; a factor
         * outside this list builds its sub-pool on first use, inside render, and `lateAllocations`
         * cannot see that (it counts growth, not creation). 16× is the largest anyone has authored;
         * beyond it the trade is the user's.
         */
        val WARM_OVERSAMPLE_FACTORS: List<Int> = listOf(2, 4, 8, 16)
    }
}
