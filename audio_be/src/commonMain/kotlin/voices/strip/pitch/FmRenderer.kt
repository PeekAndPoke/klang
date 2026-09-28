/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.fastSin
import io.peekandpoke.klang.audio_be.smallNumFastMod
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import io.peekandpoke.klang.audio_be.wrapPhase
import kotlin.math.abs

/**
 * FM (Frequency Modulation) synthesis.
 * Modulates the pitch with a sine oscillator at [Voice.Fm.ratio] * baseFreq,
 * with envelope-controlled depth.
 */
class FmRenderer(
    private val fm: Voice.Fm,
    private val freqHz: Double,
    private val sampleRate: Int,
        // Absolute backend frame — Double, see RenderClock.cursorFrame.
    private val startFrame: Double,
) : BlockRenderer {
    private val core = EnvelopeCore()

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer

        // Ensure buffer is initialized
        if (!ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) buf[ctx.offset + i] = 1.0
            ctx.freqModBufferWritten = true
        }

        val modFreq = freqHz * fm.ratio
        val modInc = (TWO_PI * modFreq) / sampleRate
        var modPhase = fm.modPhase
        // The one-subtract wrap holds while |inc| < 2π; a modulator past the sample rate (a
        // raw-Motor ratio, either sign) takes the full wrap. NaN and infinite inc take it too.
        val safeWrap = !(abs(modInc) < TWO_PI)

        // Gate read from the ctx per call — a realtime note-off may move it (Voice.releaseGate)
        val envLevel = calculateControlRateEnvelope(fm.envelope, ctx.blockStart, startFrame, ctx.gateEndFrame, core)
        // Hoisted: the divide and the offset read are loop-invariant, and after the sine swap
        // the divide would be the loop's largest remaining cost.
        val depthOverFreq = fm.depth * envLevel / freqHz
        val off = ctx.offset

        for (i in 0 until ctx.length) {
            val fmMult = 1.0 + fastSin(modPhase) * depthOverFreq

            modPhase += modInc
            modPhase = if (safeWrap) modPhase.wrapPhase(TWO_PI) else modPhase.smallNumFastMod(TWO_PI)
            buf[off + i] *= fmMult
        }

        fm.modPhase = modPhase
    }
}
