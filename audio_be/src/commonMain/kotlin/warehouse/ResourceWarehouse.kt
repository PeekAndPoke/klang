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
    val sized: SizedBuffers = SizedBuffers(
        baseFrames = (sampleRate * MIN_RING_SECONDS).toInt(),
        budgetBytes = budgetBytes,
        allocate = allocate,
    )

    /** The one shared scratch pool. Engines render sequentially within a block, so one is enough. */
    val scratch: ScratchBuffers = ScratchBuffers(blockFrames)

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
    }
}
