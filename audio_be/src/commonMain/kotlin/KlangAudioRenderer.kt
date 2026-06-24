/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Cylinders -> [MasterStage] -> Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 *
 * The final post-chain (limiter + DC block + clip/interleave) lives in [MasterStage] so the
 * per-playback mixdown can reuse it once on the summed mix — see `docs/tasks/per-playback-engine.md`.
 */
class KlangAudioRenderer(
    sampleRate: Int,
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val cylinders: Cylinders,
) {
    private val mix = StereoBuffer(blockFrames)

    private val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * Clears stateful post-chain elements (limiter envelope + DC blocker IIR state).
     * Used at the end of the warmup handshake so post-chain state does not survive
     * into the first real playback block.
     */
    fun resetPostChain() {
        master.reset()
    }

    /**
     * Pre-allocates every cylinder up to the configured `maxCylinders`, so the first
     * note of a song that touches a new orbit doesn't pay the allocation cost during
     * its audio block. Called from the warmup handshake.
     */
    fun preallocateCylinders() {
        cylinders.preallocateAll()
    }

    fun renderBlock(
        cursorFrame: Int,
        out: ShortArray,
    ) {
        // 1. Reset mix buffers
        mix.clear()
        cylinders.clearAll()

        // 2. Process voices
        voices.process(cursorFrame)

        // 3. Mix voices through the cylinders into the main mix
        cylinders.processAndMix(mix)

        // 4. Master/output stage: limiter -> DC block -> transparent clip + interleave.
        master.process(mix, out)
    }
}
