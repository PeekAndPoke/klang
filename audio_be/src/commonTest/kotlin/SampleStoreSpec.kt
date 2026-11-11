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
 * a whole sample kept as it came, completion feedback, request de-duplication, and not-found handling.
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

    "a Complete sample's PCM is kept as it came: the audio thread allocates and copies nothing (KLANG-00431 D5)" {
        val (store, _) = newStore()
        val r = req("loop")
        val pcm = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val meta = SampleMetadata(anchor = 0.01, loop = SampleMetadata.LoopRange(startSec = 0.0, endSec = 0.5), adsr = null)

        store.addSample(
            KlangCommLink.Cmd.Sample.Complete(
                req = r, note = null, pitchHz = 440.0, sample = MonoSamplePcm(sampleRate = 44100, pcm = pcm, meta = meta),
            )
        )

        val entry = store.getComplete(r).shouldNotBeNull()
        (entry.sample.pcm === pcm) shouldBe true
        entry.sample.meta shouldBe meta
        store.residentBytes shouldBe 32.0
        store.residentCount shouldBe 1
    }

    "NotFound marks the request seen but never completes" {
        val (store, _) = newStore()
        val r = req("missing")

        store.addSample(KlangCommLink.Cmd.Sample.NotFound(req = r))

        store.contains(r) shouldBe true
        store.getComplete(r) shouldBe null
    }
})
