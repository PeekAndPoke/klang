/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Primes the audio render hot path before the first real voice arrives.
 *
 * Scheduling work runs on the **real** [PlaybackEngineDispatcher] engine so that V8 inline caches,
 * hidden-class bindings, and any lazy allocations inside those instances are warm when the user's
 * first kick hits. The caller silences the output buffer for the duration of the warmup, so users
 * do not hear the synthetic voices.
 *
 * At the end of the warmup window the runner hard-removes its own voices and resets the master
 * post chain (limiter envelope etc.) so no warmup residue leaks into real playback.
 */
class WarmupRunner(
    private val sampleRate: Int,
    /** Real backend host — warmup voices run through its engine; its post-chain is reset at the end. */
    private val dispatcher: PlaybackEngineDispatcher,
    /** Comm link used only to emit [KlangCommLink.Feedback.BackendReady]. */
    private val feedback: KlangCommLink.BackendEndpoint,
    /** Number of audio blocks to warm up for. 8 blocks ≈ 85 ms at 48 kHz / 512 frames. */
    private val warmupBlocks: Int = 8,
) {
    companion object {
        /** Reserved playback-id — no real song can use this. */
        const val WARMUP_PLAYBACK_ID = "--WARMUP--"

        /** Reserved sample name — no real sample can use this. */
        const val WARMUP_SAMPLE_NAME = "--warmup--"
    }

    private var started = false
    private var finished = false
    private var blocksRun = 0

    /** True while the runner is still priming the audio path. Callers should zero output in this window. */
    val isWarming: Boolean get() = started && !finished

    /**
     * Register the synthetic warmup sample on the real scheduler and schedule the warmup voices.
     * Idempotent.
     */
    fun start() {
        if (started) return
        started = true

        // Pre-register the all-zeros warmup sample (SYSTEM-wide sample cache). Reserved name —
        // no real song references it, so the entry is harmless left in the cache.
        dispatcher.handle(
            KlangCommLink.Cmd.Sample.Complete(
                req = SampleRequest(bank = null, sound = WARMUP_SAMPLE_NAME, index = null, note = null),
                note = null,
                pitchHz = 440.0,
                sample = MonoSamplePcm(
                    sampleRate = sampleRate,
                    pcm = DoubleArray(256),
                    meta = SampleMetadata.default,
                ),
            )
        )

        // Synth + sample voice exercise the full render path on a dedicated warmup engine (created
        // lazily). Cylinders are allocated lazily as the voices touch orbits — no global pre-alloc.
        dispatcher.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = WARMUP_PLAYBACK_ID,
                voices = listOf(
                    // Exercises Voice.render → IgnitorSine → filter → cylinder mix → limiter.
                    ScheduledVoice(
                        playbackId = WARMUP_PLAYBACK_ID,
                        data = VoiceData.empty.copy(
                            sound = "sine",
                            freqHz = 440.0,
                            adsr = AdsrDef.Std(attack = 0.005, decay = 0.05, sustain = 0.0, release = 0.05),
                        ),
                        startTime = 0.0,
                        gateEndTime = 0.1,
                        playbackStartTime = 0.0,
                    ),
                    // Exercises SampleIgnitor lookup + playback of the synthetic all-zeros sample.
                    ScheduledVoice(
                        playbackId = WARMUP_PLAYBACK_ID,
                        data = VoiceData.empty.copy(
                            sound = WARMUP_SAMPLE_NAME,
                            adsr = AdsrDef.Std(attack = 0.001, decay = 0.05, sustain = 0.0, release = 0.05),
                        ),
                        startTime = 0.0,
                        gateEndTime = 0.1,
                        playbackStartTime = 0.0,
                    ),
                ),
            )
        )
    }

    /**
     * Should be called once per audio block while warming. Counts progress toward [warmupBlocks]
     * and, on the final tick, cleans up warmup voices, resets the limiter, and emits
     * [KlangCommLink.Feedback.BackendReady]. Returns true while still warming, false once done.
     */
    fun tick(): Boolean {
        if (!started || finished) return false

        blocksRun++

        if (blocksRun >= warmupBlocks) {
            finished = true
            // Dispose the warmup engine entirely (scheduled + active + cylinders).
            dispatcher.cleanupHard(WARMUP_PLAYBACK_ID)
            // Wipe limiter state so the first real kick sees unity gain.
            dispatcher.resetPostChain()
            feedback.feedback.send(KlangCommLink.Feedback.BackendReady())
            return false
        }

        return true
    }
}
