/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Sidechain ducking/compression processor.
 *
 * Reduces the volume of a signal based on the level of a sidechain (trigger) signal.
 * Commonly used for kick-bass ducking or pumping effects.
 *
 * Uses linked stereo detection: the sidechain level is derived from max(abs(L), abs(R))
 * and a single shared envelope applies the same gain reduction to both channels.
 * This preserves the stereo image during ducking.
 *
 * **Topology** (intentionally NOT shared with [Compressor]): linear-domain peak detector
 * with **instantaneous attack** (gain drops the moment the sidechain spikes) and
 * **exponential release** (one-pole IIR with coefficient `1 - exp(-1/(t·sr))`). The
 * `Compressor` class in this directory uses a dB-domain feedback envelope follower with
 * a soft-knee gain curve — different math, different artefacts. Don't try to share helpers.
 *
 * @param sampleRate Audio sample rate in Hz
 * @param attackSeconds Recovery/release time — how fast the volume returns to normal after
 *   the sidechain trigger stops (in seconds). Note: the duck-down is instantaneous;
 *   this parameter only controls the return smoothing. Named "attack" for strudel compatibility.
 * @param depth Amount of ducking (0.0 = no effect, 1.0 = full silence)
 */
class Ducking(
    private val sampleRate: Int,
    attackSeconds: Double = 0.1,
    depth: Double = 0.5,
) {
    /** Internal release coefficient (named externally as "attack" for Strudel compat). */
    private var releaseCoeff: Double = calculateCoefficient(attackSeconds)

    /** Current gain (1.0 = no duck, 0.0 = full duck). */
    private var currentGain: Double = 1.0

    /** Ducking depth (0.0 = no ducking, 1.0 = full silence). Setter silently ignores non-finite values. */
    var depth: Double = depth.coerceIn(0.0, 1.0)
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceIn(0.0, 1.0)
        }

    /** Attack time in seconds (return to normal). Setter silently ignores non-finite values. */
    var attackSeconds: Double = guardOr(attackSeconds, 0.1)
        set(value) {
            if (!value.isFinite()) return
            field = value
            releaseCoeff = calculateCoefficient(value)
        }

    /**
     * Per-sample envelope step. Computes target gain from the sidechain level and [depthNow]
     * (the property for the settled path, a ramped value while the depth glides), then applies
     * instant-attack / exponential-release smoothing on `currentGain`.
     * Inlined into both `process()` overloads so each loop body stays specialized.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun stepGain(sidechainLevel: Double, depthNow: Double): Double {
        // No silence gate — the formula naturally yields gain=1.0 when sidechainLevel is 0,
        // so the previous `if (level > 0.01)` short-circuit was redundant and introduced a
        // tiny snap discontinuity at the threshold crossing.
        val targetGain = 1.0 - depthNow * min(1.0, sidechainLevel * SIDECHAIN_SENSITIVITY)
        currentGain = if (targetGain < currentGain) {
            targetGain
        } else {
            currentGain + releaseCoeff * (targetGain - currentGain)
        }
        return currentGain
    }

    /**
     * Processes stereo audio with sidechain ducking (linked stereo detection).
     *
     * Detects sidechain level from max(abs(L), abs(R)) and applies the same gain
     * to both channels, preserving the stereo image.
     */
    fun processStereo(
        inputL: AudioBuffer,
        inputR: AudioBuffer,
        sidechainL: AudioBuffer,
        sidechainR: AudioBuffer,
        blockSize: Int,
    ) {
        for (i in 0 until blockSize) {
            val gain = stepGain(max(abs(sidechainL[i]), abs(sidechainR[i])), depth)
            inputL[i] = inputL[i] * gain
            inputR[i] = inputR[i] * gain
        }
    }

    /**
     * The same block, with [depth] and the stage's WEIGHT moving across it (Katalyst 5c-9,
     * `docs/plans/knob-glide.md`). The envelope runs on the whole block either way, so it stays
     * continuous across every edge; what changes is only what the block multiplies by.
     *
     * - **depth** ramps linearly from [depthFrom] to [depthTo]. The target gain is LINEAR in depth
     *   (`1 - depth * x`), so that is the axis the law is linear in, and the detector is
     *   memoryless downwards (the attack is instantaneous), which makes a depth step a level step:
     *   measured -16.8 dB above 8 kHz against the signal at worst on a rise, at the floor on a
     *   fall (the release smooths that direction on its own).
     * - **weight** ramps linearly from [weightFrom] to [weightTo], 1 being the full reduction and
     *   0 exactly 0 dB of it: `gain = 1 + w * (g - 1)`. That is what switching OFF rides down, so
     *   the stage reaches identity BEFORE it lets go of the envelope, and what switching ON rides
     *   up. No dry copy is needed, because ducking is one multiply: blending a scaled mix with the
     *   dry one is the same number as scaling the reduction.
     *
     * Both ramps are written from the block's END, so the last sample carries [depthTo] and
     * [weightTo] bit for bit.
     */
    fun processStereoGliding(
        inputL: AudioBuffer,
        inputR: AudioBuffer,
        sidechainL: AudioBuffer,
        sidechainR: AudioBuffer,
        blockSize: Int,
        depthFrom: Double,
        depthTo: Double,
        weightFrom: Double,
        weightTo: Double,
    ) {
        // A block of zero frames never enters the loop; the reciprocal keeps the division out of
        // the divide-by-zero corner anyway.
        val inv = if (blockSize > 0) 1.0 / blockSize else 0.0
        val depthStep = (depthTo - depthFrom) * inv
        val weightStep = (weightTo - weightFrom) * inv
        val last = blockSize - 1

        for (i in 0 until blockSize) {
            val back = (last - i).toDouble()
            val gain = stepGain(max(abs(sidechainL[i]), abs(sidechainR[i])), depthTo - depthStep * back)
            val weighted = 1.0 + (weightTo - weightStep * back) * (gain - 1.0)

            inputL[i] = inputL[i] * weighted
            inputR[i] = inputR[i] * weighted
        }
    }

    /**
     * Processes mono audio with sidechain ducking.
     *
     * @param input The audio buffer to be ducked
     * @param sidechain The trigger signal (e.g., kick drum on another cylinder)
     * @param blockSize Number of samples to process
     */
    fun process(input: AudioBuffer, sidechain: AudioBuffer, blockSize: Int) {
        for (i in 0 until blockSize) {
            input[i] = input[i] * stepGain(abs(sidechain[i]), depth)
        }
    }

    /**
     * Calculate time constant coefficient for the release envelope. Higher values =
     * faster response. Non-finite or sub-millisecond inputs fall back to a 1ms minimum
     * (matches `Compressor.updateCoefficients`).
     */
    private fun calculateCoefficient(timeSeconds: Double): Double {
        val safeTime = if (timeSeconds.isFinite()) timeSeconds else 0.001
        val clampedTime = max(0.001, safeTime)
        return 1.0 - exp(-1.0 / (clampedTime * sampleRate))
    }

    /** Reset internal state */
    fun reset() {
        currentGain = 1.0
    }

    companion object {
        /**
         * Sidechain sensitivity / saturation knee. With `* 2.0`, the ducker reaches full
         * saturation when the sidechain peak is 0.5 — half the dynamic range, which is
         * musical for percussion sidechains (kick drums peak around -3 to -6 dBFS).
         * Lower the constant for a less aggressive duck; raise it for a harsher pump.
         */
        private const val SIDECHAIN_SENSITIVITY: Double = 2.0

        /** Returns [value] when finite; otherwise [fallback]. Mirrors `Compressor.guardOr`. */
        private fun guardOr(value: Double, fallback: Double): Double =
            if (value.isFinite()) value else fallback
    }
}
