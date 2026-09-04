/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Guards the sample-cache logic extracted from VoiceScheduler into [SampleStore] (D2·a):
 * chunk reassembly, completion feedback, request de-duplication, and not-found handling.
 */
class SampleStoreSpec : StringSpec({

    fun req(sound: String = "bd") = SampleRequest(bank = null, sound = sound, index = null, note = null)

    fun newStore(): Pair<SampleStore, KlangCommLink> {
        val commLink = KlangCommLink(capacity = 1024)
        return SampleStore(commLink.backend) to commLink
    }

    fun KlangCommLink.drainFeedback(): List<KlangCommLink.Feedback> {
        val out = mutableListOf<KlangCommLink.Feedback>()
        while (true) {
            out.add(frontend.feedback.receive() ?: break)
        }
        return out
    }

    "Complete sample is retrievable and emits one SampleReceived" {
        val (store, commLink) = newStore()
        val r = req()
        store.getComplete(r) shouldBe null
        store.contains(r) shouldBe false

        store.addSample(
            KlangCommLink.Cmd.Sample.Complete(
                req = r,
                note = "c4",
                pitchHz = 261.6,
                sample = MonoSamplePcm(sampleRate = 44100, pcm = doubleArrayOf(0.1, 0.2)),
            )
        )

        val entry = store.getComplete(r).shouldNotBeNull()
        entry.sample.pcm.toList() shouldBe listOf(0.1, 0.2)
        store.contains(r) shouldBe true
        commLink.drainFeedback().filterIsInstance<KlangCommLink.Feedback.SampleReceived>() shouldHaveSize 1
    }

    "requestIfMissing emits RequestSample once and marks the request seen" {
        val (store, commLink) = newStore()
        val r = req("hh")

        store.requestIfMissing(r, "pid-1")
        store.contains(r) shouldBe true
        store.getComplete(r) shouldBe null   // Requested, not Complete

        store.requestIfMissing(r, "pid-1")   // already seen → no second request

        commLink.drainFeedback().filterIsInstance<KlangCommLink.Feedback.RequestSample>() shouldHaveSize 1
    }

    "chunked sample reassembles across chunks and completes on the last" {
        val (store, commLink) = newStore()
        val r = req("loop")
        val meta = SampleMetadata.default

        store.addSample(
            KlangCommLink.Cmd.Sample.Chunk(
                req = r, note = null, pitchHz = 440.0, sampleRate = 44100, meta = meta,
                totalSize = 4, isLastChunk = false, chunkOffset = 0, data = doubleArrayOf(1.0, 2.0),
            )
        )
        store.getComplete(r) shouldBe null   // still partial

        store.addSample(
            KlangCommLink.Cmd.Sample.Chunk(
                req = r, note = null, pitchHz = 440.0, sampleRate = 44100, meta = meta,
                totalSize = 4, isLastChunk = true, chunkOffset = 2, data = doubleArrayOf(3.0, 4.0),
            )
        )

        val entry = store.getComplete(r).shouldNotBeNull()
        entry.sample.pcm.toList() shouldBe listOf(1.0, 2.0, 3.0, 4.0)
        commLink.drainFeedback().filterIsInstance<KlangCommLink.Feedback.SampleReceived>() shouldHaveSize 1
    }

    "NotFound marks the request seen but never completes" {
        val (store, _) = newStore()
        val r = req("missing")

        store.addSample(KlangCommLink.Cmd.Sample.NotFound(req = r))

        store.contains(r) shouldBe true
        store.getComplete(r) shouldBe null
    }


    // ── Resource warehouse 2g: the PCM array is the one MB-scale allocation the store makes ──────

    "a chunked sample whose PCM cannot be allocated is silent, counted, and its later chunks are dropped — nothing throws" {
        // Chunks arrive on the audio thread through the worklet's message port; before 2g a failed
        // DoubleArray(totalSize) was an uncaught OutOfMemoryError there, and the worklet stopped
        // for good. Now it is a sample that never completes, like a NotFound one.
        val commLink = KlangCommLink(capacity = 1024)
        var asked = 0
        val store = SampleStore(commLink.backend, allocatePcm = { asked++; null })
        val r = req("huge")
        val meta = SampleMetadata.default

        val first = KlangCommLink.Cmd.Sample.Chunk(
            req = r, note = null, pitchHz = 440.0, sampleRate = 44100, meta = meta,
            totalSize = 4, isLastChunk = false, chunkOffset = 0, data = doubleArrayOf(1.0, 2.0),
        )
        val last = first.copy(isLastChunk = true, chunkOffset = 2, data = doubleArrayOf(3.0, 4.0))

        store.addSample(first)
        store.addSample(last)

        store.getComplete(r) shouldBe null
        store.contains(r) shouldBe true // known, so it is not re-requested every note
        store.allocationFailures shouldBe 1
        asked shouldBe 1 // the second chunk did not restart the allocation
        // The frontend's preloader awaits the same ack a success sends, with no timeout: without
        // it a failed upload left the playback waiting forever (review round 3).
        commLink.drainFeedback().filterIsInstance<KlangCommLink.Feedback.SampleReceived>().map { it.req } shouldBe listOf(r)
    }

    "the default PCM allocator turns a hopeless size into null rather than a throw" {
        SampleStore.allocatePcmOrNull(Int.MAX_VALUE) shouldBe null
        SampleStore.allocatePcmOrNull(4).shouldNotBeNull().size shouldBe 4
    }
})
