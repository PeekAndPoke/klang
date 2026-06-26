/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Standalone single-engine render-to-PCM, used by the offline renderer and the benchmarks.
 *
 * It owns its own [AudioBackendContext] + clock + one [PlaybackEngine], and runs the **same**
 * canonical chain as the live dispatcher — [PlaybackEngine.renderInto] (voices → cylinders → mix)
 * then [MasterStage] (limiter + DC + clip). The realtime path does NOT go through this class.
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

    fun setBackendStartTime(startTimeSec: Double) {
        clock.startTimeSec = startTimeSec
    }

    /** Clears the master post-chain (limiter envelope + DC blocker IIR state). */
    fun resetPostChain() {
        master.reset()
    }

    fun renderBlock(cursorFrame: Int, out: ShortArray) {
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
        ): KlangAudioRenderer {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
            )
            return KlangAudioRenderer(context = context, clock = clock)
        }
    }
}
