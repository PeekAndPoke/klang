/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
    /**
     * Number of audio blocks to warm up for: one per warmed orbit ([WARMUP_ORBITS]), plus
     * [TAIL_BLOCKS] so the last orbit's voice has rendered through its effects too. 24 blocks ≈
     * 64 ms at 48 kHz / [AudioBackendContext.RENDER_QUANTUM_FRAMES] frames.
     *
     * A block count, not a duration — deliberately. What is being primed is per-block work (JIT of
     * the render path, lazy allocations, inline caches), so the useful unit is *renders performed*.
     * It used to be ~85 ms on the JVM only because that backend ran a 4× larger block; that
     * divergence is gone.
     */
    private val warmupBlocks: Int = WARMUP_ORBITS + TAIL_BLOCKS,
) {
    companion object {
        /** Reserved playback-id — no real song can use this. */
        const val WARMUP_PLAYBACK_ID = "--WARMUP--"

        /** Reserved sample name — no real sample can use this. */
        const val WARMUP_SAMPLE_NAME = "--warmup--"

        /**
         * Orbits the warmup builds — one cylinder, one delay ring and one reverb network each, all of
         * which go to the warehouse's shelves when the warmup engine is disposed, so the first song
         * takes them from there instead of building them in its first frame (maintainer, 2026-09-04,
         * after the Fairphone measurement: that first frame killed the playback). 16 covers every
         * builtin song (the widest uses 8) twice over.
         */
        const val WARMUP_ORBITS: Int = 16

        /**
         * The warmup is BUCKETED: orbit k's voice starts in block k, so each block builds ONE cylinder,
         * rents one ring and one network. Building all sixteen in the first render frame would be the
         * very stall the warmup exists to prevent — inaudible (output is silenced), but on a phone
         * long enough to have the worklet dropped for overrunning.
         */
        const val TAIL_BLOCKS: Int = 8

        /**
         * Sounds the warmed voices rotate through. Each is a different ignitor graph, and the first
         * note of each JITs it: a song's first supersaw used to compile in a live frame. The
         * warmup's own all-zeros sample exercises the sample path.
         */
        val WARMUP_SOUNDS: List<String> = listOf("sine", "saw", "supersaw", "square", "triangle", WARMUP_SAMPLE_NAME)
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

        // One WET voice per warmed orbit on a dedicated warmup engine, each starting one block
        // after the previous (bucketed, see TAIL_BLOCKS): every block builds one cylinder and rents
        // one ring + one network, and the voice renders through delay, reverb, filter and its
        // ignitor graph — Voice.render → ignitor → filter → cylinder mix → delay → reverb → limiter.
        // Explicit and synthetic, not a builtin song (maintainer). Output is silenced by the host.
        val blockSec = AudioBackendContext.RENDER_QUANTUM_FRAMES.toDouble() / sampleRate
        dispatcher.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = WARMUP_PLAYBACK_ID,
                voices = List(WARMUP_ORBITS) { orbit ->
                    // Mid-block, not on the boundary: block-framing B2 drops a voice whose start is
                    // behind the block it is promoted for, and `k * blockSec` can land an ulp before
                    // the clock's own `k * frames / sampleRate`.
                    val start = (orbit + 0.5) * blockSec
                    ScheduledVoice(
                        playbackId = WARMUP_PLAYBACK_ID,
                        data = VoiceData.empty.copy(
                            sound = WARMUP_SOUNDS[orbit % WARMUP_SOUNDS.size],
                            freqHz = 220.0 + 20.0 * orbit,
                            cylinder = orbit,
                            adsr = AdsrDef.Std(attack = 0.001, decay = 0.05, sustain = 0.0, release = 0.05),
                            cutoff = 2000.0,
                            resonance = 0.3,
                            delay = 0.5,
                            delayTime = 0.3,
                            delayFeedback = 0.2,
                            room = 0.5,
                            roomSize = 0.6,
                        ),
                        startTime = start,
                        gateEndTime = start + 2 * blockSec,
                        playbackStartTime = 0.0,
                    )
                },
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
            // Dispose the warmup engine entirely: its cylinders, rings and networks go to the
            // warehouse's shelves (the return path, 2f), stocked for the first real playback.
            dispatcher.cleanupHard(WARMUP_PLAYBACK_ID)
            // Wipe limiter state so the first real kick sees unity gain.
            dispatcher.resetPostChain()
            feedback.feedback.send(KlangCommLink.Feedback.BackendReady())
            return false
        }

        return true
    }
}
