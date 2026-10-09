/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Standalone single-engine render, used by the offline renderer and the benchmarks. Its output is the
 * engine's floating-point stereo block (see [MasterStage.process]); a caller that needs 16-bit PCM
 * converts at its own edge with [writePcm16].
 *
 * It owns its own [AudioBackendContext] + clock + one [PlaybackEngine], and runs the **same**
 * canonical chain as the live dispatcher — [PlaybackEngine.renderInto] (voices → cylinders → mix)
 * then [MasterStage] (DC blockers + limiter + clip). The realtime path does NOT go through this class.
 */
class KlangAudioRenderer private constructor(
    private val context: AudioBackendContext,
    private val clock: BackendClock,
) {
    private val engine = PlaybackEngine.create(context = context, playbackId = ENGINE_PLAYBACK_ID)
    private val mix = StereoBuffer(context.blockFrames)
    private val master = MasterStage(sampleRate = context.sampleRate, blockFrames = context.blockFrames)

    /** The single engine's scheduler — callers schedule voices here. */
    val voices: VoiceScheduler get() = engine.scheduler

    /** Parent ignitor registry — callers register custom oscillators here. */
    val ignitorRegistry: IgnitorRegistry get() = context.ignitorRegistry

    /** Parent Katalyst registry; callers register custom chains here, for an orbit or the output. */
    val katalystRegistry: KatalystRegistry get() = context.katalystRegistry

    /**
     * Frames of latency the master post-chain adds (the limiter's lookahead delay).
     *
     * An offline render must run this many frames PAST its musical end, or the final samples are
     * still in the delay ring when the loop stops.
     */
    val latencyFrames: Int get() = master.latencyFrames

    fun setBackendStartTime(startTimeSec: Double) {
        clock.startTimeSec = startTimeSec
    }

    /** Clears the master post-chain (limiter envelope + DC blocker IIR state). */
    fun resetPostChain() {
        master.reset()
    }

    /** Renders one block into [out] (`blockFrames` frames per channel), clipped to `[-1, 1]`. */
    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderBlock(cursorFrame: Double, out: StereoBuffer) {
        clock.cursorFrame = cursorFrame
        mix.clear()
        engine.renderInto(mix, cursorFrame)
        master.process(mix = mix, out = out)
        // The same per-block housekeeping as the live dispatcher: offline is not realtime, but
        // "every render loop housekeeps" keeps the shelf contract a renderer contract, not a host one.
        context.warehouse.housekeep()
        // Same convention as the live dispatcher (RenderClock.cursorFrame): the clock is the NEXT
        // block between renders. Offline, everything is scheduled before the first render at
        // cursor 0, so this changes nothing about where a render starts.
        clock.cursorFrame = cursorFrame + context.blockFrames
    }

    /** The render clock, for the spec that pins the between-renders convention offline (block-framing B1). */
    internal val clockForTest: RenderClock get() = clock

    companion object {
        /**
         * The id the one engine is filed under. Nothing reads it here (there is no dispatcher); the voices a
         * caller schedules name their own playback, and the scheduler's context takes that id.
         */
        private const val ENGINE_PLAYBACK_ID: String = "offline"

        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double = { 0.0 },
            phasePoolSeed: Int? = null,
        ): KlangAudioRenderer {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
                phasePoolSeed = phasePoolSeed,
            )
            return KlangAudioRenderer(context = context, clock = clock)
        }
    }
}
