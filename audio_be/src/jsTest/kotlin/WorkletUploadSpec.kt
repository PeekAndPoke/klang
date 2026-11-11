/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.WorkletContract.sendSample
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageChannel
import org.w3c.dom.MessageEvent
import kotlin.coroutines.resume

/**
 * The browser's side of the worklet link that no JVM spec reaches:
 *
 * - a sample goes over as ONE message whose PCM buffer is transferred, not cloned (KLANG-00431 D5, audit L9-09): the
 *   copy sent is detached on this side, the frontend's own PCM (its cache entry) untouched, and the receiver decodes
 *   the whole sample;
 * - the main thread's figures join the worklet's diagnostics (`withMainThreadFigures`): the context's latency on top
 *   of the engine's own (D3), the main thread's refused sends on top of the worklet's (D10), its failed sample copies
 *   on top of the warehouse's (D5).
 */
class WorkletUploadSpec : StringSpec({

    val req = SampleRequest(bank = "b", sound = "s", index = null, note = "c3")

    fun complete(pcm: DoubleArray) = KlangCommLink.Cmd.Sample.Complete(
        req = req,
        note = "c3",
        pitchHz = 261.6,
        sample = MonoSamplePcm(
            sampleRate = 44100,
            pcm = pcm,
            meta = SampleMetadata(anchor = 0.01, loop = SampleMetadata.LoopRange(startSec = 0.0, endSec = 0.5), adsr = null),
        ),
    )

    "a sample crosses as one message whose PCM buffer is transferred; the frontend's PCM stays whole" {
        val channel = MessageChannel()
        val cached = DoubleArray(100_000) { it * 1e-5 }
        var sent: DoubleArray? = null

        val received = suspendCancellableCoroutine { cont ->
            channel.port2.onmessage = { event: MessageEvent -> cont.resume(WorkletContract.decodeCmd(event)) }
            channel.port2.start()

            val ok = channel.port1.sendSample(complete(cached)) { pcm -> pcm.copyOf().also { sent = it } }

            ok shouldBe true
        }

        // Transferred: the copy's buffer left this side (detached, its length 0); a clone would have left it whole
        (sent!!.asDynamic().length as Int) shouldBe 0
        // The cache's PCM is not what moved
        cached.size shouldBe 100_000
        cached[99_999] shouldBe (0.99999 plusOrMinus 1e-12)

        val sample = (received as KlangCommLink.Cmd.Sample.Complete).sample

        received.req shouldBe req
        sample.sampleRate shouldBe 44100
        sample.meta shouldBe complete(cached).sample.meta
        sample.pcm.size shouldBe 100_000
        sample.pcm.toList() shouldBe cached.toList()

        channel.port1.close()
        channel.port2.close()
    }

    "what is transferred is the main thread's copy: the frontend's cache keeps its PCM whole" {
        val channel = MessageChannel()
        val cached = DoubleArray(1000) { it * 1e-3 }

        val received = suspendCancellableCoroutine { cont ->
            channel.port2.onmessage = { event: MessageEvent -> cont.resume(WorkletContract.decodeCmd(event)) }
            channel.port2.start()
            channel.port1.sendSample(complete(cached)) shouldBe true
        }

        cached.size shouldBe 1000
        (received as KlangCommLink.Cmd.Sample.Complete).sample.pcm.toList() shouldBe cached.toList()

        channel.port1.close()
        channel.port2.close()
    }

    "a copy that cannot be allocated sends nothing and says so" {
        val channel = MessageChannel()

        channel.port1.sendSample(complete(DoubleArray(4))) { throw RuntimeException("no memory") } shouldBe false

        channel.port1.close()
        channel.port2.close()
    }

    "the main thread's figures join the worklet's report: latency on top of the engine's, refusals and failed copies on top" {
        val fromWorklet = KlangCommLink.Feedback.Diagnostics(
            playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
            sampleRate = 48000,
            renderHeadroom = 0.9,
            activeVoiceCount = 0,
            cylinders = emptyList(),
            backendNowMs = 1000.0,
            // The engine's own: the house lookahead and a master's, 5 + 10 ms
            outputLatencyMs = 15.0,
            warehouse = KlangCommLink.Feedback.Diagnostics.WarehouseStats.empty,
            refusedFeedbackSends = 2.0,
        )

        val enriched = withMainThreadFigures(
            diagnostics = fromWorklet,
            baseLatencyMs = 10.0,
            deviceLatencyMs = 30.0,
            refusedFeedbackSends = 3.0,
            uploadAllocationFailures = 1,
        )

        enriched.baseLatencyMs shouldBe 10.0
        enriched.outputDeviceLatencyMs shouldBe 30.0
        enriched.outputLatencyMs shouldBe (55.0 plusOrMinus 1e-12)
        enriched.refusedFeedbackSends shouldBe 5.0
        enriched.warehouse.sampleAllocationFailures shouldBe 1

        withMainThreadFigures(
            diagnostics = fromWorklet,
            baseLatencyMs = 0.0,
            deviceLatencyMs = 0.0,
            refusedFeedbackSends = 0.0,
            uploadAllocationFailures = 0,
        ).warehouse shouldBe fromWorklet.warehouse
    }
})
