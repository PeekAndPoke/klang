/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Where the backend's expensive buffers come from (KLANG-00230 (resource warehouse)).
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
 *   count-bounded) and [cylinders] (whole cylinders, count-bounded), large, returnable state. Rent
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
    private val sampleRate: Int,
    blockFrames: Int,
    /**
     * The backend's sample PCM store — in the warehouse so that every MB-scale thing the backend
     * holds reports through ONE entry point ([stats]); its behaviour is unchanged. Built outside
     * because it needs the comm link (it asks the frontend for samples); a spec without one gets
     * a store on a private link.
     */
    val samples: SampleStore = SampleStore(KlangCommLink(capacity = 16).backend),
    budgetBytes: Int = SHELF_BUDGET_BYTES,
    allocate: (frames: Int) -> StereoBuffer? = SizedBuffers::allocateOrNull,
    allocateReverb: (sampleRate: Int, factor: Int) -> Reverb? = ReverbUnits::allocateOrNull,
) {
    /** Delay rings. Class 0 is [MIN_RING_SECONDS] at [sampleRate]. */
    val sized: SizedBuffers = SizedBuffers.forRings(sampleRate = sampleRate, budgetBytes = budgetBytes, allocate = allocate)

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
     * Puts [count] delay rings that hold [seconds] (the class a delay of that time rents, [RING_MARGIN_FRAMES]
     * included) on the ring shelf: rented, allocated where the shelf has none, and given back, dirty, so the per-block
     * [housekeep] zeroes them like any return. The warmup's stock of the longer classes (`WarmupRunner`, maintainer
     * 2026-11-11, KLANG-00428): the one place the shelf is filled by prediction (see [SizedBuffers] rule 2), so a
     * song's first 1 s or 2 s delay does not allocate its ring in its onset block. Inside the idle budget like every
     * return: over it, the largest idle rings are dropped first. Not for render code: it allocates.
     */
    fun stockDelayRings(seconds: Double, count: Int) {
        val frames = (seconds * sampleRate).toInt() + RING_MARGIN_FRAMES
        val rented = arrayOfNulls<StereoBuffer>(count)

        // All rented first, then all given back: a ring given back at once would be the next rent's hit
        for (i in 0 until count) {
            rented[i] = sized.rent(minFrames = frames)
        }

        for (i in 0 until count) {
            rented[i]?.let { sized.giveBack(it) }
        }
    }

    // ── Stats: one entry point, maintained by change, read for free ──────────────────────────────

    private var statsVersion = -1
    private var statsDroppedVoices = -1
    private var statsDeniedRents = -1
    private var stats: KlangCommLink.Feedback.Diagnostics.WarehouseStats? = null

    /**
     * The warehouse's stats for the diagnostics feed (maintainer, 2026-09-04: "the stats need to
     * be calculated for each part as soon as something has changed, so that getting the stats does
     * not need to do any calculations again and again"). Every part bumps a `version` when it
     * changes; this rebuilds the snapshot only when the combined version moved, otherwise it hands
     * back the same object. [droppedVoices] and [deniedRents] live in the engines, not here, so the
     * caller sums them and passes them in; each is compared on its own, not folded into the version: [deniedRents] falls
     * when a playback stops or its engine goes, so in a sum a fall could cancel the parts' rise and serve a stale
     * snapshot (audit L9-11; [droppedVoices] no longer falls since KLANG-00431 D6, the dispatcher keeps a stopped
     * playback's count). The parts' versions only ever rise, so their sum moves with any of them.
     */
    fun stats(droppedVoices: Int, deniedRents: Int): KlangCommLink.Feedback.Diagnostics.WarehouseStats {
        val version = sized.version + reverbs.version + cylinders.version + scratch.version + samples.version
        val cached = stats
        val unchanged = version == statsVersion && droppedVoices == statsDroppedVoices && deniedRents == statsDeniedRents

        if (cached != null && unchanged) {
            return cached
        }

        val fresh = KlangCommLink.Feedback.Diagnostics.WarehouseStats(
            ringIdleBytes = sized.shelfBytes,
            ringIdleCount = sized.shelfCount,
            ringDirtyCount = sized.dirtyCount,
            ringAllocations = sized.allocations,
            ringHits = sized.hits,
            ringFailures = sized.failures,
            ringDropped = sized.dropped,
            ringSyncCleans = sized.syncCleans,
            // Every reverb shelf, the scaled ones inside regions included, a unit counted once whatever its factor.
            reverbIdleCount = reverbs.totalIdleCount,
            reverbDirtyCount = reverbs.totalDirtyCount,
            reverbAllocations = reverbs.totalAllocations,
            reverbHits = reverbs.totalHits,
            reverbFailures = reverbs.totalFailures,
            reverbDropped = reverbs.totalDropped,
            cylinderIdleCount = cylinders.idleCount,
            cylinderAllocations = cylinders.allocations,
            cylinderHits = cylinders.hits,
            cylinderDropped = cylinders.dropped,
            scratchCapacity = scratch.capacity,
            scratchHighWater = scratch.highWater,
            scratchLateAllocations = scratch.lateAllocations,
            scratchUnbalancedReleases = scratch.unbalancedReleases,
            sampleBytes = samples.residentBytes,
            sampleCount = samples.residentCount,
            // The store allocates no PCM since KLANG-00431 D5: the browser's main thread makes the copy it transfers
            // and adds the copies it could not make (`JsAudioBackend`); the JVM hands the decoded PCM over in process.
            sampleAllocationFailures = 0,
            droppedVoices = droppedVoices,
            deniedRents = deniedRents,
        )
        stats = fresh
        statsVersion = version
        statsDroppedVoices = droppedVoices
        statsDeniedRents = deniedRents

        return fresh
    }

    /**
     * The one shared scratch pool. Engines render sequentially within a block, so one is enough.
     *
     * Sized HERE, once, to [SCRATCH_DEPTH] — not per voice at build. The plan said "the factory
     * knows its graph's depth"; it does not, cheaply: a per-voice depth count would be a recursion
     * over `IgnitorDsl.childNodes()` at every build, for a 1 KB buffer, and would still not know
     * how deep the RUNTIME nests scratch (an affine's three coefficients, an oversampler). The
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
            // Creation only: a sub-pool's constructor already holds 4 work buffers. A region nests deeper and grows
            // its sub-pool once per engine, at its first note (see WARM_OVERSAMPLE_FACTORS).
            oversample(factor)
        }
    }

    companion object {
        /**
         * The smallest ring class, in seconds. Every `delay(time)` in the shipped corpus is 1/8–1/4 of
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
         * The WARMED oversample factors, not the set the DSL accepts. A region's factor
         * knob takes any number (`Oversampler.factorOf` truncates it to a whole factor),
         * `Oversampler.factorToStages` floors that to a power of two, and a region stops at 16×
         * (`REGION_MAX_STAGES`), so no door reaches 32× any more; a factor outside this list builds its
         * sub-pool on first use, inside render, and `lateAllocations` cannot see that (it counts growth,
         * not creation).
         *
         * A sub-pool is NOT pre-sized beyond its construction default (4 work buffers), and the DoubleArray half stays
         * EMPTY, since nothing on an oversampled path calls `useDouble` (only `ModApplyingIgnitor`, on the main pool).
         * Review round 1 had warmed both halves to depth 8, ~460 KB that no render can reach, paid eagerly in the
         * worklet's first `process()` (review round 2). The proof is the sub-pool's own counters after a render.
         *
         * An oversampling REGION (KLANG-00214 (oversampling regions)) nests deeper than one lease: its body renders
         * with the sub-pool as its whole scratch, so the region's two leases plus every stage of its body nest there
         * (about 7 for a three-stage preamp), and a stage or a nested region inside it opens a sub-pool of the sub-pool,
         * which is not warmed. Both grow once per engine, at the first note that needs them, inside render; the warmup
         * vocabulary's region pre-grows part of it.
         */
        val WARM_OVERSAMPLE_FACTORS: List<Int> = listOf(2, 4, 8, 16)
    }
}
