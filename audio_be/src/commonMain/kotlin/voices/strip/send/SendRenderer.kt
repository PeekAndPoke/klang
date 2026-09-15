/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.send

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Routes the processed voice signal to the cylinder mixer.
 *
 * This is the final stage of the voice pipeline. Reads the processed audio from
 * [BlockContext.audioBuffer] and mixes it into the cylinder's stereo buffers with
 * panning and effect sends (delay, reverb).
 */
class SendRenderer(
    private val voice: Voice,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val cylinder = ctx.renderContext.cylinders.getOrInit(voice.cylinderId, voice, ctx.renderContext.blockStart)

        // Equal Power Panning
        // Input: 0.0 (Left) .. 1.0 (Right)
        // Map to: 0.0 .. PI/2
        val panNorm = voice.pan.coerceIn(0.0, 1.0)
        val panAngle = panNorm * (PI / 2.0)

        // Apply dynamic gain multiplier (e.g., for solo/mute, fades, etc.)
        val effectiveGain = voice.gain * voice.gainMultiplier

        val gainL = cos(panAngle) * effectiveGain
        val gainR = sin(panAngle) * effectiveGain

        // Pre-fetch cylinder buffers
        val audioBuffer = ctx.audioBuffer
        val outL = cylinder.mixBuffer.left
        val outR = cylinder.mixBuffer.right
        val delaySendL = cylinder.delaySendBuffer.left
        val delaySendR = cylinder.delaySendBuffer.right
        val reverbSendL = cylinder.reverbSendBuffer.left
        val reverbSendR = cylinder.reverbSendBuffer.right

        // Delay and Reverb send amounts
        val delayAmount = voice.delay.amount
        val sendToDelay = delayAmount > 0.0
        val reverbAmount = voice.reverb.room
        val sendToReverb = reverbAmount > 0.0

        val offset = ctx.offset
        val length = ctx.length

        if (ctx.measurePeak) {
            measurePeak(ctx, audioBuffer, offset, length, delayAmount, reverbAmount)
        }

        for (i in 0 until length) {
            val idx = offset + i

            // Read processed signal from voice buffer
            var signal = audioBuffer[idx]

            // Apply post-gain
            signal *= voice.postGain

            // Split to Stereo with panning
            val left = signal * gainL
            val right = signal * gainR

            // Sum to cylinder mix buffer
            outL[idx] = (outL[idx] + left)
            outR[idx] = (outR[idx] + right)

            // Send to effects buses
            if (sendToDelay) {
                delaySendL[idx] = (delaySendL[idx] + left * delayAmount)
                delaySendR[idx] = (delaySendR[idx] + right * delayAmount)
            }

            if (sendToReverb) {
                reverbSendL[idx] = (reverbSendL[idx] + left * reverbAmount)
                reverbSendR[idx] = (reverbSendR[idx] + right * reverbAmount)
            }
        }
    }

    /**
     * The block's output peak for silence culling (`Voice.render`): an upper bound on what the
     * cylinder receives from this voice on the mix bus AND the send buses, BEFORE the solo/mute
     * multiplier (so a soloed-away voice is not taken for a dead one). A separate pass, run only
     * on the blocks that read it, so the mix loop above stays untouched.
     */
    private fun measurePeak(
        ctx: BlockContext,
        audioBuffer: AudioBuffer,
        offset: Int,
        length: Int,
        delayAmount: Double,
        reverbAmount: Double,
    ) {
        var peak = 0.0
        val end = offset + length

        for (i in offset until end) {
            val sample = audioBuffer[i]
            val magnitude = if (sample < 0.0) -sample else sample

            // NaN-guard: a NaN sample fails this compare and counts as silence, so a voice that blew
            // up in its release is culled rather than kept poisoning the bus.
            if (magnitude > peak) {
                peak = magnitude
            }
        }

        // Raw-Motor: gain, postGain and the send amounts are unclamped and may be negative or above 1;
        // the bound is a magnitude, and a send above 1 puts more on its bus than the mix gets. A NaN
        // gain makes the peak NaN, which reads as audible (never culled): NaN-guard by inaction.
        val gainMagnitude = abs(voice.gain * voice.postGain)
        val sendMagnitude = maxOf(1.0, abs(delayAmount), abs(reverbAmount))

        ctx.voiceOutputPeak = peak * gainMagnitude * sendMagnitude
    }
}
