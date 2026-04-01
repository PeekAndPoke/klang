package io.peekandpoke.klang.audio_be.voices.strip.send

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.PI
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
        val cylinder = ctx.renderContext.cylinders.getOrInit(voice.cylinderId, voice)

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

        for (i in 0 until length) {
            val idx = offset + i

            // Read processed signal from voice buffer
            var signal = audioBuffer[idx].toDouble()

            // Apply post-gain
            signal *= voice.postGain

            // Split to Stereo with panning
            val left = signal * gainL
            val right = signal * gainR

            // Sum to cylinder mix buffer
            outL[idx] = (outL[idx] + left).toFloat()
            outR[idx] = (outR[idx] + right).toFloat()

            // Send to effects buses
            if (sendToDelay) {
                delaySendL[idx] = (delaySendL[idx] + left * delayAmount).toFloat()
                delaySendR[idx] = (delaySendR[idx] + right * delayAmount).toFloat()
            }

            if (sendToReverb) {
                reverbSendL[idx] = (reverbSendL[idx] + left * reverbAmount).toFloat()
                reverbSendR[idx] = (reverbSendR[idx] + right * reverbAmount).toFloat()
            }
        }
    }
}
