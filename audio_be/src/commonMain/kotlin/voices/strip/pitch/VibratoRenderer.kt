/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.fastExp2
import io.peekandpoke.klang.audio_be.utils.fastSin
import io.peekandpoke.klang.audio_be.utils.wrapPhaseFastOrSafe
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.abs

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
        // The one-subtract wrap holds while |inc| < 2π; a rate past the sample rate (raw-Motor,
        // either sign) takes the full wrap. NaN and infinite inc take it too.
        val safeWrap = !(abs(phaseInc) < TWO_PI)
        val off = ctx.offset

        if (ctx.freqModBufferWritten) {
            // Multiply into existing modulation
            for (i in 0 until ctx.length) {
                val idx = off + i
                // Equal temperament: symmetric, never negative
                buf[idx] *= fastExp2(fastSin(phase) * depthOctaves)
                phase += phaseInc
                phase = phase.wrapPhaseFastOrSafe(period = TWO_PI, safe = safeWrap)
            }
        } else {
            // First pitch renderer — write directly
            for (i in 0 until ctx.length) {
                val idx = off + i
                buf[idx] = fastExp2(fastSin(phase) * depthOctaves)
                phase += phaseInc
                phase = phase.wrapPhaseFastOrSafe(period = TWO_PI, safe = safeWrap)
            }
            ctx.freqModBufferWritten = true
        }

        vibrato.phase = phase
    }
}
