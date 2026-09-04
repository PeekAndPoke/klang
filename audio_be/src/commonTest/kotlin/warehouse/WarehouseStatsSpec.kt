/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The warehouse stats feed (`docs/tasks/future/warehouse-stats-feed.md`, maintainer 2026-09-04):
 * ONE entry point for what the backend holds, maintained INCREMENTALLY — every part bumps a
 * version when something changes, the snapshot is rebuilt only then, and an unchanged warehouse
 * hands back the same object. Sent with every `Diagnostics`.
 */
class WarehouseStatsSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    fun warehouse(): Pair<ResourceWarehouse, KlangCommLink> {
        val commLink = KlangCommLink(capacity = 1024)
        return ResourceWarehouse(sampleRate = sampleRate, blockFrames = blockFrames, samples = SampleStore(commLink.backend)) to commLink
    }

    "an unchanged warehouse hands back the SAME snapshot object — reading is a plain read" {
        val (w, _) = warehouse()
        val a = w.stats(droppedVoices = 0, deniedRents = 0)
        val b = w.stats(droppedVoices = 0, deniedRents = 0)
        b shouldBeSameInstanceAs a
        // Housekeeping with nothing dirty is not a change either.
        w.housekeep()
        w.stats(droppedVoices = 0, deniedRents = 0) shouldBeSameInstanceAs a
    }

    "every part's change re-snapshots, and the numbers are the part's own" {
        val (w, _) = warehouse()
        val s0 = w.stats(0, 0)

        // Rings: rent (allocates), return (dirty), housekeep (clean), rent again (hit).
        val ring = w.sized.rent(1).shouldNotBeNull()
        val s1 = w.stats(0, 0)
        s1 shouldNotBeSameInstanceAs s0
        s1.ringAllocations shouldBe 1
        w.sized.giveBack(ring)
        val s2 = w.stats(0, 0)
        s2.ringIdleCount shouldBe 1
        s2.ringDirtyCount shouldBe 1
        s2.ringIdleBytes shouldBe SizedBuffers.bytesOf(ring)
        while (!w.isClean) {
            w.housekeep()
        }
        val s3 = w.stats(0, 0)
        s3.ringDirtyCount shouldBe 0
        w.sized.rent(1)
        w.stats(0, 0).ringHits shouldBe 1

        // Reverbs and cylinders.
        val unit = w.reverbs.rent().shouldNotBeNull()
        w.reverbs.giveBack(unit)
        val s4 = w.stats(0, 0)
        s4.reverbAllocations shouldBe 1
        s4.reverbIdleCount shouldBe 1
        s4.reverbDirtyCount shouldBe 1
        val cylinder = w.cylinders.rent(id = 3, silentBlocksBeforeTailCheck = 10)
        w.cylinders.giveBack(cylinder)
        val s5 = w.stats(0, 0)
        s5.cylinderAllocations shouldBe 1
        s5.cylinderIdleCount shouldBe 1

        // Scratch: the pre-sized capacity, and a deeper nesting bumps the high water.
        s5.scratchCapacity shouldBe ResourceWarehouse.SCRATCH_DEPTH
        w.scratch.use { w.scratch.use { } }
        w.stats(0, 0).scratchHighWater shouldBe 2

        // Samples: bytes and count follow arrivals; a failed allocation is counted.
        w.samples.addSample(
            KlangCommLink.Cmd.Sample.Complete(
                req = SampleRequest(bank = null, sound = "kick", index = null, note = null),
                note = null, pitchHz = 440.0,
                sample = MonoSamplePcm(sampleRate = sampleRate, pcm = DoubleArray(1000), meta = SampleMetadata.default),
            )
        )
        val s6 = w.stats(0, 0)
        s6.sampleCount shouldBe 1
        s6.sampleBytes shouldBe 8000.0

        // The engine-side counters are folded in: a change re-snapshots, no change does not.
        val s7 = w.stats(droppedVoices = 2, deniedRents = 1)
        s7 shouldNotBeSameInstanceAs s6
        s7.droppedVoices shouldBe 2
        s7.deniedRents shouldBe 1
        w.stats(droppedVoices = 2, deniedRents = 1) shouldBeSameInstanceAs s7
    }

    "a re-uploaded sample replaces its bytes rather than adding to them" {
        val (w, _) = warehouse()
        val req = SampleRequest(bank = null, sound = "snare", index = null, note = null)
        fun upload(size: Int) = w.samples.addSample(
            KlangCommLink.Cmd.Sample.Complete(
                req = req, note = null, pitchHz = 440.0,
                sample = MonoSamplePcm(sampleRate = sampleRate, pcm = DoubleArray(size), meta = SampleMetadata.default),
            )
        )
        upload(1000)
        upload(4000)
        val s = w.stats(0, 0)
        s.sampleCount shouldBe 1
        s.sampleBytes shouldBe 32_000.0
    }

    "the dispatcher sends the snapshot with every Diagnostics, with the engines' dropped and denied summed in" {
        val clock = BackendClock(sampleRate)
        val commLink = KlangCommLink(capacity = 4096)
        var now = 0.0
        val context = AudioBackendContext.create(
            sampleRate = sampleRate, blockFrames = blockFrames, commLink = commLink.backend, clock = clock,
            performanceTimeMs = { now },
        )
        val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also { it.setBackendStartTime(0.0) }
        // A voice scheduled in the past is dropped at admission (block-framing B2).
        dispatcher.handle(
            KlangCommLink.Cmd.ScheduleVoice(
                playbackId = "song",
                voice = ScheduledVoice(
                    playbackId = "song", startTime = -1.0, gateEndTime = -0.5,
                    data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0), playbackStartTime = 0.0,
                ),
            )
        )
        val out = ShortArray(blockFrames * 2)
        // Diagnostics go out every ~20 ms of wall time; advance the fake clock past that.
        repeat(3) {
            now += 25.0
            dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
        }

        val diagnostics = generateSequence { commLink.frontend.feedback.receive() }
            .filterIsInstance<KlangCommLink.Feedback.Diagnostics>().toList()
        (diagnostics.size >= 2) shouldBe true
        val stats = diagnostics.last().warehouse
        stats.droppedVoices shouldBe 1
        stats.scratchCapacity shouldBe ResourceWarehouse.SCRATCH_DEPTH
        // Nothing changed between the last two emissions: the same snapshot object rode both.
        diagnostics[diagnostics.size - 1].warehouse shouldBeSameInstanceAs diagnostics[diagnostics.size - 2].warehouse
    }
})
