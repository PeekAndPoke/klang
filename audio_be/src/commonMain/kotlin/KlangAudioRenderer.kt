/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.master.MasterRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Standalone single-engine render-to-PCM, used by the offline renderer and the benchmarks.
 *
 * It owns its own [AudioBackendContext] + clock + one [PlaybackEngine], and runs the **same**
 * canonical chain as the live dispatcher — [PlaybackEngine.renderInto] (voices → cylinders → mix)
 * then [MasterStage] (DC blockers + limiter + clip). The realtime path does NOT go through this class.
 */
class KlangAudioRenderer private constructor(
    private val context: AudioBackendContext,
    private val clock: BackendClock,
) {
    private val engine = PlaybackEngine.create(context)
    private val mix = StereoBuffer(context.blockFrames)
    private val master = MasterStage(sampleRate = context.sampleRate, blockFrames = context.blockFrames)

    /** The single engine's scheduler — callers schedule voices here. */
    val voices: VoiceScheduler get() = engine.scheduler

    /** Parent ignitor registry — callers register custom oscillators here. */
    val ignitorRegistry: IgnitorRegistry get() = context.ignitorRegistry

    /** Parent pipeline registry — callers register custom voice pipelines here. */
    val pipelineRegistry: PipelineRegistry get() = context.pipelineRegistry

    /** Parent master registry — callers register custom master chains here. */
    val masterRegistry: MasterRegistry get() = context.masterRegistry

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

    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderBlock(cursorFrame: Double, out: ShortArray) {
        clock.cursorFrame = cursorFrame
        mix.clear()
        engine.renderInto(mix, cursorFrame)
        master.process(mix, out)
    }

    companion object {
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
