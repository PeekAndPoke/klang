/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Backend cache of uploaded PCM samples, keyed by [SampleRequest].
 *
 * Shared across all PlaybackEngines: `Cmd.Sample` uploads are SYSTEM-wide (they carry
 * `SYSTEM_PLAYBACK_ID`) and PCM is large, so there is exactly **one** store per backend. Extracted
 * from `VoiceScheduler` so per-playback schedulers can share it rather than each owning a private
 * cache. See KLANG-00176 (per playback engine) (D2·a).
 *
 * A sample arrives whole, as one `Cmd.Sample.Complete`, and the store keeps its PCM as it came: it allocates and copies
 * nothing on the audio thread. In the browser that PCM is a buffer the main thread made and transferred to the worklet
 * (`JsAudioBackend`, KLANG-00431 D5); until 2026-11-11 it came in chunks, and the store allocated the whole PCM at the
 * first one and copied every chunk into it (audit L9-09).
 */
class SampleStore(
    private val commLink: KlangCommLink.BackendEndpoint,
) {
    sealed interface SampleEntry {
        val req: SampleRequest

        data class Requested(
            override val req: SampleRequest,
        ) : SampleEntry

        data class NotFound(
            override val req: SampleRequest,
        ) : SampleEntry

        data class Complete(
            override val req: SampleRequest,
            val note: String?,
            val pitchHz: Double,
            val sample: MonoSamplePcm,
        ) : SampleEntry
    }

    // The samples uploaded to the backend.
    private val samples = mutableMapOf<SampleRequest, SampleEntry>()

    /** PCM bytes resident in the store (8 per sample; a Double, no Long). Maintained on arrival. */
    var residentBytes: Double = 0.0
        private set

    /** Samples with PCM in the store. */
    var residentCount: Int = 0
        private set

    /** Bumped on every change to the store's contents or counters — the stats snapshot rebuilds only then. */
    var version: Int = 0
        private set

    fun getComplete(req: SampleRequest): SampleEntry.Complete? = samples[req] as? SampleEntry.Complete

    fun contains(req: SampleRequest): Boolean = samples.containsKey(req)

    /** If we have never seen [req], mark it Requested and ask the frontend to load it. */
    fun requestIfMissing(req: SampleRequest, playbackId: String) {
        if (!samples.containsKey(req)) {
            samples[req] = SampleEntry.Requested(req)
            commLink.feedback.send(
                KlangCommLink.Feedback.RequestSample(playbackId = playbackId, req = req)
            )
        }
    }

    private fun notifyReceived(playbackId: String, req: SampleRequest) {
        commLink.feedback.send(
            KlangCommLink.Feedback.SampleReceived(playbackId = playbackId, req = req)
        )
    }

    fun addSample(msg: KlangCommLink.Cmd.Sample) {
        val req = msg.req

        return when (msg) {
            is KlangCommLink.Cmd.Sample.NotFound -> {
                version++
                samples[req] = SampleEntry.NotFound(req)
            }

            is KlangCommLink.Cmd.Sample.Complete -> {
                val previous = samples[req]
                if (previous is SampleEntry.Complete) {
                    residentBytes -= previous.sample.pcm.size * BYTES_PER_SAMPLE
                    residentCount--
                }
                samples[req] = SampleEntry.Complete(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = msg.sample,
                )
                residentBytes += msg.sample.pcm.size * BYTES_PER_SAMPLE
                residentCount++
                version++
                notifyReceived(msg.playbackId, req)
            }
        }
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 8.0
    }
}
