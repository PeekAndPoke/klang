package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.*

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
    val delaySendL = orbit.delaySendBuffer.left
    val delaySendR = orbit.delaySendBuffer.right
    val reverbSendL = orbit.reverbSendBuffer.left
    val reverbSendR = orbit.reverbSendBuffer.right

    val env = envelope
    val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
    val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
    val relRate = if (env.releaseFrames > 0) env.sustainLevel / env.releaseFrames else 1.0

    // Frame position relative to the start of the voice
    var absPos = (ctx.blockStart + offset) - startFrame
    val gateEndPos = gateEndFrame - startFrame
    var currentEnv = env.level

    // Delay
    val delayAmount = delay.amount
    val sendToDelay = delayAmount > 0.0

    // Reverb
    val reverbAmount = reverb.room
    val sendToReverb = reverbAmount > 0.0

    // Distortion: Drive factor: 1.0 (no distortion) to ~11.0 (heavy distortion)
    val hasDistortion = this@mixToOrbit.distort.amount > 0.0
    val distortionDrive = 1.0 + (this@mixToOrbit.distort.amount * 10.0)

    // Crush
    val crushBits = crush.amount
    val hasCrush = crushBits > 0.0
    val crushLevels = if (hasCrush) 2.0.pow(crushBits).toInt().toDouble() else 0.0

    // Coarse
    val coarseFactor = coarse.amount
    val hasCoarse = coarseFactor > 1.0

    for (i in 0 until length) {
        val idx = offset + i
        var signal = voiceBuffer[idx]

        // 1. Bitcrushing (Crush)
        if (hasCrush) {
            signal = if (crushLevels > 1) {
                floor(signal * (crushLevels / 2.0)) / (crushLevels / 2.0)
            } else signal
        }

        // 2. Downsampling (Coarse)
        if (hasCoarse) {
            if (coarse.coarseCounter >= 1.0 || i == 0 && coarse.coarseCounter == 0.0) {
                coarse.lastCoarseValue = signal
                coarse.coarseCounter -= 1.0
            }
            signal = coarse.lastCoarseValue
            coarse.coarseCounter += (1.0 / coarseFactor)
        }

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
        var wetSignal = signal * currentEnv

        if (hasDistortion) {
            wetSignal = tanh(wetSignal * distortionDrive)
        }

        // 1. Dry Mix (Split to Stereo)
        val left = wetSignal * gainL
        val right = wetSignal * gainR

        outL[idx] += left
        outR[idx] += right

        // 2. Delay Send (Wet)
        if (sendToDelay) {
            delaySendL[idx] += left * delayAmount
            delaySendR[idx] += right * delayAmount
        }

        // 3. Reverb Send (Wet)
        if (sendToReverb) {
            reverbSendL[idx] += left * reverbAmount
            reverbSendR[idx] += right * reverbAmount
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
