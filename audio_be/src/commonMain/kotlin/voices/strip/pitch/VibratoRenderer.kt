/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.fastExp2
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.sin

/**
 * LFO pitch modulation (vibrato).
 * Writes periodic pitch multipliers to [BlockContext.freqModBuffer].
 */
class VibratoRenderer(
    private val vibrato: Voice.Vibrato,
    private val sampleRate: Int,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val phaseInc = (TWO_PI * vibrato.rate) / sampleRate
        val depthOctaves = vibrato.semitones / 12.0
        var phase = vibrato.phase

        if (ctx.freqModBufferWritten) {
            // Multiply into existing modulation
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                // Equal temperament: symmetric, never negative
                buf[idx] *= fastExp2(sin(phase) * depthOctaves)
                phase += phaseInc
            }
        } else {
            // First pitch renderer — write directly
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] = fastExp2(sin(phase) * depthOctaves)
                phase += phaseInc
            }
            ctx.freqModBufferWritten = true
        }

        if (phase >= TWO_PI) phase -= TWO_PI
        vibrato.phase = phase
    }
}
