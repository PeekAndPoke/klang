/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
 * cache. See `docs/tasks/per-playback-engine.md` (D2·a).
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

        data class Partial(
            override val req: SampleRequest,
            val note: String?,
            val pitchHz: Double,
            val sample: MonoSamplePcm,
        ) : SampleEntry
    }

    // The samples uploaded to the backend.
    private val samples = mutableMapOf<SampleRequest, SampleEntry>()

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
                samples[req] = SampleEntry.NotFound(req)
            }

            is KlangCommLink.Cmd.Sample.Complete -> {
                samples[req] = SampleEntry.Complete(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = msg.sample,
                )
                notifyReceived(msg.playbackId, req)
            }

            is KlangCommLink.Cmd.Sample.Chunk -> {
                val existing = samples[req]
                if (existing is SampleEntry.Complete) return

                val entry = (existing as? SampleEntry.Partial) ?: SampleEntry.Partial(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = MonoSamplePcm(sampleRate = msg.sampleRate, pcm = DoubleArray(msg.totalSize)),
                )

                msg.data.copyInto(destination = entry.sample.pcm, destinationOffset = msg.chunkOffset)

                samples[req] = if (!msg.isLastChunk) {
                    entry
                } else {
                    SampleEntry.Complete(
                        req = req,
                        note = entry.note,
                        pitchHz = entry.pitchHz,
                        sample = entry.sample,
                    ).also {
                        notifyReceived(msg.playbackId, req)
                    }
                }
            }
        }
    }
}
