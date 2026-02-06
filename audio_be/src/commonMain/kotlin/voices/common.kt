package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.TWO_PI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
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

    // Delay and Reverb send amounts
    val delayAmount = delay.amount
    val sendToDelay = delayAmount > 0.0
    val reverbAmount = reverb.room
    val sendToReverb = reverbAmount > 0.0

    for (i in 0 until length) {
        val idx = offset + i

        // Read processed signal from voice buffer
        // (All effects have already been applied in the voice's render pipeline)
        var signal = voiceBuffer[idx]

        // Apply post-gain
        signal *= postGain

        // Split to Stereo with panning
        val left = signal * gainL
        val right = signal * gainR

        // Sum to orbit mix buffer
        outL[idx] += left
        outR[idx] += right

        // Send to effects buses
        if (sendToDelay) {
            delaySendL[idx] += left * delayAmount
            delaySendR[idx] += right * delayAmount
        }

        if (sendToReverb) {
            reverbSendL[idx] += left * reverbAmount
            reverbSendR[idx] += right * reverbAmount
        }
    }
}

/**
 * Adjust pitch by combining [Voice.Accelerate], [Voice.Vibrato], and [Voice.PitchEnvelope]
 * Returns null if no modulation is active.
 */
fun Voice.fillPitchModulation(ctx: Voice.RenderContext, offset: Int, length: Int): DoubleArray? {
    val vib = vibrato
    val accel = accelerate.amount
    val pEnv = pitchEnvelope

    val hasVibrato = vib.depth > 0.0
    val hasAccelerate = accel != 0.0
    val hasPitchEnv = pEnv != null

    // CRITICAL FIX: Return null instead of a dirty buffer!
    // This tells the voice to use standard playback speed/frequency.
    if (!hasVibrato && !hasAccelerate && !hasPitchEnv) return null

    val out = ctx.freqModBuffer
    val totalFrames = (endFrame - startFrame).toDouble()

    var phase = vib.phase
    val phaseInc = (TWO_PI * vib.rate) / ctx.sampleRate

    for (i in 0 until length) {
        val idx = offset + i
        val absFrame = ctx.blockStart + idx
        val relPos = (absFrame - startFrame).toDouble()

        // 1. Vibrato component
        val vibMod = if (hasVibrato) {
            val mod = 1.0 + (sin(phase) * vib.depth)
            phase += phaseInc
            mod
        } else {
            1.0
        }

        // 2. Accelerate component (Exponential pitch change)
        val accelMod = if (hasAccelerate) {
            val progress = relPos / totalFrames
            2.0.pow(accel * progress)
        } else {
            1.0
        }

        // 3. Pitch Envelope component
        val pEnvMod = if (hasPitchEnv) {
            var envLevel = pEnv.anchor

            if (relPos < pEnv.attackFrames) {
                // Attack: Anchor -> 1.0
                val progress = relPos / pEnv.attackFrames
                envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress
            } else if (relPos < (pEnv.attackFrames + pEnv.decayFrames)) {
                // Decay: 1.0 -> Anchor
                val decayProgress = (relPos - pEnv.attackFrames) / pEnv.decayFrames
                envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress
            }
            // Else Sustain at Anchor

            // Convert semitone offset to frequency ratio
            2.0.pow((pEnv.amount * envLevel) / 12.0)
        } else {
            1.0
        }

        out[idx] = vibMod * accelMod * pEnvMod
    }

    vib.phase = phase
    return out
}

/**
 * Calculate filter envelope value at the current block position.
 * Returns a value from 0.0 to 1.0 representing the envelope's current state.
 * This is calculated once per block (control rate) for efficiency.
 */
fun Voice.calculateFilterEnvelope(env: Voice.Envelope, ctx: Voice.RenderContext): Double {
    // Calculate position relative to voice start
    // Use the actual voice start position in the block (handles voices starting mid-block)
    val currentFrame = maxOf(ctx.blockStart, startFrame)
    val absPos = currentFrame - startFrame
    val gateEndPos = gateEndFrame - startFrame

    val envValue = if (absPos >= gateEndPos) {
        // Release phase
        val relPos = absPos - gateEndPos
        val relRate = if (env.releaseFrames > 0) env.sustainLevel / env.releaseFrames else 1.0
        env.sustainLevel - (relPos * relRate)
    } else {
        // Attack/Decay/Sustain phases
        when {
            absPos < env.attackFrames -> {
                // Attack phase
                val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
                absPos * attRate
            }

            absPos < env.attackFrames + env.decayFrames -> {
                // Decay phase
                val decPos = absPos - env.attackFrames
                val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
                1.0 - (decPos * decRate)
            }

            else -> {
                // Sustain phase
                env.sustainLevel
            }
        }
    }

    return envValue.coerceIn(0.0, 1.0)
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
