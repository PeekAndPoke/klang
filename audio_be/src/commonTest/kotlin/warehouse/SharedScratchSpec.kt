/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.crush
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.tremolo
import kotlin.math.abs

/**
 * Resource warehouse step 2a, through the real engine: scratch is ONE shared pool, pre-sized, and
 * render never allocates from it. The isolation spec (`ResourceWarehouseSpec`) proves the pool's
 * rules; this one proves the engine actually uses that pool and never exhausts it.
 */
class SharedScratchSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    fun context(): Pair<AudioBackendContext, BackendClock> {
        val clock = BackendClock(sampleRate)
        return AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
        ) to clock
    }

    /** A synthetic chain far deeper than any shipped instrument: 24 nested effects on a sine. */
    fun deepChain(): IgnitorDsl {
        var dsl: IgnitorDsl = IgnitorDsl.Sine()
        repeat(6) {
            dsl = dsl.crush(3.0).coarse(4.0).distort(2.0, oversample = 4).tremolo(rate = 5.0, depth = 0.5)
        }
        return dsl
    }

    fun renderPeak(context: AudioBackendContext, clock: BackendClock, pid: String, sound: String, blocks: Int): Double {
        val engine = PlaybackEngine.create(context)
        engine.scheduler.registerIgnitor(sound, deepChain())
        engine.scheduler.scheduleVoice(
            ScheduledVoice(
                playbackId = pid,
                startTime = 0.0,
                gateEndTime = 1.0,
                data = VoiceData.empty.copy(sound = sound, freqHz = 220.0),
                playbackStartTime = 0.0,
            )
        )
        val mix = StereoBuffer(blockFrames)
        var peak = 0.0
        repeat(blocks) {
            mix.clear()
            engine.renderInto(mix, clock.cursorFrame)
            for (i in 0 until blockFrames) peak = maxOf(peak, abs(mix.left[i]))
            clock.cursorFrame += blockFrames
        }
        return peak
    }

    "every playback on a backend renders through the SAME scratch pool" {
        val (context, _) = context()
        val a = PlaybackEngine.create(context)
        val b = PlaybackEngine.create(context)

        // The pool each scheduler actually RENDERS with — not the context's field, which would be
        // the same object whatever the scheduler did privately. (The first cut of this row asked the
        // context, and a scheduler building its own pool passed it.)
        a.scheduler.scratchBuffersForTest shouldBeSameInstanceAs context.warehouse.scratch
        b.scheduler.scratchBuffersForTest shouldBeSameInstanceAs context.warehouse.scratch
    }

    "a 24-deep effect chain renders without a single in-render scratch allocation" {
        val (context, clock) = context()
        val scratch = context.warehouse.scratch

        val peak = renderPeak(context, clock, pid = "deep", sound = "deepchain", blocks = 40)

        // Positive control: the chain actually ran — a silent render exercises no scratch at all.
        peak shouldBeGreaterThan 1e-6
        // ...and it ran through THIS pool, nesting at least as deep as the chain is long. A private
        // per-scheduler pool would leave the shared one's high-water mark at zero.
        (scratch.highWater >= 20) shouldBe true
        // The claim. Before step 2a a fresh pool held 4 buffers and grew inside process() as the
        // chain nested.
        scratch.lateAllocations shouldBe 0
    }

    "the oversample sub-pools exist BEFORE the first render — their first use allocates nothing" {
        val (context, _) = context()
        val scratch = context.warehouse.scratch

        // lateAllocations cannot see this: a sub-pool's first-use cost is its CREATION (four
        // buffers at once inside process()), not growth. Presence before any render is the proof.
        for (factor in ResourceWarehouse.WARM_OVERSAMPLE_FACTORS) {
            scratch.hasOversample(factor) shouldBe true
        }
    }

    "the stack discipline holds across a full render — no unbalanced release anywhere in the engine" {
        val (context, clock) = context()

        renderPeak(context, clock, pid = "deep", sound = "deepchain", blocks = 40)

        context.warehouse.scratch.unbalancedReleases shouldBe 0
    }

    "a second playback after the first pays nothing — the pool is kept, not disposed" {
        val (context, clock) = context()
        renderPeak(context, clock, pid = "first", sound = "deepchain", blocks = 20)
        val capacityAfterFirst = context.warehouse.scratch.capacity

        renderPeak(context, clock, pid = "second", sound = "deepchain", blocks = 20)

        context.warehouse.scratch.capacity shouldBe capacityAfterFirst
        context.warehouse.scratch.lateAllocations shouldBe 0
    }
})
