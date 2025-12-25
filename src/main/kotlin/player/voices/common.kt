package io.peekandpoke.player.voices

import io.peekandpoke.utils.Numbers.TWO_PI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Shared Logic Extensions

fun Voice.mixToOrbit(ctx: Voice.RenderContext, offset: Int, length: Int) {
    val orbit = ctx.orbits.getOrInit(orbitId, this)

    // Equal Power Panning
    // Input: -1.0 (Left) .. 1.0 (Right)
    // Map to: 0.0 .. PI/2
    val panNorm = (pan.coerceIn(-1.0, 1.0) + 1.0) * 0.5
    val panAngle = panNorm * (PI / 2.0)

    val gainL = cos(panAngle) * gain
    val gainR = sin(panAngle) * gain

    // Pre-fetch orbit buffers
    val voiceBuffer = ctx.voiceBuffer
    val outL = orbit.mixBuffer.left
    val outR = orbit.mixBuffer.right
    val sendL = orbit.delaySendBuffer.left
    val sendR = orbit.delaySendBuffer.right

    val env = envelope
    val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
    val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
    val relRate = if (env.releaseFrames > 0) env.sustainLevel / env.releaseFrames else 1.0

    // Frame position relative to the start of the voice
    var absPos = (ctx.blockStart + offset) - startFrame
    val gateEndPos = gateEndFrame - startFrame
    var currentEnv = env.level

    val delayAmount = delay.amount
    val sendToDelay = delayAmount > 0.0

    for (i in 0 until length) {
        val idx = offset + i
        val signal = voiceBuffer[idx]

        // Envelope Logic
        if (absPos >= gateEndPos) {
            val relPos = absPos - gateEndPos
            currentEnv = env.sustainLevel - (relPos * relRate)
        } else {
            if (absPos < env.attackFrames) {
                currentEnv = absPos * attRate
            } else if (absPos < env.attackFrames + env.decayFrames) {
                val decPos = absPos - env.attackFrames
                currentEnv = 1.0 - (decPos * decRate)
            } else {
                currentEnv = env.sustainLevel
            }
        }

        if (currentEnv < 0.0) currentEnv = 0.0

        // Apply Envelope to signal
        val wetSignal = signal * currentEnv

        // 1. Dry Mix (Split to Stereo)
        val left = wetSignal * gainL
        val right = wetSignal * gainR

        outL[idx] += left
        outR[idx] += right

        // 2. Delay Send (Wet)
        if (sendToDelay) {
            sendL[idx] += left * delayAmount
            sendR[idx] += right * delayAmount
        }

        absPos++
    }

    env.level = currentEnv
}

fun Voice.fillVibrato(ctx: Voice.RenderContext, offset: Int, length: Int): DoubleArray? {
    if (vibrato.depth <= 0.0) return null

    val modBuffer = ctx.freqModBuffer
    val lfoInc = TWO_PI * vibrato.rate / ctx.sampleRate.toDouble()
    var lfoP = vibrato.phase

    for (i in 0 until length) {
        modBuffer[offset + i] = 1.0 + sin(lfoP) * vibrato.depth
        lfoP += lfoInc
    }
    vibrato.phase = lfoP

    return modBuffer
}
