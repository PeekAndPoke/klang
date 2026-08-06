/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
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
        // The master limiter has lookahead, so a left-only impulse emerges
        // LIMITER_LOOKAHEAD_SECONDS later — past the end of a single 64-frame block. Render enough
        // blocks to carry it through, then look for it wherever it lands.
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = ShortArray(blockFrames * 2)
        val leftSeen = mutableListOf<Int>()
        val rightSeen = mutableListOf<Int>()

        repeat(8) { block ->
            val mix = StereoBuffer(blockFrames)
            // left-only impulse in the first block, well below the -1 dB limiter threshold
            if (block == 0) mix.left[0] = 0.5

            master.process(mix, out)

            for (i in 0 until blockFrames) {
                if (out[i * 2].toInt() != 0) leftSeen += block * blockFrames + i
                if (out[i * 2 + 1].toInt() != 0) rightSeen += block * blockFrames + i
            }
        }

        leftSeen.shouldNotBeEmpty()          // the left impulse does come out...
        rightSeen.shouldBeEmpty()            // ...and never leaks into the right channel
    }
})
