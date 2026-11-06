/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The voice's channel, its last stage: mixes the voice's output into its cylinder's mix with pan and gain (the one
 * level word, `gain`, applied once here), the solo/mute multiplier ramped where it moved. Nothing else: the orbit's
 * delay and reverb take their feed from the orbit mix since Katalyst step 5b-2, so a voice has no send of its own.
 * Was `SendRenderer` until 2026-11-06 (KLANG-00290 (engine follow ups) item 21: the voice's fixed stages are plain
 * code, and the stage lost the "send" word, audit A2.5).
 *
 * Returns the block's output peak for silence culling when [measurePeak] is set, else 0.0 (see [measureVoicePeak]).
 */
internal fun mixIntoCylinder(
    voice: Voice,
    ctx: Voice.RenderContext,
    /** The voice's output this block, at its rate ([rateSteps]). */
    audioBuffer: AudioBuffer,
    /** The voice's window in the block, in frames. */
    offsetFrames: Int,
    lengthFrames: Int,
    /** The 2x steps above the base rate the voice hands over at, 0 at the base rate. */
    rateSteps: Int,
    measurePeak: Boolean,
): Double {
    // Every rendering voice routes into its orbit; only a voice whose gate is open offers itself as the owner
    // of the orbit's bus settings (`Voice.claimsOrbit`), the newest offer of the block wins (`Cylinder.offer`).
    val cylinders = ctx.cylinders
    val blockStart = ctx.blockStart
    val cylinder = if (voice.claimsOrbit(blockStart)) {
        cylinders.offer(id = voice.cylinderId, voice = voice, blockStart = blockStart)
    } else {
        cylinders.checkIn(id = voice.cylinderId, blockStart = blockStart)
    }

    // Equal Power Panning
    // Input: 0.0 (Left) .. 1.0 (Right)
    // Map to: 0.0 .. PI/2
    val panNorm = voice.pan.coerceIn(0.0, 1.0)
    val panAngle = panNorm * (PI / 2.0)

    // Pre-fetch cylinder buffers. A voice that hands over (KLANG-00214 (oversampling regions) step 3b) lands on the
    // orbit's bus at its rate, 2^rateSteps samples per frame; the orbit converts every voice there at once.
    val steps = rateSteps
    val mix = cylinder.mixBufferAt(steps)
    val outL = mix.left
    val outR = mix.right

    val offset = offsetFrames shl steps
    val length = lengthFrames shl steps

    val peak = if (measurePeak) {
        measureVoicePeak(voice = voice, audioBuffer = audioBuffer, offset = offset, length = length)
    } else {
        0.0
    }

    // The solo/mute multiplier moved since the last block: ramp it across this one (no step, no click).
    if (voice.gainMultiplierFrom != voice.gainMultiplier) {
        mixRamped(
            voice = voice,
            blockSamples = (ctx.blockFrames shl steps).toDouble(),
            panAngle = panAngle,
            audioBuffer = audioBuffer,
            outL = outL,
            outR = outR,
            offset = offset,
            length = length,
        )

        return peak
    }

    // Apply dynamic gain multiplier (e.g., for solo/mute, fades, etc.)
    val effectiveGain = voice.gain * voice.gainMultiplier

    val gainL = cos(panAngle) * effectiveGain
    val gainR = sin(panAngle) * effectiveGain

    for (i in 0 until length) {
        val idx = offset + i

        // Read processed signal from voice buffer
        val signal = audioBuffer[idx]

        // Split to Stereo with panning
        val left = signal * gainL
        val right = signal * gainR

        // Sum to cylinder mix buffer
        outL[idx] = (outL[idx] + left)
        outR[idx] = (outR[idx] + right)
    }

    return peak
}

/**
 * The mix loop with the multiplier ramped linearly across the whole block, from [Voice.gainMultiplierFrom] to
 * [Voice.gainMultiplier]: frame `k` of the block uses `from + (to - from) * (k + 1) / blockFrames`, so the
 * block's last frame lands on the new value and the next block continues from there. A voice that renders only
 * part of the block (it starts or ends inside it) reads the same line at its own frames.
 */
private fun mixRamped(
    voice: Voice,
    /** The block's length in the buffer's samples: a voice that hands over reads the same line between the frames. */
    blockSamples: Double,
    panAngle: Double,
    audioBuffer: AudioBuffer,
    outL: AudioBuffer,
    outR: AudioBuffer,
    /** The window in the buffer's samples: frames times `1 shl rateSteps` (see [mixIntoCylinder]). */
    offset: Int,
    length: Int,
) {
    val from = voice.gainMultiplierFrom
    val delta = voice.gainMultiplier - from
    val baseL = cos(panAngle) * voice.gain
    val baseR = sin(panAngle) * voice.gain

    for (i in 0 until length) {
        val idx = offset + i
        val multiplier = from + delta * (idx + 1) / blockSamples
        val signal = audioBuffer[idx]

        outL[idx] = (outL[idx] + signal * (baseL * multiplier))
        outR[idx] = (outR[idx] + signal * (baseR * multiplier))
    }
}

/**
 * The block's output peak for silence culling (`Voice.render`): an upper bound on what the
 * cylinder receives from this voice, BEFORE the solo/mute multiplier (so a soloed-away voice is
 * not taken for a dead one). A separate pass, run only on the blocks that read it, so the mix
 * loop above stays untouched.
 *
 * The mix is all a voice reaches since Katalyst step 5b-2: the delay and the reverb are fed from
 * the orbit mix by the owner's `wet`, so a wet above 1 amplifies an orbit, not a voice, and
 * the bound no longer scales by a send amount.
 */
private fun measureVoicePeak(
    voice: Voice,
    audioBuffer: AudioBuffer,
    offset: Int,
    length: Int,
): Double {
    var peak = 0.0
    val end = offset + length

    for (i in offset until end) {
        val sample = audioBuffer[i]
        val magnitude = if (sample < 0.0) -sample else sample

        // NaN-guard: a NaN sample loses this compare, so it contributes nothing and a block
        // of them measures as silence. One rule covers that and the infinities below: CULL ON
        // WHAT IS MEASURABLY SILENT. A NaN has no magnitude to compare, so it leaves nothing
        // to measure and the block reads as silent, which is how a voice that blew up in its
        // release stops rendering and stops feeding the bus. An INFINITE sample does win this
        // compare, and an infinite bound is a measurement that is not small, so that voice is
        // kept (see below).
        if (magnitude > peak) {
            peak = magnitude
        }
    }

    // Raw-Motor: the gain is unclamped and may be negative or above 1; the bound is a magnitude.
    //
    // Every voice the factory builds has a FINITE gain (it substitutes a non-finite wire
    // value), so a non-finite WIRE value can no longer make this bound unusable. The product
    // can still be non-finite when the voice's own output blew up: an Inf sample sets `peak`
    // to Inf (only NaN loses the compare above), Inf times a zero gain is NaN, and a finite
    // peak times a large gain can overflow. Either way the bound fails `< VOICE_CULL_FLOOR`,
    // so such a voice is never culled. That is the same rule as the NaN one above and not its
    // opposite: culling is an optimisation and it fires on measurable silence, so a block with
    // nothing to measure goes, and a block whose measurement is not small stays.
    return peak * abs(voice.gain)
}
