/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler

/**
 * Standalone single-engine render-to-PCM, used by the offline renderer and the benchmarks.
 *
 * It is a thin wrapper around the **same** canonical chain the live dispatcher uses —
 * [PlaybackEngine.renderInto] (voices → cylinders → mix) followed by [MasterStage] (limiter + DC +
 * clip). Keeping it delegating means the DSP order lives in exactly one place. The realtime path
 * does NOT go through this class (it renders engines directly in `PlaybackEngineDispatcher`).
 */
class KlangAudioRenderer(
    sampleRate: Int,
    blockFrames: Int,
    voices: VoiceScheduler,
    cylinders: Cylinders,
) {
    private val mix = StereoBuffer(blockFrames)
    private val engine = PlaybackEngine(scheduler = voices, cylinders = cylinders)
    private val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * Clears stateful post-chain elements (limiter envelope + DC blocker IIR state).
     * Used at the end of the warmup handshake so post-chain state does not survive
     * into the first real playback block.
     */
    fun resetPostChain() {
        master.reset()
    }

    fun renderBlock(cursorFrame: Int, out: ShortArray) {
        mix.clear()
        engine.renderInto(mix, cursorFrame)
        master.process(mix, out)
    }
}
