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
 * cache. See `docs/tasks/per-playback-engine.md` (D2·a).
 */
class SampleStore(
    private val commLink: KlangCommLink.BackendEndpoint,
    /**
     * Where a chunked sample's PCM array comes from — the one MB-scale allocation the store makes,
     * on the audio thread (chunks arrive through the worklet's message port). Caught here as
     * `null` rather than thrown (resource warehouse, 2g): an uncaught allocation failure inside
     * the worklet stops it permanently, while a sample that failed to arrive is simply silent, the
     * way a NotFound one is. Injectable so a spec can fail it without exhausting the JVM.
     */
    private val allocatePcm: (frames: Int) -> DoubleArray? = ::allocatePcmOrNull,
) {
    sealed interface SampleEntry {
        val req: SampleRequest

        data class Requested(
            override val req: SampleRequest,
        ) : SampleEntry

        data class NotFound(
            override val req: SampleRequest,
        ) : SampleEntry

        /**
         * The PCM array for this sample could not be allocated. Behaves like [NotFound] for every
         * consumer (no `Complete`, so the voice is silent); a distinct state so the diagnostics can
         * say "out of memory" rather than "no such sample", and so the remaining chunks of that
         * upload are dropped instead of restarting the allocation each. The frontend is told with
         * the same `SampleReceived` a success sends — its preloader awaits that ack with no timeout,
         * and before review round 3 a failed upload left it waiting forever. Permanent for the
         * backend's life, on purpose: making `contains` false would re-request and re-upload
         * megabytes on every note under the very memory pressure that failed the first one.
         */
        data class AllocationFailed(
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

    /** Chunked uploads whose PCM array could not be allocated. Monotone; for the diagnostics feed. */
    var allocationFailures: Int = 0
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
                if (existing is SampleEntry.Complete || existing is SampleEntry.AllocationFailed) return

                val entry = (existing as? SampleEntry.Partial) ?: run {
                    val pcm = allocatePcm(msg.totalSize)

                    if (pcm == null) {
                        allocationFailures++
                        samples[req] = SampleEntry.AllocationFailed(req)
                        // The upload is over as far as the frontend is concerned: release its wait.
                        notifyReceived(msg.playbackId, req)

                        return
                    }

                    SampleEntry.Partial(
                        req = req,
                        note = msg.note,
                        pitchHz = msg.pitchHz,
                        // `meta` MUST come across with the PCM. Every chunk carries it (toChunks puts the
                        // sample's meta on each one), and this constructor used to leave it defaulted —
                        // so every sample that travelled chunked arrived with loop = null, adsr = null,
                        // anchor = 0. In the browser that is EVERY sample: JsAudioBackend chunks all
                        // Complete messages before the worklet boundary, regardless of size. No soundfont
                        // had ever looped there. The JVM path passes Complete in-process and never lost it,
                        // which is why the offline renderer disagreed with the ear for so long.
                        sample = MonoSamplePcm(sampleRate = msg.sampleRate, pcm = pcm, meta = msg.meta),
                    )
                }

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

    companion object {
        /** The one place a sample's PCM is allocated; see `SizedBuffers.allocateOrNull` for why the catch is sound. */
        fun allocatePcmOrNull(frames: Int): DoubleArray? = try {
            DoubleArray(frames)
        } catch (e: Throwable) {
            null
        }
    }
}
