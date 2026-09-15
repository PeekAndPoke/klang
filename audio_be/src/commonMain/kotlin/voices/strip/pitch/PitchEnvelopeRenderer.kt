/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.fastExp2
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Pitch envelope: attack/decay transient pitch modulation.
 * Creates pitch bends during the voice onset (e.g., drum tuning, synth swoops).
 *
 * All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
 */
class PitchEnvelopeRenderer(
    private val pitchEnvelope: Voice.PitchEnvelope,
        // Absolute backend frame — Double, see RenderClock.cursorFrame.
    private val startFrame: Double,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val pEnv = pitchEnvelope

        // Compute voice-relative position as Int (once per block)
        val blockRelStart = (ctx.blockStart + ctx.offset - startFrame).toInt()

        if (blockRelStart >= pEnv.attackFrames + pEnv.decayFrames) {
            // Settled on the anchor for the whole block: one ratio, not one per sample.
            val settled = fastExp2(pEnv.semitones * pEnv.anchor / 12.0)

            if (ctx.freqModBufferWritten) {
                for (i in 0 until ctx.length) {
                    buf[ctx.offset + i] *= settled
                }
            } else {
                for (i in 0 until ctx.length) {
                    buf[ctx.offset + i] = settled
                }

                ctx.freqModBufferWritten = true
            }

            return
        }

        if (ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] *= calculatePitchMod(blockRelStart + i, pEnv)
            }
        } else {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] = calculatePitchMod(blockRelStart + i, pEnv)
            }
            ctx.freqModBufferWritten = true
        }
    }

    private fun calculatePitchMod(relPos: Int, pEnv: Voice.PitchEnvelope): Double {
        val relPosD = relPos.toDouble()

        var envLevel = pEnv.anchor

        if (relPosD < pEnv.attackFrames) {
            val progress = relPosD / pEnv.attackFrames
            envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress
        } else if (relPosD < (pEnv.attackFrames + pEnv.decayFrames)) {
            val decayProgress = (relPosD - pEnv.attackFrames) / pEnv.decayFrames
            envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress
        }

        return fastExp2(pEnv.semitones * envLevel / 12.0)
    }
}
