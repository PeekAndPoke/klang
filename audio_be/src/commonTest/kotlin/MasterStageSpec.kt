/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Guards the final master/output stage extracted from KlangAudioRenderer (D2·1). D2·b will call
 * [MasterStage.process] directly on the summed mix, so the wiring (limiter → DC → clip + interleave)
 * is covered here in isolation. (Clip-bounds + limiter math are also covered by KlangAudioRendererSpec.)
 */
class MasterStageSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 64

    "silent mix produces all-zero output" {
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val mix = StereoBuffer(blockFrames)            // cleared on construction
        val out = ShortArray(blockFrames * 2) { 999 }  // non-zero, must be overwritten

        master.process(mix, out)

        out.all { it == 0.toShort() } shouldBe true
    }

    "output is interleaved L/R and routes channels independently" {
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val mix = StereoBuffer(blockFrames)
        mix.left[0] = 0.5   // left-only impulse, well below the -1 dB limiter threshold
        val out = ShortArray(blockFrames * 2)

        master.process(mix, out)

        (out[0].toInt() != 0) shouldBe true   // left frame 0 carries signal
        out[1] shouldBe 0.toShort()           // right frame 0 stays silent
    }
})
