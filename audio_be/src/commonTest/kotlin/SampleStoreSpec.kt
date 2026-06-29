/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
})
