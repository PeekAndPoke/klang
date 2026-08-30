/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.sin

/**
 * Tremolo effect — rhythmic amplitude modulation via sine LFO.
 */
class TremoloRenderer(
    rate: Double,
    private val depth: Double,
    sampleRate: Int,
) : BlockRenderer {

    private var phase: Double = 0.0
    private val phaseIncrement: Double = (rate * TWO_PI) / sampleRate

    override fun render(ctx: BlockContext) {
        if (depth <= 0.0) return

        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            // wrapPhase over the bare subtract (ledger W2): identical in range; a non-finite
            // or negative rate can no longer kill the phase for the voice's life.
            phase = (phase + phaseIncrement).wrapPhase(TWO_PI)

            val lfoNorm = (sin(phase) + 1.0) * 0.5
            val gain = 1.0 - (depth * (1.0 - lfoNorm))

            buf[idx] = (buf[idx] * gain)
        }
    }
}
