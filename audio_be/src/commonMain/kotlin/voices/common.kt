package io.peekandpoke.klang.audio_be.voices

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Shared Logic Extensions

/**
 * Simplified mixing function - handles only post-gain, panning, and buffer summing.
 * All effects (crush, coarse, distortion, tremolo, etc.) are now handled in the voice's render pipeline.
 */
fun Voice.mixToOrbit(ctx: Voice.RenderContext, offset: Int, length: Int) {
    val orbit = ctx.orbits.getOrInit(orbitId, this)

    // Equal Power Panning
    // Input: -1.0 (Left) .. 1.0 (Right)
    // Map to: 0.0 .. PI/2
    val panNorm = pan.coerceIn(0.0, 1.0)
    val panAngle = panNorm * (PI / 2.0)

    // Apply dynamic gain multiplier (e.g., for solo/mute, fades, etc.)
    val effectiveGain = gain * gainMultiplier

    val gainL = cos(panAngle) * effectiveGain
    val gainR = sin(panAngle) * effectiveGain

    // Pre-fetch orbit buffers
    val voiceBuffer = ctx.voiceBuffer
    val outL = orbit.mixBuffer.left
    val outR = orbit.mixBuffer.right
    val delaySendL = orbit.delaySendBuffer.left
    val delaySendR = orbit.delaySendBuffer.right
    val reverbSendL = orbit.reverbSendBuffer.left
    val reverbSendR = orbit.reverbSendBuffer.right

    // Delay and Reverb send amounts
    val delayAmount = delay.amount
    val sendToDelay = delayAmount > 0.0
    val reverbAmount = reverb.room
    val sendToReverb = reverbAmount > 0.0

    for (i in 0 until length) {
        val idx = offset + i

        // Read processed signal from voice buffer
        // (All effects have already been applied in the voice's render pipeline)
        var signal = voiceBuffer[idx].toDouble()

        // Apply post-gain
        signal *= postGain

        // Split to Stereo with panning
        val left = signal * gainL
        val right = signal * gainR

        // Sum to orbit mix buffer
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
